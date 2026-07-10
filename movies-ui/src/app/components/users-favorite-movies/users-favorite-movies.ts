import { CommonModule } from '@angular/common';
import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Subscription } from 'rxjs';
import { AuthService } from '../../services/auth';
import { Movie, MoviesApiService } from '../../services/movies-api';
import { MoviePageNavigatorComponent } from '../movie-page-navigator/movie-page-navigator';

@Component({
  standalone: true,
  selector: 'app-users-favorite-movies',
  imports: [CommonModule, RouterLink, MoviePageNavigatorComponent],
  templateUrl: './users-favorite-movies.html',
  styleUrl: './users-favorite-movies.css'
})
export class UsersFavoriteMoviesComponent implements OnInit, OnDestroy {
  private readonly moviesApi = inject(MoviesApiService);
  readonly auth = inject(AuthService);
  private authSub?: Subscription;

  movies: Movie[] = [];
  loading = false;
  errorMessage = '';
  currentPage = 1;
  totalCount = 0;
  readonly pageSize = this.moviesApi.moviePageSize;
  filterText = '';
  activeFilter = '';
  private filterTimer?: ReturnType<typeof setTimeout>;

  ngOnInit(): void {
    if (this.auth.token) {
      this.loadUsersFavoriteMovies(1);
    }
    this.authSub = this.auth.isAuthenticated$.subscribe(authenticated => {
      if (authenticated) {
        this.loadUsersFavoriteMovies(1);
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

  loadUsersFavoriteMovies(page = this.currentPage): void {
    this.loading = true;
    this.errorMessage = '';
    this.moviesApi.listUsersFavoriteMovies(page, this.pageSize, this.activeFilter).subscribe({
      next: moviePage => {
        this.movies = moviePage.movies;
        this.totalCount = moviePage.totalCount;
        this.currentPage = page;
        this.loading = false;
      },
      error: err => {
        this.errorMessage = err?.error?.message ?? err?.message ?? 'Could not load users favorite movies';
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
        this.loadUsersFavoriteMovies(1);
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
        this.loadUsersFavoriteMovies(1);
      }
    }, 300);
  }

  clearFilter(): void {
    this.clearFilterTimer();
    const shouldReload = !!this.activeFilter || !!this.filterText;
    this.filterText = '';
    this.activeFilter = '';
    if (shouldReload) {
      this.loadUsersFavoriteMovies(1);
    }
  }

  poster(movie: Movie): string {
    return movie.poster && movie.poster !== 'N/A' ? movie.poster : '/images/movie-poster.jpg';
  }

  private clearFilterTimer(): void {
    if (this.filterTimer) {
      clearTimeout(this.filterTimer);
      this.filterTimer = undefined;
    }
  }
}
