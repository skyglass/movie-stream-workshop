import { CommonModule } from '@angular/common';
import { Component, Input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { MovieCategory } from '../../services/movies-api';
import { categoryPageSegments } from '../../utils/category-path';

@Component({
  standalone: true,
  selector: 'app-movie-category-path-view',
  imports: [CommonModule, RouterLink],
  template: `
    <ul class="category-path-tree">
      @for (category of visibleCategories(); track category.id) {
        <li>
          <a class="category-path-label" [class.checked]="category.checked" [title]="category.description || category.name"
            [routerLink]="link(category)">
            <span class="emoji">{{ category.icon || '📁' }}</span><span>{{ category.name }}</span>
            @if (category.checked) { <span class="material-icons check" aria-hidden="true">check_circle</span> }
          </a>
          @if (hasVisibleChildren(category)) {
            <app-movie-category-path-view [categories]="category.children" [ancestors]="childAncestors(category)" />
          }
        </li>
      }
    </ul>
  `,
  styles: [`
    .category-path-tree, .category-path-tree ul { list-style: none; margin: 0; padding-left: 1.35rem; }
    .category-path-tree { padding-left: 0; }
    .category-path-label { display: inline-flex; align-items: center; gap: .4rem; padding: .25rem 0; text-decoration: none; color: inherit; }
    .category-path-label:hover { text-decoration: underline; }
    .category-path-label.checked { font-weight: 700; color: #15803d; }
    .emoji { font-size: 1.1rem; }
    .check { font-size: 1rem; color: #15803d; }
  `]
})
export class MovieCategoryPathViewComponent {
  @Input({ required: true }) categories: MovieCategory[] = [];
  // Ancestor chain leading down to `categories` (exclusive of them) -- threaded through the recursion so each
  // node's own link can be built from its full root-to-node path, not just its own name/id.
  @Input() ancestors: MovieCategory[] = [];

  visibleCategories(): MovieCategory[] {
    return this.categories.filter(category => category.checked || this.hasCheckedDescendant(category));
  }

  hasVisibleChildren(category: MovieCategory): boolean {
    return category.children.some(child => child.checked || this.hasCheckedDescendant(child));
  }

  childAncestors(category: MovieCategory): MovieCategory[] {
    return [...this.ancestors, category];
  }

  link(category: MovieCategory): (string | number)[] {
    return categoryPageSegments([...this.ancestors, category]);
  }

  private hasCheckedDescendant(category: MovieCategory): boolean {
    return category.children.some(child => child.checked || this.hasCheckedDescendant(child));
  }
}
