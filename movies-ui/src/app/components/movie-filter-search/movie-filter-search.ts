import { CommonModule } from '@angular/common';
import { Component, EventEmitter, Input, OnChanges, OnDestroy, Output, SimpleChanges, inject } from '@angular/core';
import { Subscription } from 'rxjs';
import { AuthService } from '../../services/auth';
import { MoviesApiService, OmdbMovieSearchResult, ParsedMovieSearch } from '../../services/movies-api';
import { MoviePageNavigatorComponent } from '../movie-page-navigator/movie-page-navigator';
import { CategoryTreeDialogComponent } from '../category-tree-dialog/category-tree-dialog';

export type ExternalMovieAction = 'add' | 'like' | 'addToCategory' | 'addToJourney';

@Component({
  standalone: true,
  selector: 'app-movie-filter-search',
  imports: [CommonModule, MoviePageNavigatorComponent, CategoryTreeDialogComponent],
  templateUrl: './movie-filter-search.html',
  styleUrl: './movie-filter-search.css'
})
export class MovieFilterSearchComponent implements OnDestroy, OnChanges {
  private readonly api = inject(MoviesApiService);
  readonly auth = inject(AuthService);

  @Input() value = '';
  @Input() label = 'Filter';
  @Input() ariaLabel = 'Filter movies';
  @Input() allowExternalActions = true;
  @Input() externalActions: ExternalMovieAction[] = ['add', 'like'];
  @Input() showSelectCategories = true;
  @Input() showNotRecommendedFilter = false;
  @Input() initialSelectedCategories: number[] = [];
  @Output() valueChange = new EventEmitter<string>();
  @Output() internalSearch = new EventEmitter<ParsedMovieSearch>();
  @Output() cleared = new EventEmitter<void>();
  @Output() externalAction = new EventEmitter<{ action: ExternalMovieAction; movie: OmdbMovieSearchResult }>();

  searchOmdb = false;
  notRecommendedOnly = false;
  selectedCategories: number[] = [];
  categoryDialogVisible = false;
  externalResults: OmdbMovieSearchResult[] = [];
  selectedMovie: OmdbMovieSearchResult | null = null;
  loading = false;
  selecting = false;
  acting = false;
  errorMessage = '';
  currentPage = 1;
  readonly pageSize = 10;
  private timer?: ReturnType<typeof setTimeout>;
  private searchSub?: Subscription;
  private detailsSub?: Subscription;
  private actionSub?: Subscription;

  ngOnChanges(changes: SimpleChanges): void {
    if (!changes['initialSelectedCategories']) return;
    this.selectedCategories = [...this.initialSelectedCategories];
    // The initial value is already reflected by loading the page itself (the parent's own first load), so only
    // a later change needs to trigger a fresh search here.
    if (changes['initialSelectedCategories'].firstChange) return;
    this.runInternalSearch(this.value.trim());
  }

  ngOnDestroy(): void {
    this.cancelTimer();
    this.searchSub?.unsubscribe();
    this.detailsSub?.unsubscribe();
    this.actionSub?.unsubscribe();
  }

  onInput(event: Event): void {
    this.value = (event.target as HTMLInputElement).value;
    this.valueChange.emit(this.value);
    this.cancelTimer();
    if (!this.value.trim()) {
      this.runInternalSearch('');
      this.resetExternalResults();
      return;
    }
    if (this.value.trim().length >= 4) {
      this.timer = setTimeout(() => this.search(), 300);
    }
  }

  toggleOmdb(event: Event): void {
    this.searchOmdb = (event.target as HTMLInputElement).checked;
    this.resetExternalResults();
    if (this.value.trim()) this.search();
  }

  toggleNotRecommended(event: Event): void {
    this.notRecommendedOnly = (event.target as HTMLInputElement).checked;
    this.runInternalSearch(this.value.trim());
  }

  search(): void {
    this.cancelTimer();
    const query = this.value.trim();
    if (!query && this.selectedCategories.length === 0 && !this.notRecommendedOnly) return;
    // OMDb augments the normal list; it never replaces the page's own filter.
    this.runInternalSearch(query);
    if (!this.searchOmdb) {
      return;
    }
    if (!query) return;

    this.searchSub?.unsubscribe();
    this.loading = true;
    this.errorMessage = '';
    this.selectedMovie = null;
    this.searchSub = this.api.searchOmdbFromFilter(query).subscribe({
      next: movies => {
        if (this.value.trim() !== query || !this.searchOmdb) return;
        this.externalResults = movies;
        this.currentPage = 1;
        this.loading = false;
      },
      error: error => {
        this.externalResults = [];
        this.loading = false;
        this.errorMessage = error?.message ?? 'OMDb search failed';
      }
    });
  }

  clear(): void {
    this.cancelTimer();
    this.value = '';
    this.valueChange.emit('');
    this.searchOmdb = false;
    this.notRecommendedOnly = false;
    // Restores the page's own default category selection (e.g. a Movie Guide/Personality's own category) rather
    // than clearing it outright — for pages with no default (plain "" -> []) this is unchanged.
    this.selectedCategories = [...this.initialSelectedCategories];
    this.resetExternalResults();
    this.internalSearch.emit({ keyword: '', year: '', selectedCategories: [...this.initialSelectedCategories], onlyNotRecommended: false });
    this.cleared.emit();
  }

  selectMovie(movie: OmdbMovieSearchResult): void {
    if (this.selecting) return;
    if (movie.detailsLoaded) {
      this.selectedMovie = movie;
      return;
    }
    this.detailsSub?.unsubscribe();
    this.selecting = true;
    this.errorMessage = '';
    this.detailsSub = this.api.getOmdbMovieById(movie.imdbId).subscribe({
      next: details => { this.selectedMovie = details; this.selecting = false; },
      error: error => { this.selecting = false; this.errorMessage = error?.message ?? 'Could not load movie details'; }
    });
  }

  closeMovie(): void { this.selectedMovie = null; }

  openCategoryDialog(): void {
    this.categoryDialogVisible = true;
  }

  onCategorySelectionChanged(categoryIds: number[]): void {
    this.selectedCategories = categoryIds;
    this.runInternalSearch(this.value.trim());
  }

  closeCategoryDialog(): void {
    this.categoryDialogVisible = false;
  }

  act(action: ExternalMovieAction): void {
    if (!this.selectedMovie || this.acting) return;
    const movie = this.selectedMovie;
    if (!this.allowExternalActions || action === 'addToCategory') {
      this.acting = true;
      this.externalAction.emit({ action, movie });
      return;
    }
    if (!this.auth.token) {
      this.errorMessage = 'Please sign in to update movies.';
      return;
    }
    this.acting = true;
    this.errorMessage = '';
    const request = action === 'add'
      ? this.api.createMovieFromSearch(movie)
      : action === 'like'
        ? this.api.likeMovieFromSearch(movie)
        : this.api.recommendMovieFromSearch(this.api.movieFromOmdb(movie));
    this.actionSub?.unsubscribe();
    this.actionSub = request.subscribe({
      next: () => this.completeAction(),
      error: error => {
        this.acting = false;
        this.errorMessage = error?.error?.message ?? error?.message ?? `Could not ${action} movie`;
      }
    });
  }

  pageMovies(): OmdbMovieSearchResult[] {
    const start = (this.currentPage - 1) * this.pageSize;
    return this.externalResults.slice(start, start + this.pageSize);
  }

  poster(movie: OmdbMovieSearchResult): string {
    return movie.poster && movie.poster !== 'N/A' ? movie.poster : '/images/movie-poster.jpg';
  }

  completeExternalAction(): void { this.completeAction(); }

  failExternalAction(error: unknown): void {
    this.acting = false;
    const failure = error as { error?: { message?: string }; message?: string };
    this.errorMessage = failure?.error?.message ?? failure?.message ?? 'Could not update movie';
  }

  private completeAction(): void {
    this.acting = false;
    this.selectedMovie = null;
    this.value = '';
    this.valueChange.emit('');
    this.searchOmdb = false;
    this.notRecommendedOnly = false;
    this.selectedCategories = [];
    this.resetExternalResults();
    this.internalSearch.emit({ keyword: '', year: '', selectedCategories: [], onlyNotRecommended: false });
  }

  private runInternalSearch(value: string): void {
    this.internalSearch.emit({
      ...this.api.parseMovieSearch(value),
      selectedCategories: [...this.selectedCategories],
      onlyNotRecommended: this.notRecommendedOnly
    });
  }

  private resetExternalResults(): void {
    this.searchSub?.unsubscribe();
    this.detailsSub?.unsubscribe();
    this.externalResults = [];
    this.selectedMovie = null;
    this.currentPage = 1;
    this.loading = false;
    this.selecting = false;
    this.errorMessage = '';
  }

  private cancelTimer(): void {
    if (this.timer) { clearTimeout(this.timer); this.timer = undefined; }
  }
}
