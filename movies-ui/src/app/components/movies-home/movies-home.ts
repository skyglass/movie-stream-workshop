import { CommonModule } from '@angular/common';
import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../services/auth';
import { MoviesApiService, Movie } from '../../services/movies-api';
import { Subscription } from 'rxjs';

@Component({
  standalone: true,
  selector: 'app-movies-home',
  imports: [CommonModule, RouterLink],
  templateUrl: './movies-home.html',
  styleUrl: './movies-home.css'
})
export class MoviesHomeComponent implements OnInit, OnDestroy {
  private readonly moviesApi = inject(MoviesApiService);
  readonly auth = inject(AuthService);

  movies: Movie[] = [];
  loading = false;
  errorMessage = '';
  private authSub?: Subscription;

  ngOnInit(): void {
    if (this.auth.token) {
      this.loadMovies();
    }
    this.authSub = this.auth.isAuthenticated$.subscribe(authenticated => {
      if (authenticated && this.movies.length === 0 && !this.loading) {
        this.loadMovies();
      }
    });
  }

  ngOnDestroy(): void {
    this.authSub?.unsubscribe();
  }

  loadMovies(): void {
    this.loading = true;
    this.errorMessage = '';
    this.moviesApi.listMovies().subscribe({
      next: movies => {
        this.movies = movies;
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
}
