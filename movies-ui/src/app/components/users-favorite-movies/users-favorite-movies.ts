import { CommonModule } from '@angular/common';
import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Subscription } from 'rxjs';
import { AuthService } from '../../services/auth';
import { Movie, MoviesApiService, ParsedMovieSearch } from '../../services/movies-api';
import { MoviePageNavigatorComponent } from '../movie-page-navigator/movie-page-navigator';
import { MovieFilterSearchComponent } from '../movie-filter-search/movie-filter-search';

@Component({
  standalone: true,
  selector: 'app-users-favorite-movies',
  imports: [CommonModule, RouterLink, MoviePageNavigatorComponent, MovieFilterSearchComponent],
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
  activeYear = '';
  activeCategories: number[] = [];
  hasActiveFilter = false;

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
        this.activeYear = '';
        this.activeCategories = [];
        this.hasActiveFilter = false;
      }
    });
  }

  ngOnDestroy(): void {
    this.authSub?.unsubscribe();
  }

  loadUsersFavoriteMovies(page = this.currentPage): void {
    this.loading = true;
    this.errorMessage = '';
    this.moviesApi.listUsersFavoriteMovies(page, this.pageSize, this.activeFilter, this.activeYear, this.activeCategories).subscribe({
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

  applyFilter(search: ParsedMovieSearch): void {
    const categories = search.selectedCategories ?? [];
    if (search.keyword === this.activeFilter && search.year === this.activeYear && categories.join() === this.activeCategories.join()) return;
    this.activeFilter = search.keyword;
    this.activeYear = search.year;
    this.activeCategories = categories;
    this.hasActiveFilter = search.hasActiveFilter ?? false;
    this.loadUsersFavoriteMovies(1);
  }

  emptyMessage(): string {
    return this.hasActiveFilter ? 'No movies found' : 'No users favorite movies yet';
  }

  poster(movie: Movie): string {
    return movie.poster && movie.poster !== 'N/A' ? movie.poster : '/images/movie-poster.jpg';
  }

}
