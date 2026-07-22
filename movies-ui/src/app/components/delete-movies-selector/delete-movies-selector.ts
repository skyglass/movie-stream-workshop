import { CommonModule } from '@angular/common';
import { Component, EventEmitter, Input, OnInit, Output, ViewChild, inject } from '@angular/core';
import { Movie, MoviesApiService, ParsedMovieSearch } from '../../services/movies-api';
import { MovieFilterSearchComponent } from '../movie-filter-search/movie-filter-search';
import { MoviePageNavigatorComponent } from '../movie-page-navigator/movie-page-navigator';
import { CategoryTreeDialogComponent } from '../category-tree-dialog/category-tree-dialog';

@Component({
  standalone: true,
  selector: 'app-delete-movies-selector',
  imports: [CommonModule, MovieFilterSearchComponent, MoviePageNavigatorComponent, CategoryTreeDialogComponent],
  templateUrl: './delete-movies-selector.html',
  styleUrl: './delete-movies-selector.css'
})
export class DeleteMoviesSelectorComponent implements OnInit {
  private readonly api = inject(MoviesApiService);
  @ViewChild(MovieFilterSearchComponent) filterSearch!: MovieFilterSearchComponent;

  // Exactly one of movieGuideId/watchlistId is set by the caller -- needed to call the right ownership-checked
  // remove endpoint.
  @Input() movieGuideId: number | null = null;
  @Input() watchlistId: number | null = null;
  // The guide/personality's own anchor category -- the default scope, and the fallback whenever the category
  // picker below ends up with nothing selected. Not used for watchlists: an empty pick there already means "the
  // whole watchlist" server-side (see WatchlistService.watchlistMovies/removeMovie), so no fallback substitution
  // is needed or wanted -- substituting the anchor id in would break watchlistMovies' "all ids must resolve the
  // same way" check the moment a subscribed category is involved.
  @Input() guideCategoryId: number | null = null;
  // The watchlist's own anchor category id, required alongside watchlistId -- see category-tree-dialog's
  // rootCategoryId doc for why this needs to travel separately from watchlistId.
  @Input() watchlistCategoryId: number | null = null;
  // The page's currently-selected sub-category (from "Select Category"), if any -- used as the initial scope.
  @Input() initialCategoryId: number | null = null;
  // Subscribed/default categories, excluded from the category picker -- movies can't be removed from those.
  @Input() excludedCategoryIds: number[] = [];
  @Output() closed = new EventEmitter<void>();
  @Output() moviesDeleted = new EventEmitter<void>();

  filterText = '';
  activeFilter = '';
  activeYear = '';
  // The picker's own raw state -- empty means "no sub-category explicitly picked", which is a real, distinct
  // state (not the same as "the guide root is picked"). Never put guideCategoryId in here: it isn't even a node
  // in the picker's own subtree, so once added it could never be unchecked again, and its transitive closure
  // would silently cover every sub-category, making any other pick invisible in the results.
  pickedCategoryIds: number[] = [];
  categoryDialogVisible = false;

  movies: Movie[] = [];
  loading = false;
  errorMessage = '';
  currentPage = 1;
  totalCount = 0;
  readonly pageSize = this.api.moviePageSize;
  deletingIds = new Set<string>();

  // The delete-request scope (and, for a watchlist, the listing scope too): whatever's explicitly picked; for a
  // Movie Guide, falls back to the guide's own root when nothing is picked. That fallback's transitive closure
  // does reach into any subscribed/default categories physically DAG-linked under the guide root, but
  // MovieGuideService.removeMovie always excludes those from the actual deletion server-side regardless of this
  // scope, so a subscribed category's real movie_category rows can never be touched from here. For a watchlist,
  // an empty pick is left empty -- the backend already treats that as "the whole watchlist" on both the list and
  // remove endpoints.
  get effectiveCategoryIds(): number[] {
    if (this.pickedCategoryIds.length || this.watchlistId != null) return this.pickedCategoryIds;
    return this.guideCategoryId != null ? [this.guideCategoryId] : [];
  }

  ngOnInit(): void {
    this.pickedCategoryIds = this.initialCategoryId != null ? [this.initialCategoryId] : [];
    this.loadMovies(1);
  }

  loadMovies(page = this.currentPage): void {
    this.loading = true;
    this.errorMessage = '';
    // For a Movie Guide with nothing explicitly picked, list via the guide-scoped endpoint (movie-guides/{id}/movies),
    // which already excludes movies that only show up here via a subscribed/default category -- unlike the generic
    // /api/movies?selectedCategories=[guideCategoryId] filter, whose transitive closure reaches straight through
    // subscribed categories. Once the user explicitly picks one of the guide's own native sub-categories (the
    // picker itself excludes subscribed nodes, see excludedCategoryIds), that pick can never resolve into
    // subscribed territory, so the generic filtered listing is safe to use for it.
    const request = this.watchlistId != null
      ? this.api.listWatchlistMovies(this.watchlistId, this.effectiveCategoryIds, page, this.pageSize, this.activeFilter, this.activeYear)
      : this.pickedCategoryIds.length
        ? this.api.listMovies(page, this.pageSize, this.activeFilter, this.activeYear, this.pickedCategoryIds)
        : this.api.listGuideMovies(this.movieGuideId!, page, this.pageSize, this.activeFilter, this.activeYear);
    request.subscribe({
      next: moviePage => {
        this.movies = moviePage.movies;
        this.totalCount = moviePage.totalCount;
        this.currentPage = page;
        this.loading = false;
      },
      error: err => {
        this.errorMessage = err?.error?.message ?? err?.message ?? 'Could not load movies';
        this.loading = false;
      }
    });
  }

  onInternalSearch(search: ParsedMovieSearch): void {
    if (search.keyword === this.activeFilter && search.year === this.activeYear) return;
    this.activeFilter = search.keyword;
    this.activeYear = search.year;
    this.loadMovies(1);
  }

  openCategoryDialog(): void {
    this.categoryDialogVisible = true;
  }

  closeCategoryDialog(): void {
    this.categoryDialogVisible = false;
  }

  // Fires on every check/uncheck (not just OK) -- same live-refresh pattern as the other category dialogs.
  onCategorySelectionChanged(categoryIds: number[]): void {
    this.pickedCategoryIds = categoryIds;
    this.loadMovies(1);
  }

  onCategoriesSelected(categoryIds: number[]): void {
    this.pickedCategoryIds = categoryIds;
    this.categoryDialogVisible = false;
  }

  isDeleting(movie: Movie): boolean {
    return this.deletingIds.has(movie.imdbId);
  }

  // Removes the movie from every category within the currently-selected scope's transitive subtree in one
  // request -- reaches the movie regardless of which exact descendant category it's actually filed under,
  // matching how the movie list itself is matched (both use the same transitive closure server-side).
  deleteMovie(movie: Movie): void {
    if (this.isDeleting(movie)) return;
    this.deletingIds.add(movie.imdbId);
    this.errorMessage = '';
    const request = this.watchlistId != null
      ? this.api.removeWatchlistMovie(this.watchlistId, movie.imdbId, this.effectiveCategoryIds)
      : this.api.removeGuideMovie(this.movieGuideId!, movie.imdbId, this.effectiveCategoryIds);
    request.subscribe({
      next: () => {
        this.deletingIds.delete(movie.imdbId);
        this.filterText = '';
        this.activeFilter = '';
        this.activeYear = '';
        this.filterSearch.clear();
        this.loadMovies(1);
        this.moviesDeleted.emit();
      },
      error: err => {
        this.deletingIds.delete(movie.imdbId);
        this.errorMessage = err?.error?.message ?? err?.message ?? 'Could not delete this movie';
      }
    });
  }

  close(): void {
    this.closed.emit();
  }
}
