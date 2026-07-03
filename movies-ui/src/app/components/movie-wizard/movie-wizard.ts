import { CommonModule } from '@angular/common';
import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { debounceTime, distinctUntilChanged, map, Subscription } from 'rxjs';
import { AuthService } from '../../services/auth';
import {
  MovieType,
  MoviesApiService,
  OmdbMovieSearchCriteria,
  OmdbMovieSearchResult,
  OmdbSearchType
} from '../../services/movies-api';

@Component({
  standalone: true,
  selector: 'app-movie-wizard',
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './movie-wizard.html',
  styleUrl: './movie-wizard.css'
})
export class MovieWizardComponent implements OnInit, OnDestroy {
  private readonly fb = inject(FormBuilder);
  private readonly moviesApi = inject(MoviesApiService);
  private readonly router = inject(Router);
  readonly auth = inject(AuthService);
  private authSub?: Subscription;
  private searchValueSub?: Subscription;
  private searchSub?: Subscription;

  readonly searchTypes: { value: OmdbSearchType; label: string }[] = [
    { value: 'movie', label: 'Movie' },
    { value: 'series', label: 'Series' },
    { value: 'episode', label: 'Episode' }
  ];
  readonly movieTypes: { value: MovieType; label: string }[] = [
    { value: 'MOVIE', label: 'Movie' },
    { value: 'SERIES', label: 'Series' },
    { value: 'EPISODE', label: 'Episode' }
  ];
  step = 1;
  loading = false;
  saving = false;
  errorMessage = '';
  searchResults: OmdbMovieSearchResult[] = [];
  selectedMovie: OmdbMovieSearchResult | null = null;
  currentSearchPage = 1;
  hasNextSearchPage = false;
  private lastSearchKey = '';

  readonly searchForm = this.fb.group({
    title: ['', Validators.required],
    year: [''],
    type: ['movie' as OmdbSearchType],
    exactTitleMatch: [false]
  });

  readonly movieForm = this.fb.group({
    imdbId: ['', Validators.required],
    title: ['', Validators.required],
    director: ['', Validators.required],
    writer: ['', Validators.required],
    year: ['', Validators.required],
    poster: [''],
    genre: [''],
    country: [''],
    type: ['MOVIE' as MovieType, Validators.required]
  });

  ngOnInit(): void {
    this.authSub = this.auth.isAuthenticated$.subscribe(authenticated => {
      if (!authenticated) {
        this.clearWizard();
      }
    });

    this.searchValueSub = this.searchForm.valueChanges.pipe(
      debounceTime(350),
      map(() => this.currentCriteria()),
      distinctUntilChanged((previous, current) => this.criteriaKey(previous) === this.criteriaKey(current))
    ).subscribe(criteria => {
      if (!this.auth.token) return;
      if (criteria.title.length >= 4) {
        this.runSearch(criteria, 1, false);
      } else {
        this.clearSearchResults();
      }
    });
  }

  ngOnDestroy(): void {
    this.authSub?.unsubscribe();
    this.searchValueSub?.unsubscribe();
    this.searchSub?.unsubscribe();
  }

  search(): void {
    if (!this.auth.token) return;
    if (this.searchForm.invalid) {
      this.searchForm.markAllAsTouched();
      return;
    }
    this.runSearch(this.currentCriteria(), 1, true);
  }

  nextSearchPage(): void {
    if (!this.auth.token || !this.hasNextSearchPage || this.loading) return;
    this.runSearch(this.currentCriteria(), this.currentSearchPage + 1, true);
  }

  private runSearch(criteria: OmdbMovieSearchCriteria, page: number, explicit: boolean): void {
    if (!criteria.title || (!explicit && criteria.title.length < 4)) return;

    this.loading = true;
    this.errorMessage = '';
    this.searchResults = [];
    this.selectedMovie = null;
    this.movieForm.reset();
    this.currentSearchPage = page;
    this.lastSearchKey = this.criteriaKey(criteria);
    this.searchSub?.unsubscribe();
    this.searchSub = this.moviesApi.searchOmdbMovies(criteria, page).subscribe({
      next: result => {
        if (!this.auth.token) return;
        if (this.criteriaKey(criteria) !== this.lastSearchKey) return;
        this.searchResults = result.movies;
        this.hasNextSearchPage = result.hasNext;
        if (this.searchResults.length === 0) {
          this.errorMessage = 'No movie found';
        }
        this.loading = false;
      },
      error: err => {
        if (!this.auth.token) return;
        if (this.criteriaKey(criteria) !== this.lastSearchKey) return;
        this.errorMessage = err?.message ?? 'OMDb search failed';
        this.hasNextSearchPage = false;
        this.loading = false;
      }
    });
  }

  selectMovie(movie: OmdbMovieSearchResult): void {
    this.selectedMovie = this.selectedMovie?.imdbId === movie.imdbId ? null : movie;
    if (this.selectedMovie) {
      this.movieForm.patchValue({
        imdbId: movie.imdbId,
        title: movie.englishTitle || movie.originalTitle,
        director: movie.directors || 'N/A',
        writer: movie.writers || 'N/A',
        year: movie.year,
        poster: movie.poster,
        genre: movie.genre,
        country: movie.country,
        type: movie.type
      });
    } else {
      this.movieForm.reset({ type: 'MOVIE' });
    }
  }

  next(): void {
    if (this.step === 2 && this.movieForm.invalid) {
      this.movieForm.markAllAsTouched();
      return;
    }
    this.step = Math.min(3, this.step + 1);
  }

  back(): void {
    this.step = Math.max(1, this.step - 1);
  }

  createMovie(): void {
    if (!this.auth.token) return;
    if (this.movieForm.invalid) {
      this.movieForm.markAllAsTouched();
      return;
    }
    this.saving = true;
    this.errorMessage = '';
    const value = this.movieForm.getRawValue();
    const movie = {
      imdbId: value.imdbId ?? '',
      title: value.title ?? '',
      director: value.director ?? '',
      writer: value.writer ?? '',
      year: value.year ?? '',
      poster: value.poster ?? '',
      genre: value.genre ?? '',
      country: value.country ?? '',
      type: value.type ?? 'MOVIE'
    };
    this.moviesApi.createMovie(movie).subscribe({
      next: () => {
        if (!this.auth.token) return;
        this.router.navigateByUrl('/home');
      },
      error: err => {
        if (!this.auth.token) return;
        this.errorMessage = err?.error?.message ?? err?.message ?? 'Could not create movie';
        this.saving = false;
      }
    });
  }

  poster(): string {
    const poster = this.movieForm.getRawValue().poster;
    return poster && poster !== 'N/A' ? poster : '/images/movie-poster.jpg';
  }

  private clearWizard(): void {
    this.step = 1;
    this.loading = false;
    this.saving = false;
    this.errorMessage = '';
    this.searchResults = [];
    this.selectedMovie = null;
    this.currentSearchPage = 1;
    this.hasNextSearchPage = false;
    this.lastSearchKey = '';
    this.searchSub?.unsubscribe();
    this.searchForm.reset({
      title: '',
      year: '',
      type: 'movie',
      exactTitleMatch: false
    });
    this.movieForm.reset({ type: 'MOVIE' });
  }

  private clearSearchResults(): void {
    this.searchSub?.unsubscribe();
    this.searchResults = [];
    this.selectedMovie = null;
    this.movieForm.reset({ type: 'MOVIE' });
    this.errorMessage = '';
    this.currentSearchPage = 1;
    this.hasNextSearchPage = false;
    this.lastSearchKey = '';
  }

  private currentCriteria(): OmdbMovieSearchCriteria {
    const value = this.searchForm.getRawValue();
    return {
      title: (value.title ?? '').trim(),
      year: (value.year ?? '').trim(),
      type: (value.type ?? 'movie') as OmdbSearchType,
      exactTitleMatch: value.exactTitleMatch ?? false
    };
  }

  private criteriaKey(criteria: OmdbMovieSearchCriteria): string {
    return JSON.stringify(criteria);
  }
}
