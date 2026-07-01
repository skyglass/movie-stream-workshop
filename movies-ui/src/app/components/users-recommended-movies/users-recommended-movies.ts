import { CommonModule } from '@angular/common';
import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Subscription } from 'rxjs';
import { AuthService } from '../../services/auth';
import { Movie, MoviesApiService } from '../../services/movies-api';

@Component({
  standalone: true,
  selector: 'app-users-recommended-movies',
  imports: [CommonModule, RouterLink],
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

  ngOnInit(): void {
    if (this.auth.token) {
      this.loadUsersRecommendedMovies();
    }
    this.authSub = this.auth.isAuthenticated$.subscribe(authenticated => {
      if (authenticated) {
        this.loadUsersRecommendedMovies();
      } else {
        this.movies = [];
      }
    });
  }

  ngOnDestroy(): void {
    this.authSub?.unsubscribe();
  }

  loadUsersRecommendedMovies(): void {
    this.loading = true;
    this.errorMessage = '';
    this.moviesApi.listUsersRecommendedMovies().subscribe({
      next: movies => {
        this.movies = movies;
        this.loading = false;
      },
      error: err => {
        this.errorMessage = err?.error?.message ?? err?.message ?? 'Could not load users recommended movies';
        this.loading = false;
      }
    });
  }

  poster(movie: Movie): string {
    return movie.poster && movie.poster !== 'N/A' ? movie.poster : '/images/movie-poster.jpg';
  }
}
