import { CommonModule } from '@angular/common';
import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Subscription } from 'rxjs';
import { AuthService } from '../../services/auth';
import {
  MovieChallengeSelection,
  MoviesApiService,
  OmdbMovieSearchCriteria,
  OmdbMovieSearchResult,
  OmdbSearchType,
  RecommendMovieFromSearchRequest,
  SuggestedMovieChallenge,
  SuggestedMovieChallengeMovie
} from '../../services/movies-api';
import { MoviePageNavigatorComponent } from '../movie-page-navigator/movie-page-navigator';

@Component({
  standalone: true,
  selector: 'app-movie-search',
  imports: [CommonModule, ReactiveFormsModule, MoviePageNavigatorComponent],
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
  private authSub?: Subscription;
  private suggestedSub?: Subscription;
  private suggestedSubmitSub?: Subscription;

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
  suggestedChallenges: SuggestedMovieChallenge[] = [];
  suggestedLoading = false;
  suggestedSaving = false;
  suggestedErrorMessage = '';
  suggestedCurrentPage = 1;
  suggestedTotalCount = 0;
  selectedSuggestedMovieIds: Record<string, string> = {};
  visibleProbabilityHelpKey = '';
  readonly suggestedPageSize = this.moviesApi.moviePageSize;
  readonly probabilityHelpText = 'Chance of winning, based on previous comparisons';
  private lastSearchKey = '';

  ngOnInit(): void {
    this.valueSub = this.searchForm.valueChanges.subscribe(() => this.clearResults());
    this.authSub = this.auth.isAuthenticated$.subscribe(authenticated => {
      if (authenticated) {
        this.loadSuggestedChallenges(1);
      } else {
        this.resetSuggestedChallenges();
      }
    });
  }

  ngOnDestroy(): void {
    this.valueSub?.unsubscribe();
    this.searchSub?.unsubscribe();
    this.selectionSub?.unsubscribe();
    this.recommendSub?.unsubscribe();
    this.authSub?.unsubscribe();
    this.suggestedSub?.unsubscribe();
    this.suggestedSubmitSub?.unsubscribe();
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
        this.loadSuggestedChallenges(1);
      },
      error: err => {
        this.recommending = false;
        this.errorMessage = err?.error?.message ?? err?.message ?? 'Could not recommend movie';
      }
    });
  }

  poster(movie: { poster: string }): string {
    return movie.poster && movie.poster !== 'N/A' ? movie.poster : '/images/movie-poster.jpg';
  }

  searchDisabled(): boolean {
    return this.searchForm.invalid || this.loading;
  }

  loadSuggestedChallenges(page = this.suggestedCurrentPage): void {
    if (!this.auth.token) return;

    this.suggestedSub?.unsubscribe();
    this.suggestedLoading = true;
    this.suggestedErrorMessage = '';
    this.visibleProbabilityHelpKey = '';
    this.suggestedSub = this.moviesApi.listSuggestedMovieChallenges(page, this.suggestedPageSize).subscribe({
      next: challengePage => {
        this.suggestedChallenges = challengePage.challenges;
        this.suggestedTotalCount = challengePage.totalCount;
        this.suggestedCurrentPage = page;
        this.selectedSuggestedMovieIds = {};
        this.suggestedLoading = false;
      },
      error: err => {
        this.suggestedChallenges = [];
        this.suggestedTotalCount = 0;
        this.suggestedErrorMessage = err?.error?.message ?? err?.message ?? 'Could not load suggested challenges';
        this.selectedSuggestedMovieIds = {};
        this.suggestedLoading = false;
      }
    });
  }

  selectSuggestedMovie(challenge: SuggestedMovieChallenge, movie: SuggestedMovieChallengeMovie): void {
    if (this.suggestedSaving) return;

    const key = this.challengeKey(challenge);
    if (this.selectedSuggestedMovieIds[key] === movie.imdbId) {
      delete this.selectedSuggestedMovieIds[key];
    } else {
      this.selectedSuggestedMovieIds[key] = movie.imdbId;
    }
    this.suggestedErrorMessage = '';
  }

  isSuggestedMovieSelected(challenge: SuggestedMovieChallenge, movie: SuggestedMovieChallengeMovie): boolean {
    return this.selectedSuggestedMovieIds[this.challengeKey(challenge)] === movie.imdbId;
  }

  isSuggestedMovieLoser(challenge: SuggestedMovieChallenge, movie: SuggestedMovieChallengeMovie): boolean {
    const selectedMovieId = this.selectedSuggestedMovieIds[this.challengeKey(challenge)];
    return !!selectedMovieId && selectedMovieId !== movie.imdbId;
  }

  hasSuggestedSelections(): boolean {
    return Object.keys(this.selectedSuggestedMovieIds).length > 0;
  }

  selectedSuggestedCount(): number {
    return Object.keys(this.selectedSuggestedMovieIds).length;
  }

  submitSuggestedSelections(): void {
    if (!this.hasSuggestedSelections() || this.suggestedSaving) return;

    this.suggestedSaving = true;
    this.suggestedErrorMessage = '';
    this.suggestedSubmitSub?.unsubscribe();
    this.suggestedSubmitSub = this.moviesApi.submitMovieChallengeSelections(this.selectedSuggestedRequests()).subscribe({
      next: () => {
        this.suggestedSaving = false;
        this.selectedSuggestedMovieIds = {};
        this.loadSuggestedChallenges(this.suggestedCurrentPage);
      },
      error: err => {
        this.suggestedSaving = false;
        this.suggestedErrorMessage = err?.error?.message ?? err?.message ?? 'Could not submit suggested challenges';
      }
    });
  }

  discardSuggestedSelections(): void {
    if (this.suggestedSaving) return;

    this.selectedSuggestedMovieIds = {};
    this.loadSuggestedChallenges(this.suggestedCurrentPage);
  }

  toggleProbabilityHelp(challenge: SuggestedMovieChallenge, movie: SuggestedMovieChallengeMovie): void {
    const key = `${this.challengeKey(challenge)}:${movie.imdbId}`;
    this.visibleProbabilityHelpKey = this.visibleProbabilityHelpKey === key ? '' : key;
  }

  probabilityHelpVisible(challenge: SuggestedMovieChallenge, movie: SuggestedMovieChallengeMovie): boolean {
    return this.visibleProbabilityHelpKey === `${this.challengeKey(challenge)}:${movie.imdbId}`;
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

  private selectedSuggestedRequests(): MovieChallengeSelection[] {
    return this.suggestedChallenges
      .map(challenge => ({
        movie1Id: challenge.movie1.imdbId,
        movie2Id: challenge.movie2.imdbId,
        selectedMovieId: this.selectedSuggestedMovieIds[this.challengeKey(challenge)]
      }))
      .filter(selection => !!selection.selectedMovieId);
  }

  private challengeKey(challenge: SuggestedMovieChallenge): string {
    return `${challenge.movie1.imdbId}:${challenge.movie2.imdbId}`;
  }

  private resetSuggestedChallenges(): void {
    this.suggestedSub?.unsubscribe();
    this.suggestedSubmitSub?.unsubscribe();
    this.suggestedChallenges = [];
    this.suggestedLoading = false;
    this.suggestedSaving = false;
    this.suggestedErrorMessage = '';
    this.suggestedCurrentPage = 1;
    this.suggestedTotalCount = 0;
    this.selectedSuggestedMovieIds = {};
    this.visibleProbabilityHelpKey = '';
  }
}
