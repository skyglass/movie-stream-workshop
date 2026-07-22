import { CommonModule } from '@angular/common';
import { Component, EventEmitter, OnInit, Output, inject } from '@angular/core';
import { CdkDragDrop, DragDropModule, moveItemInArray } from '@angular/cdk/drag-drop';
import { ScrollingModule } from '@angular/cdk/scrolling';
import { Movie, MoviesApiService } from '../../services/movies-api';

@Component({
  standalone: true,
  selector: 'app-edit-favorite-ranks-dialog',
  imports: [CommonModule, DragDropModule, ScrollingModule],
  templateUrl: './edit-favorite-ranks-dialog.html',
  styleUrl: './edit-favorite-ranks-dialog.css'
})
export class EditFavoriteRanksDialogComponent implements OnInit {
  private static readonly PAGE_SIZE = 100;
  private readonly api = inject(MoviesApiService);

  @Output() closed = new EventEmitter<void>();
  @Output() rankingSubmitted = new EventEmitter<void>();

  movies: Movie[] = [];
  totalCount = 0;
  loading = true;
  loadingNext = false;
  submitting = false;
  errorMessage = '';

  ngOnInit(): void {
    this.loadPage(1, false);
  }

  get hasNext(): boolean {
    return this.movies.length < this.totalCount;
  }

  rankOf(movie: Movie): number {
    return this.movies.indexOf(movie) + 1;
  }

  drop(event: CdkDragDrop<Movie[]>): void {
    moveItemInArray(this.movies, event.previousIndex, event.currentIndex);
  }

  moveToTop(movie: Movie): void {
    const index = this.movies.indexOf(movie);
    if (index > 0) moveItemInArray(this.movies, index, 0);
  }

  moveToBottom(movie: Movie): void {
    const index = this.movies.indexOf(movie);
    if (index !== -1 && index < this.movies.length - 1) {
      moveItemInArray(this.movies, index, this.movies.length - 1);
    }
  }

  loadNext(): void {
    if (!this.hasNext || this.loadingNext || this.submitting) return;
    const nextPage = Math.floor(this.movies.length / EditFavoriteRanksDialogComponent.PAGE_SIZE) + 1;
    this.loadPage(nextPage, true);
  }

  poster(movie: Movie): string {
    return movie.poster && movie.poster !== 'N/A' ? movie.poster : '/images/movie-poster.jpg';
  }

  submit(): void {
    if (this.submitting || this.loading || this.loadingNext || this.movies.length === 0) return;
    this.submitting = true;
    this.errorMessage = '';
    this.api.submitFavoriteMoviesRanking(this.movies.map(movie => movie.imdbId)).subscribe({
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
    if (!this.submitting) this.closed.emit();
  }

  private loadPage(page: number, append: boolean): void {
    if (append) this.loadingNext = true;
    else this.loading = true;
    this.errorMessage = '';
    this.api.listFavoriteMovies(page, EditFavoriteRanksDialogComponent.PAGE_SIZE).subscribe({
      next: result => {
        this.movies = append ? [...this.movies, ...result.movies] : result.movies;
        this.totalCount = result.totalCount;
        this.loading = false;
        this.loadingNext = false;
      },
      error: err => {
        this.errorMessage = err?.error?.message ?? err?.message ?? 'Could not load favorite movies';
        this.loading = false;
        this.loadingNext = false;
      }
    });
  }
}
