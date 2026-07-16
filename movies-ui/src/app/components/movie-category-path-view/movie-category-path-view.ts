import { CommonModule } from '@angular/common';
import { Component, Input } from '@angular/core';
import { MovieCategory } from '../../services/movies-api';

@Component({
  standalone: true,
  selector: 'app-movie-category-path-view',
  imports: [CommonModule],
  template: `
    <ul class="category-path-tree">
      @for (category of visibleCategories(); track category.id) {
        <li>
          <span class="category-path-label" [class.checked]="category.checked" [title]="category.description || category.name">
            <span class="emoji">{{ category.icon || '📁' }}</span><span>{{ category.name }}</span>
            @if (category.checked) { <span class="material-icons check" aria-hidden="true">check_circle</span> }
          </span>
          @if (hasVisibleChildren(category)) {
            <app-movie-category-path-view [categories]="category.children" />
          }
        </li>
      }
    </ul>
  `,
  styles: [`
    .category-path-tree, .category-path-tree ul { list-style: none; margin: 0; padding-left: 1.35rem; }
    .category-path-tree { padding-left: 0; }
    .category-path-label { display: inline-flex; align-items: center; gap: .4rem; padding: .25rem 0; }
    .category-path-label.checked { font-weight: 700; color: #15803d; }
    .emoji { font-size: 1.1rem; }
    .check { font-size: 1rem; color: #15803d; }
  `]
})
export class MovieCategoryPathViewComponent {
  @Input({ required: true }) categories: MovieCategory[] = [];

  visibleCategories(): MovieCategory[] {
    return this.categories.filter(category => category.checked || this.hasCheckedDescendant(category));
  }

  hasVisibleChildren(category: MovieCategory): boolean {
    return category.children.some(child => child.checked || this.hasCheckedDescendant(child));
  }

  private hasCheckedDescendant(category: MovieCategory): boolean {
    return category.children.some(child => child.checked || this.hasCheckedDescendant(child));
  }
}
