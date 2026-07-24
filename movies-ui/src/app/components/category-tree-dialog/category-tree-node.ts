import { CommonModule } from '@angular/common';
import { Component, EventEmitter, Input, Output } from '@angular/core';
import { MovieCategory, Operator } from '../../services/movies-api';
import type { CategoryTreeMode } from './category-tree-dialog';

export interface CategoryNodeAction {
  category: MovieCategory;
  parentId: number;
}

@Component({
  standalone: true,
  selector: 'app-category-tree-node',
  imports: [CommonModule],
  styleUrl: './category-tree-node.css',
  template: `
    <li class="tree-node">
      <div class="node-row">
        @if (!category.leaf || category.operator) {
          <button type="button" class="expand" (click)="expand.emit(category)" [attr.aria-label]="expanded.has(category.id) ? 'Collapse category' : 'Expand category'">
            @if (category.operator) { <span class="material-icons composition-expand">account_tree</span> } @else { {{ expanded.has(category.id) ? '−' : '+' }} }
          </button>
        } @else { <span class="expand-spacer"></span> }
        <label class="category-choice" [class.locked]="locked" [class.composition-choice]="category.operator" [class.component-match]="isCompositionComponent">
          <input type="checkbox" [checked]="checked" [disabled]="locked" (change)="toggle.emit({category, checked: $any($event.target).checked})" />
          <span class="category-label" [title]="cycleDisabled ? 'Selecting this would create a circular dependency' : (category.description || category.name)">
            <span class="emoji">{{ category.icon || (category.operator === 'OR' ? '🔔' : category.operator ? '🧩' : '📁') }}</span>
            <span class="category-name">{{ category.name }}</span>
            @if (category.operator; as operator) {
              <span class="and-badge" [class.or-badge]="operator === 'OR'" [title]="operator === 'AND' ? 'Match All Categories (AND): all components are required' : 'Match Any Category (OR): any component is enough'">{{ operator }}</span>
            }
            @if (isCompositionComponent; as componentOperator) {
              <span class="and-badge" [class.or-badge]="componentOperator === 'OR'" title="Component of the selected composition/subscription category">{{ componentOperator }}</span>
            }
          </span>
        </label>
        @if (management) {
          <div class="node-actions">
            @if (!category.operator) {
            <button type="button" class="node-action" title="Create sub-category" (click)="create.emit(category.id)"><span class="material-icons">create_new_folder</span></button>
            }
            <button type="button" class="node-action" title="Edit category" (click)="edit.emit(category)"><span class="material-icons">edit</span></button>
            <button type="button" class="node-action" title="Move category" (click)="move.emit({category, parentId: parentIdForActions})"><span class="material-icons">drive_file_move</span></button>
            <button type="button" class="node-action danger" title="Delete category" (click)="remove.emit({category, parentId: parentIdForActions})"><span class="material-icons">delete</span></button>
          </div>
        }
      </div>
      @if (category.operator && expanded.has(category.id)) {
        <ul class="composition-components" aria-label="Composition components">
          @for (component of category.components; track component.id; let first = $first) {
            <li><span class="and-badge" [class.or-badge]="category.operator === 'OR'">{{ first ? 'IS' : category.operator }}</span>
              @if (mode === 'assign') {
                <input type="checkbox" [checked]="assignmentSelected.has(component.id)" (change)="compositionComponentToggle.emit({componentId: component.id, checked: $any($event.target).checked})" [attr.aria-label]="'Assign ' + component.name" />
              }
              <span class="emoji">{{ component.icon || '📁' }}</span>{{ component.name }}</li>
          }
        </ul>
      }
      @if (!category.leaf && expanded.has(category.id)) {
        <ul>
          @for (child of category.children; track child.id) {
            <app-category-tree-node [category]="child" [mode]="mode" [ancestors]="childAncestors" [rootCategoryId]="rootCategoryId" [expanded]="expanded" [explicitSelected]="explicitSelected" [assignmentSelected]="assignmentSelected" [compositionComponentIds]="compositionComponentIds" [componentPicker]="componentPicker" [management]="management" [cycleDisabledIds]="cycleDisabledIds"
              (expand)="expand.emit($event)" (toggle)="toggle.emit($event)" (compositionComponentToggle)="compositionComponentToggle.emit($event)" (create)="create.emit($event)" (edit)="edit.emit($event)" (move)="move.emit($event)" (remove)="remove.emit($event)" />
          }
        </ul>
      }
    </li>
  `
})
export class CategoryTreeNodeComponent {
  @Input({ required: true }) category!: MovieCategory;
  @Input() mode: CategoryTreeMode = 'assign';
  @Input() ancestors: MovieCategory[] = [];
  // The real parent of a top-level (ancestor-less) node when this tree is a subtree rooted somewhere other than
  // the global category forest (e.g. 'guide' mode's getCategorySubtree) -- those top-level nodes are the root
  // category's direct children, not genuine self-parented roots, so parentIdForActions must fall back to this
  // instead of the node's own id.
  @Input() rootCategoryId?: number;
  @Input() expanded = new Set<number>();
  @Input() explicitSelected = new Set<number>();
  @Input() assignmentSelected = new Set<number>();
  // id -> operator of the composition/subscription category that requires/includes it, for every component of a
  // currently-selected composable category (see CategoryTreeDialogComponent.selectedCompositionComponentIds).
  @Input() compositionComponentIds = new Map<number, Operator>();
  @Input() componentPicker = false;
  @Input() management = false;
  // Component-picker only: ids that would create a circular dependency if picked as a component of the
  // composition/subscription category currently being created/edited -- computed client-side in memory by
  // CategoryTreeDialogComponent.computeCycleDisabledIds, mirroring the server-side cycle check.
  @Input() cycleDisabledIds = new Set<number>();
  @Output() expand = new EventEmitter<MovieCategory>();
  @Output() toggle = new EventEmitter<{ category: MovieCategory; checked: boolean }>();
  @Output() compositionComponentToggle = new EventEmitter<{ componentId: number; checked: boolean }>();
  @Output() create = new EventEmitter<number>();
  @Output() edit = new EventEmitter<MovieCategory>();
  @Output() move = new EventEmitter<CategoryNodeAction>();
  @Output() remove = new EventEmitter<CategoryNodeAction>();

  get childAncestors(): MovieCategory[] { return [...this.ancestors, this.category]; }
  get parentIdForActions(): number {
    if (this.ancestors.length) return this.ancestors[this.ancestors.length - 1].id;
    return this.rootCategoryId ?? this.category.id;
  }
  get locked(): boolean {
    return (this.mode === 'filter' && this.ancestors.some(parent => this.explicitSelected.has(parent.id)))
      || this.isCompositionComponent != null
      || this.cycleDisabled;
  }
  get cycleDisabled(): boolean {
    return this.componentPicker && this.cycleDisabledIds.has(this.category.id);
  }
  get isCompositionComponent(): Operator | undefined {
    return this.componentPicker ? undefined : this.compositionComponentIds.get(this.category.id);
  }
  get checked(): boolean {
    if (this.mode === 'assign') return this.assignmentSelected.has(this.category.id);
    if (this.mode === 'move' || this.mode === 'guide') return this.explicitSelected.has(this.category.id);
    return this.isCompositionComponent != null || this.explicitSelected.has(this.category.id)
      || this.ancestors.some(parent => this.explicitSelected.has(parent.id));
  }
}
