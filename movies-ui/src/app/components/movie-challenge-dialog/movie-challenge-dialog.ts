import { CommonModule } from '@angular/common';
import { Component, EventEmitter, Input, OnChanges, OnDestroy, Output, SimpleChanges, inject } from '@angular/core';
import { Subscription, timer } from 'rxjs';
import { MoviesApiService, MovieChallenge, MovieChallengeMovie } from '../../services/movies-api';

@Component({
  standalone: true,
  selector: 'app-movie-challenge-dialog',
  imports: [CommonModule],
  templateUrl: './movie-challenge-dialog.html',
  styleUrl: './movie-challenge-dialog.css'
})
export class MovieChallengeDialogComponent implements OnChanges, OnDestroy {
  private readonly moviesApi = inject(MoviesApiService);
  private nextButtonSub?: Subscription;

  @Input() open = false;
  @Input() availabilityVersion = 0;
  @Output() closed = new EventEmitter<void>();
  @Output() challengeConsumed = new EventEmitter<void>();

  loading = false;
  saving = false;
  showNext = false;
  challenge: MovieChallenge | null = null;
  selectedMovieId = '';
  errorMessage = '';

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['open']?.currentValue === true) {
      this.loadNextChallenge();
      return;
    }

    if (changes['availabilityVersion'] && this.open && !this.challenge && !this.loading && !this.saving) {
      this.loadNextChallenge();
    }
  }

  ngOnDestroy(): void {
    this.nextButtonSub?.unsubscribe();
  }

  close(): void {
    this.closed.emit();
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
        this.challengeConsumed.emit();
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

  loadNextChallenge(): void {
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

  isWinner(movie: MovieChallengeMovie): boolean {
    return this.selectedMovieId === movie.imdbId;
  }

  isLoser(movie: MovieChallengeMovie): boolean {
    return !!this.selectedMovieId && this.selectedMovieId !== movie.imdbId;
  }
}
