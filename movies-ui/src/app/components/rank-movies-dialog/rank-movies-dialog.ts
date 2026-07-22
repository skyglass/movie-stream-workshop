import { CommonModule } from '@angular/common';
import { Component, EventEmitter, Input, OnInit, Output, inject } from '@angular/core';
import { CdkDragDrop, DragDropModule, moveItemInArray } from '@angular/cdk/drag-drop';
import { ScrollingModule } from '@angular/cdk/scrolling';
import { Movie, MoviesApiService } from '../../services/movies-api';

// "Rank Movies as Personality" dialog -- Movie Personality only. Unlike DeleteMoviesSelectorComponent, this loads
// the ENTIRE personality movie set in one unpaginated request (drag-and-drop reordering across paginated pages
// isn't meaningful), and has no filter/search: a filtered view combined with full-list drag-and-drop risks losing
// off-screen items' positions, so v1 keeps this simple. The displayed order is the payload -- rank is purely the
// array position (recomputed on every render via rankOf), never a separately-tracked per-item field.
@Component({
  standalone: true,
  selector: 'app-rank-movies-dialog',
  imports: [CommonModule, DragDropModule, ScrollingModule],
  templateUrl: './rank-movies-dialog.html',
  styleUrl: './rank-movies-dialog.css'
})
export class RankMoviesDialogComponent implements OnInit {
  private readonly api = inject(MoviesApiService);

  // Bounded so the dialog stays renderable/draggable in the DOM -- matches the CSV-import cap already used
  // elsewhere for a single guide (MovieGuideService.MAX_CSV_MOVIES). A personality larger than this shows a
  // truncation warning rather than silently dropping movies from the ranking.
  private static readonly MAX_MOVIES = 2000;

  @Input({ required: true }) movieGuideId!: number;
  @Input({ required: true }) guideCategoryId!: number;
  @Output() closed = new EventEmitter<void>();
  @Output() rankingSubmitted = new EventEmitter<void>();

  movies: Movie[] = [];
  loading = true;
  submitting = false;
  errorMessage = '';
  truncated = false;

  ngOnInit(): void {
    this.api.listPersonalityMovies(this.movieGuideId, 1, RankMoviesDialogComponent.MAX_MOVIES, '', '', [this.guideCategoryId]).subscribe({
      next: page => {
        this.movies = page.movies;
        this.truncated = page.totalCount > RankMoviesDialogComponent.MAX_MOVIES;
        this.loading = false;
      },
      error: err => {
        this.errorMessage = err?.error?.message ?? err?.message ?? 'Could not load movies';
        this.loading = false;
      }
    });
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
    if (this.submitting || this.movies.length === 0) return;
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
