import { CommonModule } from '@angular/common';
import { Component, EventEmitter, Input, Output, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../services/auth';
import { Movie, MoviesApiService } from '../../services/movies-api';
import { MoviePageNavigatorComponent } from '../movie-page-navigator/movie-page-navigator';
import { CategoryTreeDialogComponent } from '../category-tree-dialog/category-tree-dialog';

@Component({
  standalone: true,
  selector: 'app-movie-grid',
  imports: [CommonModule, RouterLink, MoviePageNavigatorComponent, CategoryTreeDialogComponent],
  templateUrl: './movie-grid.html',
  styleUrl: './movie-grid.css'
})
export class MovieGridComponent {
  private readonly moviesApi = inject(MoviesApiService);
  readonly auth = inject(AuthService);

  @Input() movies: Movie[] = [];
  @Input() loading = false;
  @Input() errorMessage = '';
  @Input() currentPage = 1;
  @Input() totalCount = 0;
  @Input() pageSize = 20;
  @Input() emptyMessage = 'No movies found';
  // Movie Personality pages only: movie.rating/rankPosition carry the personality's own synthetic-user rank
  // instead of the viewer's -- swaps the label/format accordingly and, unlike "Your Rating", shows it to every
  // viewer (not just signed-in ones), since it isn't the viewer's own data.
  @Input() showPersonalityRating = false;
  @Output() pageChange = new EventEmitter<number>();

  recommendationBusy: Record<string, boolean> = {};
  recommendationError = '';
  categoryMovie: Movie | null = null;

  openCategories(movie: Movie): void { this.categoryMovie = movie; }
  closeCategories(): void { this.categoryMovie = null; }

  poster(movie: Movie): string {
    return movie.poster && movie.poster !== 'N/A' ? movie.poster : '/images/movie-poster.jpg';
  }

  likeMovie(movie: Movie): void {
    if (this.recommendationBusy[movie.imdbId]) return;
    this.updateRecommendation(movie, () => this.moviesApi.recommendMovie(movie.imdbId));
  }

  dislikeMovie(movie: Movie): void {
    if (this.recommendationBusy[movie.imdbId]) return;
    this.updateRecommendation(movie, () => this.moviesApi.dislikeMovie(movie.imdbId));
  }

  clearRecommendation(movie: Movie): void {
    if (this.recommendationBusy[movie.imdbId]) return;
    this.updateRecommendation(movie, () => this.moviesApi.unrecommendMovie(movie.imdbId));
  }

  private updateRecommendation(movie: Movie, requestFactory: () => ReturnType<MoviesApiService['recommendMovie']>): void {
    if (!this.auth.token) return;
    this.recommendationBusy[movie.imdbId] = true;
    this.recommendationError = '';
    requestFactory().subscribe({
      next: updatedMovie => {
        movie.recommended = updatedMovie.recommended;
        movie.disliked = updatedMovie.disliked;
        this.recommendationBusy[movie.imdbId] = false;
      },
      error: err => {
        this.recommendationError = err?.error?.message ?? err?.message ?? 'Could not update recommendation';
        this.recommendationBusy[movie.imdbId] = false;
      }
    });
  }
}
