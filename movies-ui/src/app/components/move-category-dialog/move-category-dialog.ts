import { CommonModule } from '@angular/common';
import { Component, EventEmitter, Input, OnInit, Output, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MovieCategory, MoviesApiService } from '../../services/movies-api';
import { CategoryTreeNodeComponent } from '../category-tree-dialog/category-tree-node';

@Component({
  standalone: true,
  selector: 'app-move-category-dialog',
  imports: [CommonModule, FormsModule, CategoryTreeNodeComponent],
  templateUrl: './move-category-dialog.html',
  styleUrl: './move-category-dialog.css'
})
export class MoveCategoryDialogComponent implements OnInit {
  private readonly api = inject(MoviesApiService);
  @Input({ required: true }) category!: MovieCategory;
  @Input({ required: true }) sourceParentId!: number;
  // Set for a private watchlist's own category picker -- moves are then scoped to that watchlist's private
  // sandbox (private_category) instead of the public category tree. rootCategoryId must be the watchlist's own
  // anchor category id (the outer category-tree-dialog already carries this for the same reason -- see its own
  // rootCategoryId doc).
  @Input() watchlistId?: number;
  @Input() rootCategoryId?: number;
  @Output() closed = new EventEmitter<void>();
  @Output() moved = new EventEmitter<void>();

  categories: MovieCategory[] = [];
  expanded = new Set<number>();
  selected = new Set<number>();
  loading = true;
  saving = false;
  errorMessage = '';

  ngOnInit(): void {
    const request = this.watchlistId != null && this.rootCategoryId != null
      ? this.api.getPrivateCategorySubtree(this.rootCategoryId)
      : this.rootCategoryId != null
        ? this.api.getCategorySubtree(this.rootCategoryId)
        : this.api.getCategoryTree();
    request.subscribe({
      next: categories => { this.categories = categories; this.loading = false; },
      error: error => this.fail(error)
    });
  }

  toggleExpanded(category: MovieCategory): void {
    this.expanded.has(category.id) ? this.expanded.delete(category.id) : this.expanded.add(category.id);
  }

  selectTarget(category: MovieCategory, checked: boolean): void {
    this.selected.clear();
    if (checked) this.selected.add(category.id);
  }

  selectRoot(): void {
    this.selected.clear();
  }

  targetId(): number | null {
    return this.selected.size ? [...this.selected][0] : null;
  }

  submit(): void {
    this.saving = true;
    this.errorMessage = '';
    const operation = this.watchlistId != null
      ? this.api.movePrivateCategory(this.category.id, this.sourceParentId, this.targetId())
      : this.api.moveCategory(this.category.id, this.sourceParentId, this.targetId());
    operation.subscribe({
      next: () => { this.saving = false; this.moved.emit(); },
      error: error => this.fail(error)
    });
  }

  private fail(error: any): void {
    this.errorMessage = error?.error?.detail ?? error?.error?.message ?? error?.message ?? 'Move request failed';
    this.loading = false; this.saving = false;
  }
}
