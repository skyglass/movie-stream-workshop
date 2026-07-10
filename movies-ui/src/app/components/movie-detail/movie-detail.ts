import { CommonModule } from '@angular/common';
import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { Subscription, combineLatest } from 'rxjs';
import { AuthService } from '../../services/auth';
import { Movie, MovieRankHistory, MovieRankHistoryMovie, MoviesApiService } from '../../services/movies-api';

@Component({
  standalone: true,
  selector: 'app-movie-detail',
  imports: [CommonModule, ReactiveFormsModule, RouterLink],
  templateUrl: './movie-detail.html',
  styleUrl: './movie-detail.css'
})
export class MovieDetailComponent implements OnInit, OnDestroy {
  private readonly route = inject(ActivatedRoute);
  private readonly fb = inject(FormBuilder);
  private readonly moviesApi = inject(MoviesApiService);
  readonly auth = inject(AuthService);
  private pageSub?: Subscription;
  private movieSub?: Subscription;
  private rankHistorySub?: Subscription;
  private imdbId = '';

  movie: Movie | null = null;
  rankHistory: MovieRankHistory | null = null;
  loading = false;
  rankHistoryLoading = false;
  saving = false;
  recommendationSaving = false;
  replaySaving = false;
  errorMessage = '';
  rankHistoryErrorMessage = '';

  readonly commentForm = this.fb.group({
    text: ['', [Validators.required, Validators.maxLength(4000)]]
  });

  ngOnInit(): void {
    this.pageSub = combineLatest([this.route.paramMap, this.auth.isAuthenticated$]).subscribe(([params, authenticated]) => {
      this.imdbId = params.get('imdbId') ?? '';
      if (!authenticated || !this.imdbId) {
        this.clearMovie();
      } else {
        this.loadMovie(this.imdbId);
      }
    });
  }

  ngOnDestroy(): void {
    this.pageSub?.unsubscribe();
    this.movieSub?.unsubscribe();
    this.rankHistorySub?.unsubscribe();
  }

  loadMovie(imdbId: string): void {
    this.movieSub?.unsubscribe();
    this.rankHistorySub?.unsubscribe();
    this.loading = true;
    this.rankHistoryLoading = false;
    this.errorMessage = '';
    this.rankHistoryErrorMessage = '';
    this.rankHistory = null;
    this.movieSub = this.moviesApi.getMovie(imdbId).subscribe({
      next: movie => {
        if (!this.auth.token || this.imdbId !== imdbId) return;
        this.movie = movie;
        this.loading = false;
        this.loadRankHistory(imdbId);
      },
      error: err => {
        if (!this.auth.token || this.imdbId !== imdbId) return;
        this.errorMessage = err?.error?.message ?? err?.message ?? 'Could not load movie';
        this.loading = false;
      }
    });
  }

  loadRankHistory(imdbId: string): void {
    this.rankHistorySub?.unsubscribe();
    this.rankHistoryLoading = true;
    this.rankHistoryErrorMessage = '';
    this.rankHistorySub = this.moviesApi.getMovieRankHistory(imdbId).subscribe({
      next: rankHistory => {
        if (!this.auth.token || this.imdbId !== imdbId) return;
        this.rankHistory = rankHistory;
        this.rankHistoryLoading = false;
      },
      error: err => {
        if (!this.auth.token || this.imdbId !== imdbId) return;
        this.rankHistoryErrorMessage = err?.error?.message ?? err?.message ?? 'Could not load rank history';
        this.rankHistoryLoading = false;
      }
    });
  }

  addComment(): void {
    if (!this.movie || this.commentForm.invalid) return;
    this.saving = true;
    const text = this.commentForm.getRawValue().text ?? '';
    this.moviesApi.addComment(this.movie.imdbId, text).subscribe({
      next: movie => {
        if (!this.auth.token) return;
        this.movie = movie;
        this.commentForm.reset();
        this.saving = false;
      },
      error: err => {
        if (!this.auth.token) return;
        this.errorMessage = err?.error?.message ?? err?.message ?? 'Could not save comment';
        this.saving = false;
      }
    });
  }

  toggleRecommendation(): void {
    if (!this.movie || !this.auth.token || this.recommendationSaving) return;

    this.recommendationSaving = true;
    const request = this.movie.recommended
      ? this.moviesApi.unrecommendMovie(this.movie.imdbId)
      : this.moviesApi.recommendMovie(this.movie.imdbId);

    request.subscribe({
      next: movie => {
        if (!this.auth.token) return;
        this.movie = movie;
        this.recommendationSaving = false;
        this.loadRankHistory(movie.imdbId);
      },
      error: err => {
        if (!this.auth.token) return;
        this.errorMessage = err?.error?.message ?? err?.message ?? 'Could not update recommendation';
        this.recommendationSaving = false;
      }
    });
  }

  replayMovie(): void {
    if (!this.movie || !this.auth.token || this.replaySaving || this.recommendationSaving) return;

    this.replaySaving = true;
    this.errorMessage = '';
    this.moviesApi.replayMovie(this.movie.imdbId).subscribe({
      next: movie => {
        if (!this.auth.token) return;
        this.movie = movie;
        this.replaySaving = false;
        this.loadRankHistory(movie.imdbId);
      },
      error: err => {
        if (!this.auth.token) return;
        this.errorMessage = err?.error?.message ?? err?.message ?? 'Could not replay movie';
        this.replaySaving = false;
      }
    });
  }

  poster(movie: Movie | MovieRankHistoryMovie): string {
    return movie.poster && movie.poster !== 'N/A' ? movie.poster : '/images/movie-poster.jpg';
  }

  rankLabel(movie: MovieRankHistoryMovie): string {
    if (movie.rankPosition == null || movie.rating == null) {
      return '-';
    }
    return `#${movie.rankPosition}(${movie.rating.toFixed(2)})`;
  }

  avatar(seed: string): string {
    return `https://api.dicebear.com/6.x/avataaars/svg?seed=${encodeURIComponent(seed || 'user')}`;
  }

  private clearMovie(): void {
    this.movie = null;
    this.rankHistory = null;
    this.loading = false;
    this.rankHistoryLoading = false;
    this.saving = false;
    this.recommendationSaving = false;
    this.replaySaving = false;
    this.errorMessage = '';
    this.rankHistoryErrorMessage = '';
    this.commentForm.reset();
  }
}
