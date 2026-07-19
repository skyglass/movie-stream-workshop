import { CommonModule } from '@angular/common';
import { Component, EventEmitter, Input, OnInit, Output, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MovieCategory, MoviesApiService, SaveMovieCategory } from '../../services/movies-api';
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
  // category picker excludes a guide's subscribed/default categories, since movies can't be removed from those.
  @Input() excludedCategoryIds: number[] = [];
  // Threaded down to the Move dialog: false suppresses the "Copy instead of move" checkbox (e.g. inside a Movie
  // Guide's own sandbox, where subscribing to one of its own categories would be nonsensical).
  @Input() allowCopy = true;
  // 'filter' mode only: hides these top-level roots (and everything under them) from the tree -- e.g. the
  // "Subscribe to Categories" dialog excludes "Guides"/"Personalities" so a guide can't subscribe to itself,
  // another guide, or a personality, which is unsupported and untested.
  @Input() excludeRootNames: string[] = [];
  // 'guide' mode only: whether the current viewer owns this guide -- 'guide' mode is now shown to anonymous and
  // non-owner viewers too (as a read-only "Select Category" browser), so management can no longer be assumed
  // just from the mode; it's only actually enabled for the true owner (or MOVIES_GUIDE/MOVIES_ADMIN).
  @Input() isOwner = false;
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
  loading = true;
  saving = false;
  errorMessage = '';

  ngOnInit(): void {
    this.explicitSelected = new Set(this.selectedCategoryIds);
    this.originalExplicitSelected = new Set(this.selectedCategoryIds);
    this.load();
  }

  load(): void {
    this.loading = true;
    this.errorMessage = '';
    const request = (this.mode === 'guide' || this.mode === 'filter') && this.rootCategoryId != null
      ? this.api.getCategorySubtree(this.rootCategoryId, this.excludedCategoryIds)
      : this.api.getCategoryTree(this.mode === 'assign' ? this.movieId : undefined);
    request.subscribe({
      next: categories => {
        this.categories = this.excludeRootNames.length
          ? categories.filter(category => !this.excludeRootNames.includes(category.name))
          : categories;
        this.originalChecked = new Set(this.flatten(categories).filter(category => category.checked).map(category => category.id));
        this.expandAncestorsOf(this.selectedCategoryIds, categories);
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
    return ancestors.some(parent => this.explicitSelected.has(parent.id)) || this.explicitSelected.has(category.id);
  }

  isLocked(ancestors: MovieCategory[]): boolean {
    return this.mode === 'filter' && ancestors.some(parent => this.explicitSelected.has(parent.id));
  }

  toggleCategory(category: MovieCategory, checked: boolean): void {
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
  }

  beginEdit(category: MovieCategory): void {
    this.creatingParentId = undefined;
    this.editingId = category.id;
    this.name = category.name; this.icon = category.icon || ''; this.description = category.description || '';
  }

  cancelEditor(): void { this.editingId = null; this.creatingParentId = undefined; }

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
    const request: SaveMovieCategory = {
      name: this.name.trim(), icon: this.icon.trim(), description: this.description.trim(),
      parentId: existing?.parentId ?? this.creatingParentId ?? null
    };
    this.saving = true;
    const operation = this.editingId == null
      ? this.api.createCategory(request)
      : this.api.updateCategory(this.editingId, request);
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
    const verb = action.category.referencedCategoryId ? 'Unlink' : 'Delete';
    if (!confirm(`${verb} category “${action.category.name}”?`)) return;
    this.saving = true;
    this.api.deleteCategory(action.category.id, action.parentId).subscribe({
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
    // MOVIES_GUIDE/MOVIES_ADMIN account, same as everywhere else.
    if (this.mode === 'guide') return this.isOwner || this.auth.canEditMovies;
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
  private fail(error: any): void {
    this.errorMessage = error?.error?.detail ?? error?.error?.message ?? error?.message ?? 'Category request failed';
    this.loading = false; this.saving = false;
  }
}
