import { CommonModule } from '@angular/common';
import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Subscription } from 'rxjs';
import { AuthService } from '../../services/auth';
import { Movie, MoviesApiService } from '../../services/movies-api';
import { MoviePageNavigatorComponent } from '../movie-page-navigator/movie-page-navigator';

@Component({
  standalone: true,
  selector: 'app-users-recommended-movies',
  imports: [CommonModule, RouterLink, MoviePageNavigatorComponent],
  templateUrl: './users-recommended-movies.html',
  styleUrl: './users-recommended-movies.css'
})
export class UsersRecommendedMoviesComponent implements OnInit, OnDestroy {
  private readonly moviesApi = inject(MoviesApiService);
  readonly auth = inject(AuthService);
  private authSub?: Subscription;

  movies: Movie[] = [];
  loading = false;
  errorMessage = '';
  currentPage = 1;
  totalCount = 0;
  readonly pageSize = this.moviesApi.moviePageSize;
  recommendationBusy: Record<string, boolean> = {};
  filterText = '';
  activeFilter = '';
  private filterTimer?: ReturnType<typeof setTimeout>;

  ngOnInit(): void {
    if (this.auth.token) {
      this.loadUsersRecommendedMovies(1);
    }
    this.authSub = this.auth.isAuthenticated$.subscribe(authenticated => {
      if (authenticated) {
        this.loadUsersRecommendedMovies(1);
      } else {
        this.movies = [];
        this.totalCount = 0;
        this.currentPage = 1;
        this.filterText = '';
        this.activeFilter = '';
      }
    });
  }

  ngOnDestroy(): void {
    this.authSub?.unsubscribe();
    this.clearFilterTimer();
  }

  loadUsersRecommendedMovies(page = this.currentPage): void {
    this.loading = true;
    this.errorMessage = '';
    this.moviesApi.listUsersRecommendedMovies(page, this.pageSize, this.activeFilter).subscribe({
      next: moviePage => {
        this.movies = moviePage.movies;
        this.totalCount = moviePage.totalCount;
        this.currentPage = page;
        this.loading = false;
      },
      error: err => {
        this.errorMessage = err?.error?.message ?? err?.message ?? 'Could not load users recommended movies';
        this.loading = false;
      }
    });
  }

  onFilterInput(event: Event): void {
    this.filterText = (event.target as HTMLInputElement).value;
    this.clearFilterTimer();

    const filter = this.filterText.trim();
    if (!filter) {
      if (this.activeFilter) {
        this.activeFilter = '';
        this.loadUsersRecommendedMovies(1);
      }
      return;
    }

    if (filter.length <= 3) {
      return;
    }

    this.filterTimer = setTimeout(() => {
      const nextFilter = this.filterText.trim();
      if (nextFilter.length > 3 && nextFilter !== this.activeFilter) {
        this.activeFilter = nextFilter;
        this.loadUsersRecommendedMovies(1);
      }
    }, 300);
  }

  clearFilter(): void {
    this.clearFilterTimer();
    const shouldReload = !!this.activeFilter || !!this.filterText;
    this.filterText = '';
    this.activeFilter = '';
    if (shouldReload) {
      this.loadUsersRecommendedMovies(1);
    }
  }

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
    this.recommendationBusy[movie.imdbId] = true;
    this.errorMessage = '';
    requestFactory().subscribe({
      next: updatedMovie => {
        movie.recommended = updatedMovie.recommended;
        movie.disliked = updatedMovie.disliked;
        this.recommendationBusy[movie.imdbId] = false;
      },
      error: err => {
        this.errorMessage = err?.error?.message ?? err?.message ?? 'Could not update recommendation';
        this.recommendationBusy[movie.imdbId] = false;
      }
    });
  }

  private clearFilterTimer(): void {
    if (this.filterTimer) {
      clearTimeout(this.filterTimer);
      this.filterTimer = undefined;
    }
  }
}
