import { CommonModule } from '@angular/common';
import { Component, OnDestroy, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Subscription, timer } from 'rxjs';
import { AuthService } from '../../services/auth';
import { MoviesApiService, MovieChallenge, MovieChallengeMovie } from '../../services/movies-api';

@Component({
  standalone: true,
  selector: 'app-movie-challenge-page',
  imports: [CommonModule, RouterLink],
  templateUrl: './movie-challenge-page.html',
  styleUrl: './movie-challenge-page.css'
})
export class MovieChallengePageComponent implements OnDestroy {
  private readonly moviesApi = inject(MoviesApiService);
  readonly auth = inject(AuthService);
  private nextButtonSub?: Subscription;

  loading = false;
  saving = false;
  showNext = false;
  challenge: MovieChallenge | null = null;
  selectedMovieId = '';
  errorMessage = '';
  loaded = false;

  ngOnDestroy(): void {
    this.nextButtonSub?.unsubscribe();
  }

  loadNextChallenge(): void {
    if (!this.auth.token) return;

    this.loaded = true;
    this.nextButtonSub?.unsubscribe();
    this.loading = true;
    this.saving = false;
    this.showNext = false;
    this.challenge = null;
    this.selectedMovieId = '';
    this.errorMessage = '';

    this.moviesApi.nextMovieChallenge().subscribe({
      next: challenge => {
        this.challenge = challenge;
        this.loading = false;
      },
      error: err => {
        this.errorMessage = err?.error?.message ?? err?.message ?? 'Could not load movie challenge';
        this.loading = false;
      }
    });
  }

  poster(movie: MovieChallengeMovie): string {
    return movie.poster && movie.poster !== 'N/A' ? movie.poster : '/images/movie-poster.jpg';
  }

  selectMovie(movie: MovieChallengeMovie): void {
    if (!this.challenge || this.selectedMovieId || this.saving) return;

    this.selectedMovieId = movie.imdbId;
    this.saving = true;
    this.errorMessage = '';
    this.moviesApi.selectMovieChallengeWinner(
      this.challenge.movie1.imdbId,
      this.challenge.movie2.imdbId,
      movie.imdbId
    ).subscribe({
      next: () => {
        this.saving = false;
        this.nextButtonSub?.unsubscribe();
        this.nextButtonSub = timer(520).subscribe(() => this.showNext = true);
      },
      error: err => {
        this.selectedMovieId = '';
        this.saving = false;
        this.errorMessage = err?.error?.message ?? err?.message ?? 'Could not save movie challenge';
      }
    });
  }

  isWinner(movie: MovieChallengeMovie): boolean {
    return this.selectedMovieId === movie.imdbId;
  }

  isLoser(movie: MovieChallengeMovie): boolean {
    return !!this.selectedMovieId && this.selectedMovieId !== movie.imdbId;
  }
}
