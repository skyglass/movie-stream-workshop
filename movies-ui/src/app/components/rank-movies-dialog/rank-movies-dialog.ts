import { CommonModule } from '@angular/common';
import { Component, EventEmitter, Input, OnInit, Output, inject } from '@angular/core';
import { CdkDragDrop, DragDropModule, moveItemInArray } from '@angular/cdk/drag-drop';
import { ScrollingModule } from '@angular/cdk/scrolling';
import { Movie, MoviesApiService } from '../../services/movies-api';

// "Rank Movies as Personality" dialog -- loads an append-only 100-movie prefix. Submit sends that prefix; the
// backend appends the untouched current suffix before performing the Personality's normal full rank rebuild.
@Component({
  standalone: true,
  selector: 'app-rank-movies-dialog',
  imports: [CommonModule, DragDropModule, ScrollingModule],
  templateUrl: './rank-movies-dialog.html',
  styleUrl: './rank-movies-dialog.css'
})
export class RankMoviesDialogComponent implements OnInit {
  private readonly api = inject(MoviesApiService);

  private static readonly PAGE_SIZE = 100;

  @Input({ required: true }) movieGuideId!: number;
  @Input({ required: true }) guideCategoryId!: number;
  @Output() closed = new EventEmitter<void>();
  @Output() rankingSubmitted = new EventEmitter<void>();

  movies: Movie[] = [];
  loading = true;
  loadingNext = false;
  submitting = false;
  errorMessage = '';
  totalCount = 0;

  ngOnInit(): void {
    this.loadPage(1, false);
  }

  get hasNext(): boolean {
    return this.movies.length < this.totalCount;
  }

  private loadPage(pageNumber: number, append: boolean): void {
    if (append) this.loadingNext = true;
    else this.loading = true;
    this.errorMessage = '';
    this.api.listPersonalityMovies(this.movieGuideId, pageNumber, RankMoviesDialogComponent.PAGE_SIZE, '', '', [this.guideCategoryId]).subscribe({
      next: page => {
        this.movies = append ? [...this.movies, ...page.movies] : page.movies;
        this.totalCount = page.totalCount;
        this.loading = false;
        this.loadingNext = false;
      },
      error: err => {
        this.errorMessage = err?.error?.message ?? err?.message ?? 'Could not load movies';
        this.loading = false;
        this.loadingNext = false;
      }
    });
  }

  loadNext(): void {
    if (!this.hasNext || this.loadingNext || this.submitting) return;
    const nextPage = Math.floor(this.movies.length / RankMoviesDialogComponent.PAGE_SIZE) + 1;
    this.loadPage(nextPage, true);
  }

  rankOf(movie: Movie): number {
    return this.movies.indexOf(movie) + 1;
  }

  drop(event: CdkDragDrop<Movie[]>): void {
    moveItemInArray(this.movies, event.previousIndex, event.currentIndex);
  }

  // Drag-to-scroll only gets you as far as the dialog can auto-scroll while you're mid-drag, which is still
  // tedious across dozens of movies -- these give an instant jump for the common "make this my favorite" /
  // "push this to the bottom" cases without needing to drag across the whole (possibly scrolled-off) list.
  moveToTop(movie: Movie): void {
    const index = this.movies.indexOf(movie);
    if (index <= 0) return;
    moveItemInArray(this.movies, index, 0);
  }

  moveToBottom(movie: Movie): void {
    const index = this.movies.indexOf(movie);
    if (index === -1 || index === this.movies.length - 1) return;
    moveItemInArray(this.movies, index, this.movies.length - 1);
  }

  poster(movie: Movie): string {
    return movie.poster && movie.poster !== 'N/A' ? movie.poster : '/images/movie-poster.jpg';
  }

  submit(): void {
    if (this.submitting || this.loadingNext || this.movies.length === 0) return;
    this.submitting = true;
    this.errorMessage = '';
    const orderedImdbIds = this.movies.map(movie => movie.imdbId);
    this.api.submitPersonalityRanking(this.movieGuideId, orderedImdbIds).subscribe({
      next: () => {
        this.submitting = false;
        this.rankingSubmitted.emit();
      },
      error: err => {
        this.submitting = false;
        this.errorMessage = err?.error?.message ?? err?.message ?? 'Could not submit the ranking';
      }
    });
  }

  close(): void {
    this.closed.emit();
  }
}
