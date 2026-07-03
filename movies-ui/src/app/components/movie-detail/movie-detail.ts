import { CommonModule } from '@angular/common';
import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { Subscription } from 'rxjs';
import { AuthService } from '../../services/auth';
import { Movie, MoviesApiService } from '../../services/movies-api';

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
  private authSub?: Subscription;
  private plotSub?: Subscription;
  private fullPlotSub?: Subscription;
  private imdbId = '';

  movie: Movie | null = null;
  loading = false;
  saving = false;
  recommendationSaving = false;
  errorMessage = '';
  shortPlot = '';
  fullPlot = '';
  plotLoading = false;
  fullPlotLoading = false;
  plotErrorMessage = '';

  readonly commentForm = this.fb.group({
    text: ['', [Validators.required, Validators.maxLength(4000)]]
  });

  ngOnInit(): void {
    this.imdbId = this.route.snapshot.paramMap.get('imdbId') ?? '';
    this.authSub = this.auth.isAuthenticated$.subscribe(authenticated => {
      if (!authenticated) {
        this.clearMovie();
      } else if (this.imdbId && !this.movie && !this.loading) {
        this.loadMovie(this.imdbId);
      }
    });
  }

  ngOnDestroy(): void {
    this.authSub?.unsubscribe();
    this.plotSub?.unsubscribe();
    this.fullPlotSub?.unsubscribe();
  }

  loadMovie(imdbId: string): void {
    this.loading = true;
    this.errorMessage = '';
    this.moviesApi.getMovie(imdbId).subscribe({
      next: movie => {
        if (!this.auth.token) return;
        this.movie = movie;
        this.clearPlot();
        this.loading = false;
      },
      error: err => {
        if (!this.auth.token) return;
        this.errorMessage = err?.error?.message ?? err?.message ?? 'Could not load movie';
        this.loading = false;
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
      },
      error: err => {
        if (!this.auth.token) return;
        this.errorMessage = err?.error?.message ?? err?.message ?? 'Could not update recommendation';
        this.recommendationSaving = false;
      }
    });
  }

  poster(movie: Movie): string {
    return movie.poster && movie.poster !== 'N/A' ? movie.poster : '/images/movie-poster.jpg';
  }

  showShortPlot(): void {
    if (!this.movie || this.plotLoading) return;

    this.plotLoading = true;
    this.plotErrorMessage = '';
    this.plotSub?.unsubscribe();
    this.plotSub = this.moviesApi.getOmdbPlot(this.movie.imdbId, 'short').subscribe({
      next: plot => {
        this.shortPlot = plot || 'Plot is not available.';
        this.plotLoading = false;
      },
      error: err => {
        this.plotErrorMessage = err?.message ?? 'Could not load plot';
        this.plotLoading = false;
      }
    });
  }

  showFullPlot(): void {
    if (!this.movie || this.fullPlotLoading) return;

    this.fullPlotLoading = true;
    this.plotErrorMessage = '';
    this.fullPlotSub?.unsubscribe();
    this.fullPlotSub = this.moviesApi.getOmdbPlot(this.movie.imdbId, 'full').subscribe({
      next: plot => {
        this.fullPlot = plot || 'Full plot is not available.';
        this.fullPlotLoading = false;
      },
      error: err => {
        this.plotErrorMessage = err?.message ?? 'Could not load full plot';
        this.fullPlotLoading = false;
      }
    });
  }

  avatar(seed: string): string {
    return `https://api.dicebear.com/6.x/avataaars/svg?seed=${encodeURIComponent(seed || 'user')}`;
  }

  private clearMovie(): void {
    this.movie = null;
    this.loading = false;
    this.saving = false;
    this.recommendationSaving = false;
    this.errorMessage = '';
    this.clearPlot();
    this.commentForm.reset();
  }

  private clearPlot(): void {
    this.plotSub?.unsubscribe();
    this.fullPlotSub?.unsubscribe();
    this.shortPlot = '';
    this.fullPlot = '';
    this.plotLoading = false;
    this.fullPlotLoading = false;
    this.plotErrorMessage = '';
  }
}
