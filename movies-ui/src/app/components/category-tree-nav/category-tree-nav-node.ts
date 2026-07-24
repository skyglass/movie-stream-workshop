import { CommonModule } from '@angular/common';
import { Component, Input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { MovieCategory } from '../../services/movies-api';
import { categoryPageSegments } from '../../utils/category-path';

// One recursive row of CategoryTreeNavComponent's sidebar -- expand/collapse is its own control (the +/- button).
// A root category (no parent -- a plain container for sub-categories, e.g. "Genres"/"Movie Guides") never has a
// page of its own: clicking its name only expands it (never navigates, and never collapses either -- that's what
// the +/- button is for). Every other category's name is a plain navigation link to its own page.
@Component({
  standalone: true,
  selector: 'app-category-tree-nav-node',
  imports: [CommonModule, RouterLink],
  styleUrl: './category-tree-nav-node.css',
  template: `
    <li class="tree-node">
      <div class="node-row">
        @if (!category.leaf || category.operator) {
          <button type="button" class="expand" (click)="toggle()" [attr.aria-label]="expanded.has(category.id) ? 'Collapse category' : 'Expand category'">
            @if (category.operator) { <span class="material-icons composition-expand">account_tree</span> } @else { {{ expanded.has(category.id) ? '−' : '+' }} }
          </button>
        } @else { <span class="expand-spacer"></span> }
        @if (isRoot) {
          <button type="button" class="category-link root-category" (click)="expand()" [title]="category.description || category.name">
            <span class="emoji">{{ category.icon || (category.operator === 'OR' ? '🔔' : category.operator ? '🧩' : '📁') }}</span><span class="category-name">{{ category.name }}</span>@if (category.operator; as operator) { <span class="and-badge" [class.or-badge]="operator === 'OR'">{{ operator }}</span> }
          </button>
        } @else {
          <a class="category-link" [class.active]="category.id === selectedCategoryId" [routerLink]="link()" [title]="category.description || category.name">
            <span class="emoji">{{ category.icon || (category.operator === 'OR' ? '🔔' : category.operator ? '🧩' : '📁') }}</span><span class="category-name">{{ category.name }}</span>@if (category.operator; as operator) { <span class="and-badge" [class.or-badge]="operator === 'OR'">{{ operator }}</span> }
          </a>
        }
      </div>
      @if (category.operator && expanded.has(category.id)) {
        <ul class="composition-components"><li *ngFor="let component of category.components; let first = first"><span class="and-badge" [class.or-badge]="category.operator === 'OR'">{{ first ? 'IS' : category.operator }}</span><span class="emoji">{{ component.icon || '📁' }}</span>{{ component.name }}</li></ul>
      }
      @if (!category.leaf && expanded.has(category.id)) {
        <ul>
          @for (child of category.children; track child.id) {
            <app-category-tree-nav-node [category]="child" [ancestors]="childAncestors" [expanded]="expanded" [selectedCategoryId]="selectedCategoryId" />
          }
        </ul>
      }
    </li>
  `
})
export class CategoryTreeNavNodeComponent {
  @Input({ required: true }) category!: MovieCategory;
  @Input() ancestors: MovieCategory[] = [];
  // Shared by reference across the whole tree (not copied per node) -- toggling one node's expand state just
  // mutates this same Set, so every other node sees the same up-to-date membership without any event plumbing.
  @Input() expanded = new Set<number>();
  @Input() selectedCategoryId: number | null = null;

  get isRoot(): boolean {
    return this.category.parentId == null;
  }

  get childAncestors(): MovieCategory[] {
    return [...this.ancestors, this.category];
  }

  toggle(): void {
    this.expanded.has(this.category.id) ? this.expanded.delete(this.category.id) : this.expanded.add(this.category.id);
  }

  // Root categories only ever expand on a name click -- never collapse (the +/- button already covers that), and
  // never navigate (they have no page of their own).
  expand(): void {
    this.expanded.add(this.category.id);
  }

  link(): (string | number)[] {
    return categoryPageSegments([...this.ancestors, this.category]);
  }
}
