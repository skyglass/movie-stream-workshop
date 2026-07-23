import { CommonModule } from '@angular/common';
import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Subscription } from 'rxjs';
import { AuthService } from '../../services/auth';
import {
  MovieChallenge,
  MovieChallengeMovie,
  MovieChallengeSelection,
  MoviesApiService,
  OmdbMovieSearchCriteria,
  OmdbMovieSearchResult,
  OmdbSearchType,
  RecommendMovieFromSearchRequest,
  SuggestedMovieChallenge,
  SuggestedMovieChallengeMovie
} from '../../services/movies-api';

@Component({
  standalone: true,
  selector: 'app-movie-challenge-page',
  imports: [CommonModule, ReactiveFormsModule, RouterLink],
  templateUrl: './movie-challenge-page.html',
  styleUrls: ['./movie-challenge-page.css', './movie-challenge-extra.css']
})
export class MovieChallengePageComponent implements OnInit, OnDestroy {
  private readonly fb = inject(FormBuilder);
  private readonly moviesApi = inject(MoviesApiService);
  readonly auth = inject(AuthService);
  private saveSub?: Subscription;
  private fallbackValueSub?: Subscription;
  private fallbackSearchSub?: Subscription;
  private fallbackSelectionSub?: Subscription;
  private fallbackRecommendSub?: Subscription;
  private suggestedSub?: Subscription;
  private suggestedSubmitSub?: Subscription;

  readonly searchTypes: { value: OmdbSearchType; label: string }[] = [
    { value: 'movie', label: 'Movie' },
    { value: 'series', label: 'Series' }
  ];
  readonly fallbackSearchForm = this.fb.group({
    title: ['', Validators.required],
    year: [''],
    type: ['movie' as OmdbSearchType],
    exactTitleMatch: [false]
  });

  loading = false;
  saving = false;
  showNext = false;
  challenge: MovieChallenge | null = null;
  selectedMovieId = '';
  errorMessage = '';
  fallbackStep = 1;
  fallbackMovies: OmdbMovieSearchResult[] = [];
  fallbackSelectedMovie: OmdbMovieSearchResult | null = null;
  fallbackRecommendationSaved = false;
  fallbackSearchLoading = false;
  fallbackSelectingMovie = false;
  fallbackRecommending = false;
  fallbackErrorMessage = '';
  fallbackSuccessMessage = '';
  fallbackCurrentPage = 1;
  fallbackHasNext = false;
  private fallbackLastSearchKey = '';

  suggestedChallenges: SuggestedMovieChallenge[] = [];
  suggestedLoading = false;
  suggestedSaving = false;
  suggestedErrorMessage = '';
  higherRankedFirst = false;
  boostHigherRanks = false;
  moreInterestingFirst = false;
  selectedSuggestedMovieIds: Record<string, string> = {};
  visibleProbabilityHelpKey = '';
  visibleRankHelpKey = '';
  readonly probabilityHelpText = 'Chance of winning, based on previous comparisons';
  readonly rankHelpText = 'Your movie rank and rating, based on previous comparisons';

  ngOnInit(): void {
    this.fallbackValueSub = this.fallbackSearchForm.valueChanges.subscribe(() => {
      if (!this.auth.token) return;
      this.clearFallbackResults();
    });
    this.loadNextChallenge();
  }

  ngOnDestroy(): void {
    this.saveSub?.unsubscribe();
    this.fallbackValueSub?.unsubscribe();
    this.fallbackSearchSub?.unsubscribe();
    this.fallbackSelectionSub?.unsubscribe();
    this.fallbackRecommendSub?.unsubscribe();
    this.suggestedSub?.unsubscribe();
    this.suggestedSubmitSub?.unsubscribe();
  }

  loadNextChallenge(preserveFallbackSuccess = false): void {
    if (!this.auth.token) return;

    this.saveSub?.unsubscribe();
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
        if (!challenge) {
          this.resetFallbackWizardToSearch(preserveFallbackSuccess);
          this.loadSuggestedChallenges();
        }
      },
      error: err => {
        this.errorMessage = err?.error?.message ?? err?.message ?? 'Could not load movie challenge';
        this.loading = false;
      }
    });
  }

  poster(movie: { poster: string }): string {
    return movie.poster && movie.poster !== 'N/A' ? movie.poster : '/images/movie-poster.jpg';
  }

  selectMovieLocally(movie: MovieChallengeMovie): void {
    if (!this.challenge || this.saving) return;

    this.selectedMovieId = this.selectedMovieId === movie.imdbId ? '' : movie.imdbId;
    this.showNext = !!this.selectedMovieId;
    this.errorMessage = '';
  }

  saveSelectionAndLoadNext(): void {
    if (!this.challenge || !this.selectedMovieId || this.saving) return;

    this.saving = true;
    this.errorMessage = '';
    this.saveSub?.unsubscribe();
    this.saveSub = this.moviesApi.selectMovieChallengeWinner(
      this.challenge.movie1.imdbId,
      this.challenge.movie2.imdbId,
      this.selectedMovieId
    ).subscribe({
      next: () => {
        this.saving = false;
        this.loadNextChallenge();
      },
      error: err => {
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

  fallbackSearch(): void {
    if (this.fallbackSearchForm.invalid) {
      this.fallbackSearchForm.markAllAsTouched();
      return;
    }
    const criteria = this.currentFallbackCriteria();
    if (!this.readyForFallbackSearch(criteria)) return;
    this.runFallbackSearch(criteria, 1);
  }

  nextFallbackSearchPage(): void {
    if (!this.fallbackHasNext || this.fallbackSearchLoading) return;
    this.runFallbackSearch(this.currentFallbackCriteria(), this.fallbackCurrentPage + 1);
  }

  previousFallbackSearchPage(): void {
    if (this.fallbackCurrentPage <= 1 || this.fallbackSearchLoading) return;
    this.runFallbackSearch(this.currentFallbackCriteria(), this.fallbackCurrentPage - 1);
  }

  selectFallbackMovie(movie: OmdbMovieSearchResult): void {
    if (this.fallbackSelectingMovie) return;

    this.fallbackRecommendationSaved = false;
    this.fallbackSuccessMessage = '';
    this.fallbackErrorMessage = '';
    if (movie.detailsLoaded) {
      this.fallbackSelectedMovie = movie;
      this.fallbackStep = 2;
      return;
    }

    this.fallbackSelectedMovie = null;
    this.fallbackSelectingMovie = true;
    this.fallbackSelectionSub?.unsubscribe();
    this.fallbackSelectionSub = this.moviesApi.getOmdbMovieById(movie.imdbId).subscribe({
      next: selectedMovie => {
        this.fallbackSelectedMovie = selectedMovie;
        this.fallbackSelectingMovie = false;
        this.fallbackStep = 2;
      },
      error: err => {
        this.fallbackSelectingMovie = false;
        this.fallbackErrorMessage = err?.message ?? 'Could not load movie details';
      }
    });
  }

  fallbackBack(): void {
    this.fallbackSelectingMovie = false;
    this.fallbackSelectionSub?.unsubscribe();
    this.fallbackStep = Math.max(1, this.fallbackStep - 1);
  }

  fallbackSearchDisabled(): boolean {
    return this.fallbackSearchForm.invalid || this.fallbackSearchLoading;
  }

  recommendFallbackSelectedMovie(): void {
    if (!this.fallbackSelectedMovie || this.fallbackRecommending) return;
    if (!this.auth.token) {
      this.fallbackErrorMessage = 'Please log in or register to recommend movies.';
      return;
    }

    this.fallbackRecommending = true;
    this.fallbackErrorMessage = '';
    this.fallbackSuccessMessage = '';
    this.fallbackRecommendSub?.unsubscribe();
    this.fallbackRecommendSub = this.moviesApi.recommendMovieFromSearch(this.toRecommendationRequest(this.fallbackSelectedMovie)).subscribe({
      next: () => {
        this.fallbackRecommending = false;
        this.fallbackRecommendationSaved = true;
        this.fallbackStep = 3;
        this.fallbackSuccessMessage = 'Your recommendation is saved.';
        this.loadNextChallenge(true);
      },
      error: err => {
        this.fallbackRecommending = false;
        this.fallbackErrorMessage = err?.error?.message ?? err?.message ?? 'Could not recommend movie';
      }
    });
  }

  private runFallbackSearch(criteria: OmdbMovieSearchCriteria, page: number): void {
    if (!this.readyForFallbackSearch(criteria)) return;

    this.fallbackSearchLoading = true;
    this.fallbackSelectingMovie = false;
    this.fallbackErrorMessage = '';
    this.fallbackSuccessMessage = '';
    this.fallbackSelectedMovie = null;
    this.fallbackRecommendationSaved = false;
    this.fallbackStep = 1;
    this.fallbackCurrentPage = page;
    this.fallbackLastSearchKey = this.criteriaKey(criteria);
    this.fallbackSearchSub?.unsubscribe();
    this.fallbackSearchSub = this.moviesApi.searchOmdbMovies(criteria, page).subscribe({
      next: result => {
        if (this.criteriaKey(criteria) !== this.fallbackLastSearchKey) return;
        this.fallbackMovies = result.movies;
        this.fallbackHasNext = result.hasNext;
        this.fallbackSearchLoading = false;
        if (this.fallbackMovies.length === 0) {
          this.fallbackErrorMessage = 'No movies found';
        }
      },
      error: err => {
        if (this.criteriaKey(criteria) !== this.fallbackLastSearchKey) return;
        this.fallbackMovies = [];
        this.fallbackHasNext = false;
        this.fallbackSearchLoading = false;
        this.fallbackErrorMessage = err?.message ?? 'Movie search failed';
      }
    });
  }

  private clearFallbackResults(): void {
    this.fallbackSearchSub?.unsubscribe();
    this.fallbackSelectionSub?.unsubscribe();
    this.fallbackMovies = [];
    this.fallbackSelectedMovie = null;
    this.fallbackRecommendationSaved = false;
    this.fallbackStep = 1;
    this.fallbackSearchLoading = false;
    this.fallbackSelectingMovie = false;
    this.fallbackErrorMessage = '';
    this.fallbackSuccessMessage = '';
    this.fallbackCurrentPage = 1;
    this.fallbackHasNext = false;
    this.fallbackLastSearchKey = '';
  }

  private resetFallbackWizardToSearch(preserveSuccess = false): void {
    this.fallbackStep = 1;
    this.fallbackSelectedMovie = null;
    this.fallbackRecommendationSaved = false;
    this.fallbackSearchLoading = false;
    this.fallbackSelectingMovie = false;
    this.fallbackRecommending = false;
    this.fallbackErrorMessage = '';
    if (!preserveSuccess) {
      this.fallbackSuccessMessage = '';
    }
    this.fallbackCurrentPage = 1;
    this.fallbackHasNext = false;
    this.fallbackMovies = [];
    this.fallbackLastSearchKey = '';
    this.fallbackSelectionSub?.unsubscribe();
    this.fallbackSearchForm.reset({
      title: '',
      year: '',
      type: 'movie',
      exactTitleMatch: false
    }, { emitEvent: false });
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

  private currentFallbackCriteria(): OmdbMovieSearchCriteria {
    const value = this.fallbackSearchForm.getRawValue();
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

  private readyForFallbackSearch(criteria: OmdbMovieSearchCriteria): boolean {
    return !!criteria.title;
  }

  loadSuggestedChallenges(): void {
    if (!this.auth.token) return;

    this.suggestedSub?.unsubscribe();
    this.suggestedLoading = true;
    this.suggestedErrorMessage = '';
    this.visibleProbabilityHelpKey = '';
    this.visibleRankHelpKey = '';
    this.suggestedSub = this.moviesApi.listSuggestedMovieChallenges(
      this.higherRankedFirst,
      this.boostHigherRanks,
      this.moreInterestingFirst
    ).subscribe({
      next: challenges => {
        this.suggestedChallenges = challenges;
        this.selectedSuggestedMovieIds = {};
        this.suggestedLoading = false;
      },
      error: err => {
        this.suggestedChallenges = [];
        this.suggestedErrorMessage = err?.error?.message ?? err?.message ?? 'Could not load extra challenges';
        this.selectedSuggestedMovieIds = {};
        this.suggestedLoading = false;
      }
    });
  }

  toggleHigherRankedFirst(event: Event): void {
    this.higherRankedFirst = (event.target as HTMLInputElement).checked;
    if (this.higherRankedFirst) { this.boostHigherRanks = false; this.moreInterestingFirst = false; }
    this.loadSuggestedChallenges();
  }

  toggleBoostHigherRanks(event: Event): void {
    this.boostHigherRanks = (event.target as HTMLInputElement).checked;
    if (this.boostHigherRanks) { this.higherRankedFirst = false; this.moreInterestingFirst = false; }
    this.loadSuggestedChallenges();
  }

  toggleMoreInterestingFirst(event: Event): void {
    this.moreInterestingFirst = (event.target as HTMLInputElement).checked;
    if (this.moreInterestingFirst) { this.higherRankedFirst = false; this.boostHigherRanks = false; }
    this.loadSuggestedChallenges();
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
        this.loadSuggestedChallenges();
      },
      error: err => {
        this.suggestedSaving = false;
        this.suggestedErrorMessage = err?.error?.message ?? err?.message ?? 'Could not submit extra challenges';
      }
    });
  }

  discardSuggestedSelections(): void {
    if (this.suggestedSaving) return;

    this.selectedSuggestedMovieIds = {};
    this.loadSuggestedChallenges();
  }

  toggleProbabilityHelp(challenge: SuggestedMovieChallenge, movie: SuggestedMovieChallengeMovie): void {
    const key = `${this.challengeKey(challenge)}:${movie.imdbId}`;
    this.visibleProbabilityHelpKey = this.visibleProbabilityHelpKey === key ? '' : key;
  }

  probabilityHelpVisible(challenge: SuggestedMovieChallenge, movie: SuggestedMovieChallengeMovie): boolean {
    return this.visibleProbabilityHelpKey === `${this.challengeKey(challenge)}:${movie.imdbId}`;
  }

  toggleRankHelp(challenge: SuggestedMovieChallenge, movie: SuggestedMovieChallengeMovie): void {
    const key = `${this.challengeKey(challenge)}:${movie.imdbId}`;
    this.visibleRankHelpKey = this.visibleRankHelpKey === key ? '' : key;
  }

  rankHelpVisible(challenge: SuggestedMovieChallenge, movie: SuggestedMovieChallengeMovie): boolean {
    return this.visibleRankHelpKey === `${this.challengeKey(challenge)}:${movie.imdbId}`;
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
}
