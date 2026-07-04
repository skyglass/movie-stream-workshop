import { CommonModule } from '@angular/common';
import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Subscription } from 'rxjs';
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
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './movie-search.html',
  styleUrl: './movie-search.css'
})
export class MovieSearchComponent implements OnInit, OnDestroy {
  private readonly fb = inject(FormBuilder);
  private readonly moviesApi = inject(MoviesApiService);
  readonly auth = inject(AuthService);
  private valueSub?: Subscription;
  private searchSub?: Subscription;
  private selectionSub?: Subscription;
  private recommendSub?: Subscription;

  readonly searchTypes: { value: OmdbSearchType; label: string }[] = [
    { value: 'movie', label: 'Movie' },
    { value: 'series', label: 'Series' }
  ];
  readonly searchForm = this.fb.group({
    title: ['', Validators.required],
    year: [''],
    type: ['movie' as OmdbSearchType],
    exactTitleMatch: [false]
  });

  movies: OmdbMovieSearchResult[] = [];
  selectedMovie: OmdbMovieSearchResult | null = null;
  recommendationSaved = false;
  step = 1;
  loading = false;
  selectingMovie = false;
  recommending = false;
  errorMessage = '';
  successMessage = '';
  currentPage = 1;
  hasNext = false;
  private lastSearchKey = '';

  ngOnInit(): void {
    this.valueSub = this.searchForm.valueChanges.subscribe(() => this.clearResults());
  }

  ngOnDestroy(): void {
    this.valueSub?.unsubscribe();
    this.searchSub?.unsubscribe();
    this.selectionSub?.unsubscribe();
    this.recommendSub?.unsubscribe();
  }

  search(): void {
    if (this.searchForm.invalid) {
      this.searchForm.markAllAsTouched();
      return;
    }
    const criteria = this.currentCriteria();
    if (!this.readyForSearch(criteria)) return;
    this.runSearch(criteria, 1);
  }

  nextSearchPage(): void {
    if (!this.hasNext || this.loading) return;
    this.runSearch(this.currentCriteria(), this.currentPage + 1);
  }

  previousSearchPage(): void {
    if (this.currentPage <= 1 || this.loading) return;
    this.runSearch(this.currentCriteria(), this.currentPage - 1);
  }

  selectMovie(movie: OmdbMovieSearchResult): void {
    if (this.selectingMovie) return;

    this.recommendationSaved = false;
    this.successMessage = '';
    this.errorMessage = '';
    if (movie.detailsLoaded) {
      this.selectedMovie = movie;
      this.step = 2;
      return;
    }

    this.selectedMovie = null;
    this.selectingMovie = true;
    this.selectionSub?.unsubscribe();
    this.selectionSub = this.moviesApi.getOmdbMovieById(movie.imdbId).subscribe({
      next: selectedMovie => {
        this.selectedMovie = selectedMovie;
        this.selectingMovie = false;
        this.step = 2;
      },
      error: err => {
        this.selectingMovie = false;
        this.errorMessage = err?.message ?? 'Could not load movie details';
      }
    });
  }

  back(): void {
    this.recommendSub?.unsubscribe();
    this.recommending = false;
    this.recommendationSaved = false;
    this.errorMessage = '';
    this.successMessage = '';
    this.selectingMovie = false;
    this.step = Math.max(1, this.step - 1);
  }

  recommendSelectedMovie(): void {
    if (!this.selectedMovie || this.recommending) return;
    if (!this.auth.token) {
      this.errorMessage = 'Please log in or register to recommend movies.';
      return;
    }

    this.recommending = true;
    this.errorMessage = '';
    this.successMessage = '';
    this.recommendSub?.unsubscribe();
    this.recommendSub = this.moviesApi.recommendMovieFromSearch(this.toRecommendationRequest(this.selectedMovie)).subscribe({
      next: () => {
        const title = this.selectedMovie?.englishTitle || this.selectedMovie?.originalTitle || 'movie';
        this.recommending = false;
        this.recommendationSaved = true;
        this.resetSearchWizard(`Movie "${title}" is recommended!`);
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

  searchDisabled(): boolean {
    return this.searchForm.invalid || this.loading;
  }

  private runSearch(criteria: OmdbMovieSearchCriteria, page: number): void {
    if (!this.readyForSearch(criteria)) return;

    this.loading = true;
    this.selectingMovie = false;
    this.errorMessage = '';
    this.successMessage = '';
    this.selectedMovie = null;
    this.recommendationSaved = false;
    this.step = 1;
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
    this.selectionSub?.unsubscribe();
    this.movies = [];
    this.selectedMovie = null;
    this.recommendationSaved = false;
    this.step = 1;
    this.loading = false;
    this.selectingMovie = false;
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

  private readyForSearch(criteria: OmdbMovieSearchCriteria): boolean {
    return !!criteria.title;
  }

  private resetSearchWizard(successMessage = ''): void {
    this.searchSub?.unsubscribe();
    this.movies = [];
    this.selectedMovie = null;
    this.recommendationSaved = false;
    this.step = 1;
    this.loading = false;
    this.selectingMovie = false;
    this.recommending = false;
    this.errorMessage = '';
    this.successMessage = successMessage;
    this.currentPage = 1;
    this.hasNext = false;
    this.lastSearchKey = '';
    this.searchForm.reset({
      title: '',
      year: '',
      type: 'movie',
      exactTitleMatch: false
    }, { emitEvent: false });
  }
}
