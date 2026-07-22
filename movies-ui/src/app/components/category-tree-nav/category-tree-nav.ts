import { CommonModule } from '@angular/common';
import { Component, EventEmitter, Input, OnChanges, OnInit, Output, SimpleChanges, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { MovieCategory, MoviesApiService } from '../../services/movies-api';
import { CategoryTreeNavNodeComponent } from './category-tree-nav-node';
import { findCategoryPath } from '../../utils/category-path';

// Persistent, always-visible category navigation sidebar for the category browsing page -- fetches the full tree
// once and keeps it mounted across in-page navigation (breadcrumb clicks, tree clicks), auto-expanding to whatever
// category is currently selected. Every node is a plain link (see CategoryTreeNavNodeComponent), not a checkbox --
// this is a navigation control, not a category-assignment picker like CategoryTreeDialogComponent.
@Component({
  standalone: true,
  selector: 'app-category-tree-nav',
  imports: [CommonModule, RouterLink, CategoryTreeNavNodeComponent],
  templateUrl: './category-tree-nav.html',
  styleUrl: './category-tree-nav.css'
})
export class CategoryTreeNavComponent implements OnInit, OnChanges {
  private readonly api = inject(MoviesApiService);
  @Input() selectedCategoryId: number | null = null;
  // Lets the host (the category page) derive its own breadcrumb path from the exact same tree data, without a
  // second fetch -- emitted once, right after the tree loads.
  @Output() categoriesLoaded = new EventEmitter<MovieCategory[]>();

  categories: MovieCategory[] = [];
  expanded = new Set<number>();
  loading = true;
  errorMessage = '';

  ngOnInit(): void {
    this.load();
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (!changes['selectedCategoryId'] || changes['selectedCategoryId'].isFirstChange() || !this.categories.length) return;
    this.expandAncestorsOf(this.selectedCategoryId);
  }

  private load(): void {
    this.loading = true;
    this.errorMessage = '';
    this.api.getCategoryTree().subscribe({
      next: categories => {
        this.categories = categories;
        this.loading = false;
        this.expandAncestorsOf(this.selectedCategoryId);
        this.categoriesLoaded.emit(categories);
      },
      error: error => {
        this.errorMessage = error?.error?.message ?? error?.message ?? 'Could not load categories';
        this.loading = false;
      }
    });
  }

  private expandAncestorsOf(targetId: number | null): void {
    if (targetId == null) return;
    const path = findCategoryPath(this.categories, targetId);
    // Every ancestor except the target itself needs expanding to reveal it -- a leaf target has no children of its
    // own to expand.
    path?.slice(0, -1).forEach(category => this.expanded.add(category.id));
  }
}
