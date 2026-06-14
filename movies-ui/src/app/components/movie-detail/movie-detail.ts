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
  private imdbId = '';

  movie: Movie | null = null;
  loading = false;
  saving = false;
  recommendationSaving = false;
  errorMessage = '';

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
  }

  loadMovie(imdbId: string): void {
    this.loading = true;
    this.errorMessage = '';
    this.moviesApi.getMovie(imdbId).subscribe({
      next: movie => {
        if (!this.auth.token) return;
        this.movie = movie;
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

  avatar(seed: string): string {
    return `https://api.dicebear.com/6.x/avataaars/svg?seed=${encodeURIComponent(seed || 'user')}`;
  }

  private clearMovie(): void {
    this.movie = null;
    this.loading = false;
    this.saving = false;
    this.recommendationSaving = false;
    this.errorMessage = '';
    this.commentForm.reset();
  }
}
