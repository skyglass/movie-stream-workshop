import { CommonModule } from '@angular/common';
import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { debounceTime, distinctUntilChanged, map, Subscription } from 'rxjs';
import { AuthService } from '../../services/auth';
import {
  MoviesApiService,
  OmdbMovieSearchCriteria,
  OmdbMovieSearchResult,
  OmdbSearchType,
  RecommendMovieFromSearchRequest
} from '../../services/movies-api';

@Component({
  standalone: true,
  selector: 'app-movie-search',
  imports: [CommonModule, ReactiveFormsModule, RouterLink],
  templateUrl: './movie-search.html',
  styleUrl: './movie-search.css'
})
export class MovieSearchComponent implements OnInit, OnDestroy {
  private readonly fb = inject(FormBuilder);
  private readonly moviesApi = inject(MoviesApiService);
  readonly auth = inject(AuthService);
  private valueSub?: Subscription;
  private searchSub?: Subscription;
  private recommendSub?: Subscription;

  readonly searchTypes: { value: OmdbSearchType; label: string }[] = [
    { value: 'movie', label: 'Movie' },
    { value: 'series', label: 'Series' },
    { value: 'episode', label: 'Episode' }
  ];
  readonly searchForm = this.fb.group({
    title: ['', Validators.required],
    year: [''],
    type: ['movie' as OmdbSearchType],
    exactTitleMatch: [false]
  });

  movies: OmdbMovieSearchResult[] = [];
  selectedMovie: OmdbMovieSearchResult | null = null;
  loading = false;
  recommending = false;
  errorMessage = '';
  successMessage = '';
  currentPage = 1;
  hasNext = false;
  private lastSearchKey = '';

  ngOnInit(): void {
    this.valueSub = this.searchForm.valueChanges.pipe(
      debounceTime(350),
      map(() => this.currentCriteria()),
      distinctUntilChanged((previous, current) => this.criteriaKey(previous) === this.criteriaKey(current))
    ).subscribe(criteria => {
      if (criteria.title.length >= 4) {
        this.runSearch(criteria, 1, false);
      } else {
        this.clearResults();
      }
    });
  }

  ngOnDestroy(): void {
    this.valueSub?.unsubscribe();
    this.searchSub?.unsubscribe();
    this.recommendSub?.unsubscribe();
  }

  search(): void {
    if (this.searchForm.invalid) {
      this.searchForm.markAllAsTouched();
      return;
    }
    this.runSearch(this.currentCriteria(), 1, true);
  }

  next(): void {
    if (!this.hasNext || this.loading) return;
    this.runSearch(this.currentCriteria(), this.currentPage + 1, true);
  }

  selectMovie(movie: OmdbMovieSearchResult): void {
    this.selectedMovie = this.selectedMovie?.imdbId === movie.imdbId ? null : movie;
    this.successMessage = '';
    this.errorMessage = '';
  }

  recommendSelectedMovie(): void {
    if (!this.selectedMovie || this.recommending) return;
    if (!this.auth.token) {
      this.errorMessage = 'Sign in to recommend this movie.';
      return;
    }

    this.recommending = true;
    this.errorMessage = '';
    this.successMessage = '';
    this.recommendSub?.unsubscribe();
    this.recommendSub = this.moviesApi.recommendMovieFromSearch(this.toRecommendationRequest(this.selectedMovie)).subscribe({
      next: () => {
        this.recommending = false;
        this.successMessage = 'Your recommendation is saved.';
      },
      error: err => {
        this.recommending = false;
        this.errorMessage = err?.error?.message ?? err?.message ?? 'Could not recommend movie';
      }
    });
  }

  poster(movie: OmdbMovieSearchResult): string {
    return movie.poster && movie.poster !== 'N/A' ? movie.poster : '/images/movie-poster.jpg';
  }

  movieChallengeRegistrationUrl(): string {
    return this.auth.getRegistrationUrl(new URL('/movie-challenge', window.location.href).toString());
  }

  private runSearch(criteria: OmdbMovieSearchCriteria, page: number, explicit: boolean): void {
    if (!criteria.title || (!explicit && criteria.title.length < 4)) return;

    this.loading = true;
    this.errorMessage = '';
    this.successMessage = '';
    this.selectedMovie = null;
    this.currentPage = page;
    this.lastSearchKey = this.criteriaKey(criteria);
    this.searchSub?.unsubscribe();
    this.searchSub = this.moviesApi.searchOmdbMovies(criteria, page).subscribe({
      next: result => {
        if (this.criteriaKey(criteria) !== this.lastSearchKey) return;
        this.movies = result.movies;
        this.hasNext = result.hasNext;
        this.loading = false;
        if (this.movies.length === 0) {
          this.errorMessage = 'No movies found';
        }
      },
      error: err => {
        if (this.criteriaKey(criteria) !== this.lastSearchKey) return;
        this.movies = [];
        this.hasNext = false;
        this.loading = false;
        this.errorMessage = err?.message ?? 'Movie search failed';
      }
    });
  }

  private clearResults(): void {
    this.searchSub?.unsubscribe();
    this.movies = [];
    this.selectedMovie = null;
    this.loading = false;
    this.errorMessage = '';
    this.successMessage = '';
    this.currentPage = 1;
    this.hasNext = false;
    this.lastSearchKey = '';
  }

  private toRecommendationRequest(movie: OmdbMovieSearchResult): RecommendMovieFromSearchRequest {
    return {
      imdbId: movie.imdbId,
      title: movie.englishTitle || movie.originalTitle || 'N/A',
      originalTitle: movie.originalTitle,
      director: movie.directors || 'N/A',
      writer: movie.writers || 'N/A',
      year: movie.year || 'N/A',
      country: movie.country,
      genre: movie.genre,
      poster: movie.poster,
      type: movie.type
    };
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
