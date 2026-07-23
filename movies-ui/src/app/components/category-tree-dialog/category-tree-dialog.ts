import { CommonModule } from '@angular/common';
import { Component, EventEmitter, Input, OnInit, Output, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MovieCategory, MoviesApiService, Operator, SaveMovieCategory } from '../../services/movies-api';
import { AuthService } from '../../services/auth';
import { CategoryNodeAction, CategoryTreeNodeComponent } from './category-tree-node';
import { MoveCategoryDialogComponent } from '../move-category-dialog/move-category-dialog';

export type CategoryTreeMode = 'assign' | 'filter' | 'move' | 'guide';

@Component({
  standalone: true,
  selector: 'app-category-tree-dialog',
  imports: [CommonModule, FormsModule, CategoryTreeNodeComponent, MoveCategoryDialogComponent],
  templateUrl: './category-tree-dialog.html',
  styleUrl: './category-tree-dialog.css'
})
export class CategoryTreeDialogComponent implements OnInit {
  private readonly api = inject(MoviesApiService);
  readonly auth = inject(AuthService);
  @Input() mode: CategoryTreeMode = 'assign';
  @Input() movieId = '';
  @Input() selectedCategoryIds: number[] = [];
  @Input() inline = false;
  // 'guide' mode, or 'filter' mode with rootCategoryId set: scopes the tree to one category's direct children
  // (e.g. a Movie Guide's own anchor category) instead of the whole global tree.
  @Input() rootCategoryId?: number;
  // Used together with rootCategoryId: excludes these category ids from the subtree -- e.g. the "Delete Movies"
  // category picker excludes a guide's own composition/subscription categories, since movies can't be removed
  // from those directly.
  @Input() excludedCategoryIds: number[] = [];
  // 'guide' mode only: whether the current viewer owns this guide -- 'guide' mode is now shown to anonymous and
  // non-owner viewers too (as a read-only "Select Category" browser), so management can no longer be assumed
  // just from the mode; it's only actually enabled for the true owner (or MOVIES_GUIDE/MOVIES_ADMIN).
  @Input() isOwner = false;
  // Set for a private watchlist's own category picker: swaps the data source to the merged private-subtree +
  // subscribed-public-categories endpoint (WatchlistService.categoryPicker), and swaps every management call
  // (create/edit/move/delete) to the private-category endpoints. Orthogonal to `mode` -- 'guide' mode with
  // watchlistId set is a single-select "Select Category" picker over a watchlist's own sandbox; 'filter' mode
  // with watchlistId set is the multi-select picker the "Delete Movies" dialog uses. rootCategoryId should still
  // be set alongside this to the watchlist's own anchor category id, purely so top-level node actions resolve
  // their real parent id (see CategoryTreeNodeComponent.parentIdForActions) -- it's not used to pick the load
  // endpoint once watchlistId is set.
  @Input() watchlistId?: number;
  // Reuses the normal selector for the composition/subscription editor, but a composable category can only be
  // picked as a component if nesting it wouldn't create a circular dependency -- see cycleGuardId.
  @Input() componentPicker = false;
  // Component-picker instances only: the id of the composition/subscription category currently being edited (undef
  // on create, since a brand-new category can never be transitively reachable from anything yet). Used to compute
  // cycleDisabledIds client-side from whatever tree is already loaded in memory, mirroring the server-side cycle
  // check (CategoryService.wouldCreateCycle) without needing a recursive query.
  @Input() cycleGuardId?: number;
  // Skips straight to the "Create Category" editor with Combine Categories already on, targeting composeParentId
  // (falling back to rootCategoryId) and defaulting to the given operator -- backs the standalone "Compose
  // Categories" (AND) and "Subscribe to Categories" (OR) shortcut buttons on the Guide/Personality/Watchlist
  // pages, both of which just open the same editor pre-set to a different operator.
  @Input() startInComposeMode = false;
  @Input() startInOperator: Operator = 'AND';
  @Input() composeParentId?: number;
  @Output() closed = new EventEmitter<void>();
  @Output() categoriesSelected = new EventEmitter<number[]>();
  @Output() selectionChanged = new EventEmitter<number[]>();
  @Output() assignmentsSaved = new EventEmitter<void>();

  categories: MovieCategory[] = [];
  expanded = new Set<number>();
  explicitSelected = new Set<number>();
  originalExplicitSelected = new Set<number>();
  originalChecked = new Set<number>();
  addedCategories = new Set<number>();
  removedCategories = new Set<number>();
  editingId: number | null = null;
  creatingParentId: number | null | undefined = undefined;
  movingAction: CategoryNodeAction | null = null;
  name = '';
  icon = '';
  description = '';
  // "Combine Categories": the one AND/OR composition mechanism, shared by what used to be two separate features
  // ("Composition Category" and "Subscription Category") -- AND requires every component, OR requires just one.
  combineEnabled = false;
  combineOperator: Operator = 'AND';
  combineComponentIds: number[] = [];
  // Watchlist scope only: existing PUBLIC categories to include as components alongside combineComponentIds
  // (private-category ids there) -- e.g. a watchlist composition that includes "New 2026" from the public catalog.
  // Always empty for the public category API.
  combinePublicComponentIds: number[] = [];
  combineValidationNotice = false;
  combinePickerVisible = false;
  combinePublicPickerVisible = false;
  combineUncheckNotice: MovieCategory | null = null;
  private combinePublicComponentNames = new Map<number, string>();
  cycleDisabledIds = new Set<number>();
  loading = true;
  saving = false;
  errorMessage = '';

  ngOnInit(): void {
    this.explicitSelected = new Set(this.selectedCategoryIds);
    this.originalExplicitSelected = new Set(this.selectedCategoryIds);
    this.load();
    if (this.startInComposeMode) {
      this.beginCreate(this.composeParentId ?? this.rootCategoryId ?? null);
      this.setCombineEnabled(true);
      this.combineOperator = this.startInOperator;
    }
  }

  load(): void {
    this.loading = true;
    this.errorMessage = '';
    const request = this.watchlistId != null
      ? (this.componentPicker && this.rootCategoryId != null
          // A private composition's local components must come from this same watchlist's own private subtree,
          // not the merged private-subtree + subscribed-public-categories picker -- mirrors move-category-dialog's
          // own public/private distinction for the identical "pure subtree, no merge" need.
          ? this.api.getPrivateCategorySubtree(this.rootCategoryId)
          : this.api.getWatchlistCategoryPicker(this.watchlistId, this.excludedCategoryIds))
      : (this.mode === 'guide' || this.mode === 'filter') && this.rootCategoryId != null
        ? this.api.getCategorySubtree(this.rootCategoryId, this.excludedCategoryIds)
        : this.api.getCategoryTree(this.mode === 'assign' ? this.movieId : undefined);
    request.subscribe({
      next: categories => {
        this.categories = categories;
        this.originalChecked = new Set(this.flatten(categories).filter(category => category.checked).map(category => category.id));
        this.expandAncestorsOf(this.selectedCategoryIds, categories);
        this.computeCycleDisabledIds();
        this.loading = false;
      },
      error: error => this.fail(error)
    });
  }

  toggleExpanded(category: MovieCategory): void {
    this.expanded.has(category.id) ? this.expanded.delete(category.id) : this.expanded.add(category.id);
  }

  isChecked(category: MovieCategory, ancestors: MovieCategory[] = []): boolean {
    if (this.mode === 'assign') return this.currentAssignment(category.id);
    if (!this.componentPicker && this.selectedCompositionComponentIds().has(category.id)) return true;
    return ancestors.some(parent => this.explicitSelected.has(parent.id)) || this.explicitSelected.has(category.id);
  }

  isLocked(ancestors: MovieCategory[]): boolean {
    return this.mode === 'filter' && ancestors.some(parent => this.explicitSelected.has(parent.id));
  }

  toggleCategory(category: MovieCategory, checked: boolean): void {
    if (this.mode === 'assign' && category.operator && !checked) {
      // Composition/subscription membership is calculated from its components. Do not add a "remove" write: the
      // user must instead change whichever underlying category is no longer true for this movie.
      this.combineUncheckNotice = category;
      return;
    }
    if (this.mode === 'guide') {
      // Single-select, non-recursive: checking one category never implies its sub-categories, and only one
      // category may be targeted for a movie assignment at a time.
      this.explicitSelected.clear();
      if (checked) this.explicitSelected.add(category.id);
      this.selectionChanged.emit([...this.explicitSelected]);
      return;
    }
    if (this.mode === 'filter') {
      if (checked) {
        this.removeExplicitDescendants(category);
        this.explicitSelected.add(category.id);
      } else {
        this.explicitSelected.delete(category.id);
      }
      this.selectionChanged.emit([...this.explicitSelected]);
      return;
    }
    if (checked) {
      this.removedCategories.delete(category.id);
      if (!this.originalChecked.has(category.id)) this.addedCategories.add(category.id);
    } else {
      this.addedCategories.delete(category.id);
      if (this.originalChecked.has(category.id)) this.removedCategories.add(category.id);
    }
  }

  submit(): void {
    if (this.mode === 'filter' || this.mode === 'guide') {
      this.categoriesSelected.emit([...this.explicitSelected]);
      return;
    }
    if (this.mode !== 'assign' || !this.movieId) return;
    this.saving = true;
    this.api.saveMovieCategories(this.movieId, [...this.addedCategories], [...this.removedCategories]).subscribe({
      next: () => { this.saving = false; this.assignmentsSaved.emit(); },
      error: error => this.fail(error)
    });
  }

  beginCreate(parentId: number | null): void {
    this.editingId = null;
    this.creatingParentId = parentId;
    this.name = ''; this.icon = ''; this.description = '';
    this.combineEnabled = false; this.combineOperator = 'AND';
    this.combineComponentIds = []; this.combinePublicComponentIds = [];
    this.combineValidationNotice = false;
    this.computeCycleDisabledIds();
  }

  beginEdit(category: MovieCategory): void {
    this.creatingParentId = undefined;
    this.editingId = category.id;
    this.name = category.name; this.icon = category.icon || ''; this.description = category.description || '';
    this.combineEnabled = category.operator != null;
    this.combineOperator = category.operator ?? 'AND';
    if (this.watchlistId != null) {
      this.combineComponentIds = category.operator ? category.components.filter(component => !component.isPublic).map(component => component.id) : [];
      this.combinePublicComponentIds = category.operator ? category.components.filter(component => component.isPublic).map(component => component.id) : [];
    } else {
      this.combineComponentIds = category.operator ? category.components.map(component => component.id) : [];
      this.combinePublicComponentIds = [];
    }
    category.components.filter(component => component.isPublic).forEach(component => this.combinePublicComponentNames.set(component.id, component.name));
    this.combineValidationNotice = false;
    this.computeCycleDisabledIds();
  }

  cancelEditor(): void {
    this.editingId = null; this.creatingParentId = undefined;
    this.combinePickerVisible = false; this.combinePublicPickerVisible = false;
  }

  setCombineEnabled(enabled: boolean): void {
    this.combineEnabled = enabled;
    if (!enabled) { this.combineComponentIds = []; this.combinePublicComponentIds = []; }
  }
  setCombineOperator(operator: Operator): void { this.combineOperator = operator; }
  openCombinePicker(): void { this.combineEnabled = true; this.combinePickerVisible = true; }
  onCombineComponentsSelected(ids: number[]): void {
    this.combineComponentIds = ids;
    this.combinePickerVisible = false;
  }
  openCombinePublicPicker(): void { this.combineEnabled = true; this.combinePublicPickerVisible = true; }
  onCombinePublicComponentsSelected(ids: number[]): void {
    this.combinePublicComponentIds = ids;
    this.combinePublicPickerVisible = false;
    this.api.getCategoryTree().subscribe(categories => {
      this.flatten(categories).forEach(category => this.combinePublicComponentNames.set(category.id, category.name));
    });
  }
  acknowledgeCombineUncheck(): void {
    if (this.combineUncheckNotice) this.expanded.add(this.combineUncheckNotice.id);
    this.combineUncheckNotice = null;
  }
  toggleCompositionComponent(componentId: number, checked: boolean): void {
    const component = this.find(componentId);
    if (component) this.toggleCategory(component, checked);
  }
  combineComponentPaths(): string[] {
    return this.combineComponentIds.map(id => this.pathFor(id)).filter((path): path is string => !!path);
  }
  combinePublicComponentNamesList(): string[] {
    return this.combinePublicComponentIds.map(id => this.combinePublicComponentNames.get(id)).filter((name): name is string => !!name);
  }
  combineComponentDisplayItems(): { label: string; isPublic: boolean }[] {
    const items = [
      ...this.combineComponentPaths().map(label => ({ label, isPublic: false })),
      ...this.combinePublicComponentNamesList().map(label => ({ label, isPublic: true }))
    ];
    return items;
  }
  editingCombination(): boolean { return this.editingId != null && !!this.find(this.editingId)?.operator; }
  acknowledgeCombineValidation(): void { this.combineValidationNotice = false; }

  editorTitle(): string {
    if (this.editingId != null) return 'Edit Category';
    return this.creatingParentId == null ? 'Create Root Category' : 'Create Category';
  }

  editorParentCategory(): MovieCategory | undefined {
    const parentId = this.editingId != null ? (this.find(this.editingId)?.parentId ?? null) : (this.creatingParentId ?? null);
    return parentId == null ? undefined : this.find(parentId);
  }

  saveEditor(): void {
    if (!this.name.trim()) return;
    const existing = this.editingId == null ? null : this.find(this.editingId);
    const isCombinable = this.editingId == null ? this.combineEnabled : existing?.operator != null;
    if (isCombinable && this.combineComponentIds.length === 0 && this.combinePublicComponentIds.length === 0) {
      this.combineValidationNotice = true;
      return;
    }
    const request: SaveMovieCategory = {
      name: this.name.trim(), icon: this.icon.trim(), description: this.description.trim(),
      parentId: existing?.parentId ?? this.creatingParentId ?? null,
      componentCategoryIds: isCombinable ? this.combineComponentIds : undefined,
      publicComponentCategoryIds: isCombinable && this.watchlistId != null ? this.combinePublicComponentIds : undefined,
      operator: isCombinable ? this.combineOperator : undefined
    };
    this.saving = true;
    const operation = this.watchlistId != null
      ? (this.editingId == null ? this.api.createPrivateCategory(request) : this.api.updatePrivateCategory(this.editingId, request))
      : (this.editingId == null ? this.api.createCategory(request) : this.api.updateCategory(this.editingId, request));
    operation.subscribe({
      next: category => {
        if (this.editingId == null) {
          this.expanded.add(request.parentId ?? category.id);
        }
        this.cancelEditor(); this.saving = false; this.load();
      },
      error: error => this.fail(error)
    });
  }

  deleteCategory(action: CategoryNodeAction): void {
    if (!confirm(`Delete category "${action.category.name}"?`)) return;
    this.saving = true;
    const operation = this.watchlistId != null
      ? this.api.deletePrivateCategory(action.category.id, action.parentId)
      : this.api.deleteCategory(action.category.id, action.parentId);
    operation.subscribe({
      next: () => { this.explicitSelected.delete(action.category.id); this.saving = false; this.load(); },
      error: error => this.fail(error)
    });
  }

  beginMove(action: CategoryNodeAction): void { this.movingAction = action; }

  cancelMove(): void { this.movingAction = null; }

  onMoved(): void { this.movingAction = null; this.load(); }

  canSubmit(): boolean {
    return this.mode === 'filter' ? !this.setsEqual(this.explicitSelected, this.originalExplicitSelected)
      : this.addedCategories.size > 0 || this.removedCategories.size > 0;
  }

  managementEnabled(): boolean {
    // 'guide' mode is shown to every viewer (owner, non-owner, anonymous) as a read-only "Select Category"
    // browser -- management (create/edit/move/delete) is only actually enabled for the true owner or a
    // MOVIES_GUIDE/MOVIES_ADMIN account, same as everywhere else. A watchlist's private categories are never
    // manageable by MOVIES_GUIDE (that role only curates public Guides/Personalities) -- only the owner or
    // MOVIES_ADMIN.
    if (this.mode === 'guide') return this.isOwner || (this.watchlistId != null ? this.auth.isAdmin : this.auth.canEditMovies);
    return this.mode !== 'filter' && this.auth.canEditMovies;
  }

  assignmentSelectedIds(): Set<number> {
    const selected = new Set(this.originalChecked);
    this.removedCategories.forEach(id => selected.delete(id));
    this.addedCategories.forEach(id => selected.add(id));
    return selected;
  }

  private currentAssignment(id: number): boolean {
    if (this.addedCategories.has(id)) return true;
    if (this.removedCategories.has(id)) return false;
    return this.originalChecked.has(id);
  }

  private removeExplicitDescendants(category: MovieCategory): void {
    this.flatten(category.children).forEach(child => this.explicitSelected.delete(child.id));
  }

  private expandAncestorsOf(ids: number[], categories: MovieCategory[], ancestors: number[] = []): void {
    if (ids.length === 0) return;
    for (const category of categories) {
      if (ids.includes(category.id)) ancestors.forEach(id => this.expanded.add(id));
      this.expandAncestorsOf(ids, category.children, [...ancestors, category.id]);
    }
  }

  private setsEqual(a: Set<number>, b: Set<number>): boolean {
    if (a.size !== b.size) return false;
    for (const id of a) if (!b.has(id)) return false;
    return true;
  }

  private find(id: number): MovieCategory | undefined { return this.flatten(this.categories).find(category => category.id === id); }
  private flatten(categories: MovieCategory[]): MovieCategory[] { return categories.flatMap(category => [category, ...this.flatten(category.children)]); }
  selectedCompositionComponentIds(): Map<number, Operator> {
    const result = new Map<number, Operator>();
    this.flatten(this.categories)
      .filter(category => this.explicitSelected.has(category.id) && category.operator)
      .forEach(category => category.components.forEach(component => result.set(component.id, category.operator!)));
    return result;
  }
  private pathFor(id: number): string | undefined {
    const walk = (categories: MovieCategory[], ancestors: MovieCategory[]): string | undefined => {
      for (const category of categories) {
        const path = [...ancestors, category];
        if (category.id === id) return path.map(item => item.name).join(' › ');
        const found = walk(category.children, path);
        if (found) return found;
      }
      return undefined;
    };
    return walk(this.categories, []);
  }
  // Component-picker only: which loaded categories would create a circular dependency if picked as a component of
  // cycleGuardId. Walks each candidate's own (non-public) components transitively, purely in memory against
  // whatever tree is already loaded -- an iterative stack walk, not a recursive query, mirroring
  // CategoryService.wouldCreateCycle. Public components are never walked into: a public category can never depend
  // on a private one, so they're always safe terminal nodes for this check.
  private computeCycleDisabledIds(): void {
    if (this.cycleGuardId == null) { this.cycleDisabledIds = new Set(); return; }
    const guardId = this.cycleGuardId;
    const byId = new Map<number, MovieCategory>();
    this.flatten(this.categories).forEach(category => byId.set(category.id, category));
    const wouldCycle = (candidateId: number): boolean => {
      const visited = new Set<number>();
      const pending = [candidateId];
      while (pending.length) {
        const current = pending.pop()!;
        if (current === guardId) return true;
        if (visited.has(current)) continue;
        visited.add(current);
        const node = byId.get(current);
        (node?.components ?? []).filter(component => !component.isPublic).forEach(component => pending.push(component.id));
      }
      return false;
    };
    const disabled = new Set<number>();
    byId.forEach((_, id) => { if (wouldCycle(id)) disabled.add(id); });
    this.cycleDisabledIds = disabled;
  }
  private fail(error: any): void {
    this.errorMessage = error?.error?.detail ?? error?.error?.message ?? error?.message ?? 'Category request failed';
    this.loading = false; this.saving = false;
  }
}
