import { CommonModule } from '@angular/common';
import { Component, ElementRef, EventEmitter, Input, Output, inject } from '@angular/core';

@Component({
  standalone: true,
  selector: 'app-movie-page-navigator',
  imports: [CommonModule],
  templateUrl: './movie-page-navigator.html',
  styleUrl: './movie-page-navigator.css'
})
export class MoviePageNavigatorComponent {
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);

  @Input() currentPage = 1;
  @Input() totalCount = 0;
  @Input() pageSize = 50;
  @Output() pageChange = new EventEmitter<number>();

  get totalPages(): number {
    if (this.pageSize < 1) return 0;
    return Math.ceil(this.totalCount / this.pageSize);
  }

  get hasPrevious(): boolean {
    return this.currentPage > 1;
  }

  get hasNext(): boolean {
    return this.currentPage < this.totalPages;
  }

  pages(): number[] {
    return Array.from({ length: this.totalPages }, (_, index) => index + 1);
  }

  goToPage(page: number): void {
    if (page < 1 || page > this.totalPages || page === this.currentPage) return;
    this.pageChange.emit(page);
    this.scrollToListStart();
  }

  private scrollToListStart(): void {
    requestAnimationFrame(() => {
      const target = this.host.nativeElement.closest('section') ?? this.host.nativeElement;
      const prefersReducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
      target.scrollIntoView({ block: 'start', behavior: prefersReducedMotion ? 'auto' : 'smooth' });
    });
  }
}
