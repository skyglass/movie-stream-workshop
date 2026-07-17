import { CommonModule } from '@angular/common';
import { Component, EventEmitter, Input, Output } from '@angular/core';
import { MovieCategory } from '../../services/movies-api';
import type { CategoryTreeMode } from './category-tree-dialog';

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
            <span class="emoji">{{ category.icon || '📁' }}</span>
            <span class="category-name">{{ category.name }}</span>
          </span>
        </label>
        @if (management) {
          <div class="node-actions">
            <button type="button" class="node-action" title="Create sub-category" (click)="create.emit(category.id)"><span class="material-icons">create_new_folder</span></button>
            <button type="button" class="node-action" title="Edit category" (click)="edit.emit(category)"><span class="material-icons">edit</span></button>
            <button type="button" class="node-action danger" title="Delete empty category" [disabled]="!category.empty" (click)="remove.emit(category)"><span class="material-icons">delete</span></button>
          </div>
        }
      </div>
      @if (!category.leaf && expanded.has(category.id)) {
        <ul>
          @for (child of category.children; track child.id) {
            <app-category-tree-node [category]="child" [mode]="mode" [ancestors]="childAncestors" [expanded]="expanded" [explicitSelected]="explicitSelected" [assignmentSelected]="assignmentSelected" [management]="management"
              (expand)="expand.emit($event)" (toggle)="toggle.emit($event)" (create)="create.emit($event)" (edit)="edit.emit($event)" (remove)="remove.emit($event)" />
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
  @Input() expanded = new Set<number>();
  @Input() explicitSelected = new Set<number>();
  @Input() assignmentSelected = new Set<number>();
  @Input() management = false;
  @Output() expand = new EventEmitter<MovieCategory>();
  @Output() toggle = new EventEmitter<{ category: MovieCategory; checked: boolean }>();
  @Output() create = new EventEmitter<number>();
  @Output() edit = new EventEmitter<MovieCategory>();
  @Output() remove = new EventEmitter<MovieCategory>();

  get childAncestors(): MovieCategory[] { return [...this.ancestors, this.category]; }
  get locked(): boolean { return this.mode === 'filter' && this.ancestors.some(parent => this.explicitSelected.has(parent.id)); }
  get checked(): boolean {
    if (this.mode === 'assign') return this.assignmentSelected.has(this.category.id);
    if (this.mode === 'journey') return this.explicitSelected.has(this.category.id);
    return this.explicitSelected.has(this.category.id) || this.ancestors.some(parent => this.explicitSelected.has(parent.id));
  }
}
