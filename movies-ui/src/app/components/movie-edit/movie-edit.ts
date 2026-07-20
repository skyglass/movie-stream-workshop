import { CommonModule } from '@angular/common';
import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { Subscription } from 'rxjs';
import { AuthService } from '../../services/auth';
import { Movie, MovieType, MoviesApiService, OmdbMovieSearchResult } from '../../services/movies-api';

@Component({
  standalone: true,
  selector: 'app-movie-edit',
  imports: [CommonModule, ReactiveFormsModule, RouterLink],
  templateUrl: './movie-edit.html',
  styleUrl: './movie-edit.css'
})
export class MovieEditComponent implements OnInit, OnDestroy {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly fb = inject(FormBuilder);
  private readonly moviesApi = inject(MoviesApiService);
  readonly auth = inject(AuthService);
  private movieSub?: Subscription;
  private omdbSub?: Subscription;
  private saveSub?: Subscription;
  imdbId = '';

  readonly movieTypes: { value: MovieType; label: string }[] = [
    { value: 'MOVIE', label: 'Movie' },
    { value: 'SERIES', label: 'Series' }
  ];

  loading = false;
  refreshing = false;
  saving = false;
  deleting = false;
  errorMessage = '';
  private deleteSub?: Subscription;

  readonly movieForm = this.fb.group({
    imdbId: ['', Validators.required],
    title: ['', Validators.required],
    director: ['', Validators.required],
    writer: ['', Validators.required],
    year: ['', Validators.required],
    type: ['MOVIE' as MovieType, Validators.required],
    poster: [''],
    genre: [''],
    country: ['']
  });

  ngOnInit(): void {
    this.imdbId = this.route.snapshot.paramMap.get('imdbId') ?? '';
    if (!this.imdbId) {
      this.errorMessage = 'Movie id is missing';
      return;
    }
    this.loadMovie();
  }

  ngOnDestroy(): void {
    this.movieSub?.unsubscribe();
    this.omdbSub?.unsubscribe();
    this.saveSub?.unsubscribe();
    this.deleteSub?.unsubscribe();
  }

  updateFromOmdb(): void {
    if (!this.imdbId || this.refreshing) return;

    this.refreshing = true;
    this.errorMessage = '';
    this.omdbSub?.unsubscribe();
    this.omdbSub = this.moviesApi.getOmdbMovieById(this.imdbId).subscribe({
      next: movie => {
        this.patchFromOmdb(movie);
        this.refreshing = false;
      },
      error: err => {
        this.errorMessage = err?.message ?? 'Could not update movie from OMDb';
        this.refreshing = false;
      }
    });
  }

  submit(): void {
    if (this.movieForm.invalid) {
      this.movieForm.markAllAsTouched();
      return;
    }

    this.saving = true;
    this.errorMessage = '';
    this.saveSub?.unsubscribe();
    this.saveSub = this.moviesApi.updateMovie(this.imdbId, this.moviePayload()).subscribe({
      next: movie => {
        this.router.navigateByUrl(`/movies/${movie.imdbId}`);
      },
      error: err => {
        this.errorMessage = err?.error?.message ?? err?.message ?? 'Could not update movie';
        this.saving = false;
      }
    });
  }

  // Admin-only (gated in the template via auth.isAdmin, and enforced server-side regardless): permanently
  // removes this movie from the catalog. Every table that references it (challenges/votes/ranks, recommendations,
  // comments, category assignments, journey/watchlist entries) cascade-deletes at the database level, so this is
  // irreversible and affects every user who has that movie in a challenge, favorites, a journey, etc. -- not just
  // the admin performing the delete.
  deleteMovie(): void {
    if (this.deleting || this.saving) return;
    const title = this.movieForm.getRawValue().title || this.imdbId;
    if (!confirm(`Delete "${title}" from the catalog? This also removes it from every user's favorites, `
      + `challenges, recommendations, journeys, watchlists, and comments. This cannot be undone.`)) {
      return;
    }
    this.deleting = true;
    this.errorMessage = '';
    this.deleteSub?.unsubscribe();
    this.deleteSub = this.moviesApi.deleteMovie(this.imdbId).subscribe({
      next: () => {
        this.router.navigateByUrl('/home');
      },
      error: err => {
        this.errorMessage = err?.error?.message ?? err?.message ?? 'Could not delete movie';
        this.deleting = false;
      }
    });
  }

  poster(): string {
    const poster = this.movieForm.getRawValue().poster;
    return poster && poster !== 'N/A' ? poster : '/images/movie-poster.jpg';
  }

  private loadMovie(): void {
    this.loading = true;
    this.errorMessage = '';
    this.movieSub?.unsubscribe();
    this.movieSub = this.moviesApi.getMovie(this.imdbId).subscribe({
      next: movie => {
        this.patchFromMovie(movie);
        this.loading = false;
      },
      error: err => {
        this.errorMessage = err?.error?.message ?? err?.message ?? 'Could not load movie';
        this.loading = false;
      }
    });
  }

  private patchFromMovie(movie: Movie): void {
    this.movieForm.patchValue({
      imdbId: movie.imdbId,
      title: movie.title,
      director: movie.director,
      writer: movie.writer,
      year: movie.year,
      type: movie.type,
      poster: movie.poster,
      genre: movie.genre,
      country: movie.country
    });
  }

  private patchFromOmdb(movie: OmdbMovieSearchResult): void {
    this.movieForm.patchValue({
      imdbId: movie.imdbId,
      title: movie.englishTitle || movie.originalTitle || 'N/A',
      director: movie.directors || 'N/A',
      writer: movie.writers || 'N/A',
      year: movie.year || 'N/A',
      type: movie.type,
      poster: movie.poster,
      genre: movie.genre,
      country: movie.country
    });
  }

  private moviePayload(): Partial<Movie> {
    const value = this.movieForm.getRawValue();
    return {
      title: value.title ?? '',
      director: value.director ?? '',
      writer: value.writer ?? '',
      year: value.year ?? '',
      type: value.type ?? 'MOVIE',
      poster: value.poster ?? '',
      genre: value.genre ?? '',
      country: value.country ?? ''
    };
  }
}
