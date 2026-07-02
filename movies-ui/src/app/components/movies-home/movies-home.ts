import { CommonModule } from '@angular/common';
import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../services/auth';
import { MoviesApiService, Movie } from '../../services/movies-api';
import { Subscription } from 'rxjs';
import { MoviePageNavigatorComponent } from '../movie-page-navigator/movie-page-navigator';

@Component({
  standalone: true,
  selector: 'app-movies-home',
  imports: [CommonModule, RouterLink, MoviePageNavigatorComponent],
  templateUrl: './movies-home.html',
  styleUrl: './movies-home.css'
})
export class MoviesHomeComponent implements OnInit, OnDestroy {
  private readonly moviesApi = inject(MoviesApiService);
  readonly auth = inject(AuthService);

  movies: Movie[] = [];
  loading = false;
  errorMessage = '';
  currentPage = 1;
  totalCount = 0;
  readonly pageSize = this.moviesApi.moviePageSize;
  recommendationBusy: Record<string, boolean> = {};
  private authSub?: Subscription;

  ngOnInit(): void {
    this.authSub = this.auth.isAuthenticated$.subscribe(() => {
      this.loadMovies(1);
    });
  }

  ngOnDestroy(): void {
    this.authSub?.unsubscribe();
  }

  loadMovies(page = this.currentPage): void {
    this.loading = true;
    this.errorMessage = '';
    this.moviesApi.listMovies(page, this.pageSize).subscribe({
      next: moviePage => {
        this.movies = moviePage.movies;
        this.totalCount = moviePage.totalCount;
        this.currentPage = page;
        this.loading = false;
      },
      error: err => {
        this.errorMessage = err?.error?.message ?? err?.message ?? 'Could not load movies';
        this.loading = false;
      }
    });
  }

  poster(movie: Movie): string {
    return movie.poster && movie.poster !== 'N/A' ? movie.poster : '/images/movie-poster.jpg';
  }

  toggleRecommendation(movie: Movie): void {
    if (!this.auth.token || this.recommendationBusy[movie.imdbId]) return;

    this.recommendationBusy[movie.imdbId] = true;
    const request = movie.recommended
      ? this.moviesApi.unrecommendMovie(movie.imdbId)
      : this.moviesApi.recommendMovie(movie.imdbId);

    request.subscribe({
      next: updatedMovie => {
        movie.recommended = updatedMovie.recommended;
        this.recommendationBusy[movie.imdbId] = false;
      },
      error: err => {
        this.errorMessage = err?.error?.message ?? err?.message ?? 'Could not update recommendation';
        this.recommendationBusy[movie.imdbId] = false;
      }
    });
  }
}
