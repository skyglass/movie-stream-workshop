import { CommonModule } from '@angular/common';
import { Component, EventEmitter, Input, Output } from '@angular/core';
import { MovieCategory } from '../../services/movies-api';
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
        @if (!category.leaf) {
          <button type="button" class="expand" (click)="expand.emit(category)" [attr.aria-label]="expanded.has(category.id) ? 'Collapse category' : 'Expand category'">
            {{ expanded.has(category.id) ? '−' : '+' }}
          </button>
        } @else { <span class="expand-spacer"></span> }
        <label class="category-choice" [class.locked]="locked">
          <input type="checkbox" [checked]="checked" [disabled]="locked" (change)="toggle.emit({category, checked: $any($event.target).checked})" />
          <span class="category-label" [title]="category.description || category.name">
            <span class="emoji">{{ subscribed ? '🔔' : (category.icon || '📁') }}</span>
            <span class="category-name">{{ category.name }}</span>
            @if (category.referencedCategoryId && mode !== 'guide') {
              <span class="material-icons reference-badge" title="Referenced from another guide/category">link</span>
            }
            @if (subscribed) {
              <span class="subscribed-label" title="Subscribed category">Subscribed</span>
            }
          </span>
        </label>
        @if (management && !(mode === 'guide' && subscribed)) {
          <div class="node-actions">
            @if (!category.referencedCategoryId) {
              <button type="button" class="node-action" title="Create sub-category" (click)="create.emit(category.id)"><span class="material-icons">create_new_folder</span></button>
              <button type="button" class="node-action" title="Edit category" (click)="edit.emit(category)"><span class="material-icons">edit</span></button>
              <button type="button" class="node-action" title="Move category" (click)="move.emit({category, parentId: parentIdForActions})"><span class="material-icons">drive_file_move</span></button>
            }
            <button type="button" class="node-action danger" [title]="category.referencedCategoryId ? 'Unlink from this guide' : 'Delete category'" (click)="remove.emit({category, parentId: parentIdForActions})"><span class="material-icons">{{ category.referencedCategoryId ? 'link_off' : 'delete' }}</span></button>
          </div>
        }
      </div>
      @if (!category.leaf && expanded.has(category.id)) {
        <ul>
          @for (child of category.children; track child.id) {
            <app-category-tree-node [category]="child" [mode]="mode" [ancestors]="childAncestors" [rootCategoryId]="rootCategoryId" [expanded]="expanded" [explicitSelected]="explicitSelected" [assignmentSelected]="assignmentSelected" [management]="management"
              (expand)="expand.emit($event)" (toggle)="toggle.emit($event)" (create)="create.emit($event)" (edit)="edit.emit($event)" (move)="move.emit($event)" (remove)="remove.emit($event)" />
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
  @Input() management = false;
  @Output() expand = new EventEmitter<MovieCategory>();
  @Output() toggle = new EventEmitter<{ category: MovieCategory; checked: boolean }>();
  @Output() create = new EventEmitter<number>();
  @Output() edit = new EventEmitter<MovieCategory>();
  @Output() move = new EventEmitter<CategoryNodeAction>();
  @Output() remove = new EventEmitter<CategoryNodeAction>();

  // A node is "subscribed" if it's a reference itself, or lives under one -- e.g. "Genres > Crime" is just as
  // read-only as "Genres" once "Genres" is a subscribed reference, since both belong to a tree managed elsewhere.
  get subscribed(): boolean {
    return !!this.category.referencedCategoryId || this.ancestors.some(ancestor => !!ancestor.referencedCategoryId);
  }

  get childAncestors(): MovieCategory[] { return [...this.ancestors, this.category]; }
  get parentIdForActions(): number {
    if (this.ancestors.length) return this.ancestors[this.ancestors.length - 1].id;
    return this.rootCategoryId ?? this.category.id;
  }
  get locked(): boolean { return this.mode === 'filter' && this.ancestors.some(parent => this.explicitSelected.has(parent.id)); }
  get checked(): boolean {
    if (this.mode === 'assign') return this.assignmentSelected.has(this.category.id);
    if (this.mode === 'move' || this.mode === 'guide') return this.explicitSelected.has(this.category.id);
    return this.explicitSelected.has(this.category.id) || this.ancestors.some(parent => this.explicitSelected.has(parent.id));
  }
}
