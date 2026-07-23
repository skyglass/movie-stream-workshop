import { CommonModule } from '@angular/common';
import { Component, OnDestroy, OnInit, ViewChild, inject } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { Meta, Title } from '@angular/platform-browser';
import { Observable, Subscription, map } from 'rxjs';
import { AuthService } from '../../services/auth';
import { Movie, MovieCategory, MoviesApiService, Operator, OmdbMovieSearchResult, ParsedMovieSearch, WatchlistDto } from '../../services/movies-api';
import { MovieFilterSearchComponent } from '../movie-filter-search/movie-filter-search';
import { MovieGridComponent } from '../movie-grid/movie-grid';
import { MovieSelectorComponent } from '../movie-selector/movie-selector';
import { CategoryTreeDialogComponent } from '../category-tree-dialog/category-tree-dialog';
import { ImportCsvDialogComponent } from '../import-csv-dialog/import-csv-dialog';
import { DeleteMoviesSelectorComponent } from '../delete-movies-selector/delete-movies-selector';
import { ShareDialogComponent } from '../share-dialog/share-dialog';
import { findCategoryPath, subCategorySegments } from '../../utils/category-path';

// Trimmed port of MovieGuideDetailComponent for a private watchlist: same "Select Category"/"Subscribe to
// Categories"/"Add Movies"/CSV import/"Delete Movies" mechanics, reusing the exact same dialog components (just
// pointed at the watchlist-scoped API via their watchlistId inputs). Two things Guide has that a private
// watchlist deliberately doesn't: no public "Share" link (this page is owner-only, enforced server-side) -- its
// "Export" button reuses ShareDialogComponent with an empty shareUrl, which hides the link row and leaves only
// "Download Poster Collage"/"Download CSV file" -- and no wizard/wizard-status handling (a watchlist is created
// complete in one dialog, not over multiple steps).
@Component({
  standalone: true,
  selector: 'app-watchlist-detail',
  imports: [CommonModule, RouterLink, MovieFilterSearchComponent, MovieGridComponent, MovieSelectorComponent,
    CategoryTreeDialogComponent, ImportCsvDialogComponent, DeleteMoviesSelectorComponent, ShareDialogComponent],
  templateUrl: './watchlist-detail.html',
  styleUrl: './watchlist-detail.css'
})
export class WatchlistDetailComponent implements OnInit, OnDestroy {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly api = inject(MoviesApiService);
  private readonly meta = inject(Meta);
  private readonly title = inject(Title);
  readonly auth = inject(AuthService);
  @ViewChild(MovieFilterSearchComponent) filterSearch!: MovieFilterSearchComponent;
  private paramsSub?: Subscription;
  private hasLoadedOnce = false;

  watchlistId = 0;
  watchlist: WatchlistDto | null = null;
  loading = true;
  errorMessage = '';
  movies: Movie[] = [];
  moviesLoading = false;
  currentPage = 1;
  totalCount = 0;
  readonly pageSize = this.api.moviePageSize;
  filterText = '';
  activeFilter = '';
  activeYear = '';
  // Empty means "the whole watchlist" (its own movies + private subtree + subscribed categories) -- unlike
  // Guide, there's no single anchor category id that already covers all of that via one transitive-closure join,
  // so "no scope picked" has to stay its own real, distinct state instead of defaulting to [categoryId].
  activeCategoryIds: number[] = [];
  activeOnlyNotRecommended = false;
  hasActiveFilter = false;

  isOwner = false;
  movieSelectorVisible = false;
  // Populated from WatchlistDto.subscribedCategoryIds -- every public category currently followed via an OR
  // subscription component (see WatchlistService.subscribedPublicCategoryIds).
  subscribedCategoryIds: number[] = [];
  selectedCategory: number | null = null;
  categoryDialogVisible = false;
  composeCategoryDialogVisible = false;
  composeCategoryOperator: Operator = 'AND';
  csvDialogVisible = false;
  deleteMoviesSelectorVisible = false;
  descriptionDialogVisible = false;
  shareDialogVisible = false;
  // Cached purely to resolve selectedCategoryPath below (the breadcrumb next to "Select Category") -- refreshed
  // whenever the picker dialog closes, since that's the only time the picked node's identity can change.
  categoryTree: MovieCategory[] = [];

  ngOnInit(): void {
    // Reactive (not a one-shot route.snapshot read), same reasoning as MovieGuideDetailComponent: the
    // matcher-based route reuses this same component instance when only subCategoryId changes.
    this.paramsSub = this.route.paramMap.subscribe(params => {
      const requestedRootId = Number(params.get('id'));
      const subIdParam = params.get('subCategoryId');
      const sameRoot = this.hasLoadedOnce && this.watchlistId === requestedRootId;
      this.watchlistId = requestedRootId;
      this.selectedCategory = subIdParam != null ? Number(subIdParam) : null;
      if (!sameRoot) {
        this.hasLoadedOnce = true;
        this.loadWatchlist();
        this.loadCategoryTree();
      }
      this.loadMovies(1);
    });
  }

  ngOnDestroy(): void {
    this.paramsSub?.unsubscribe();
  }

  watchlistRootSegments(): (string | number)[] {
    return ['/my-watchlists', this.watchlistId];
  }

  watchlistBreadcrumbSegments(path: MovieCategory[]): (string | number)[] {
    return subCategorySegments(this.watchlistRootSegments(), path);
  }

  get canManage(): boolean {
    return this.isOwner || this.auth.isAdmin;
  }

  get showAddMovies(): boolean {
    if (!this.canManage) return false;
    const targetingSubscribedCategory = this.selectedCategory != null && this.subscribedCategoryIds.includes(this.selectedCategory);
    return !targetingSubscribedCategory;
  }

  get watchlistCategoryId(): number | null {
    return this.watchlist?.categoryId ?? null;
  }

  openMovieSelector(): void {
    this.movieSelectorVisible = true;
  }

  onMoviesSelected(imdbIds: string[]): void {
    this.movieSelectorVisible = false;
    if (imdbIds.length === 0) return;
    this.errorMessage = '';
    this.api.assignMoviesToWatchlist(this.watchlistId, imdbIds, this.selectedCategory).subscribe({
      next: () => this.loadMovies(1),
      error: err => {
        this.errorMessage = err?.error?.message ?? err?.message ?? 'Could not add the selected movies';
      }
    });
  }

  // The inline "Search OMDb" box's own "Add to this Watchlist" button (allowExternalActions=false +
  // externalActions=['addToCategory'] on the filter below) -- mirrors onMoviesSelected's two-step,
  // properly-authorized path (create-in-catalog, then assignMoviesToWatchlist) rather than the generic,
  // ownership-unchecked CategoryService.addMovieFromSearchToCategory endpoint.
  onExternalAddToWatchlist(event: { movie: OmdbMovieSearchResult }): void {
    this.errorMessage = '';
    this.api.createMovieFromSearch(event.movie).subscribe({
      next: created => {
        this.api.assignMoviesToWatchlist(this.watchlistId, [created.imdbId], this.selectedCategory).subscribe({
          next: () => {
            this.filterSearch.completeExternalAction();
            this.loadMovies(1);
          },
          error: err => this.filterSearch.failExternalAction(err)
        });
      },
      error: err => this.filterSearch.failExternalAction(err)
    });
  }

  openCategoryDialog(): void {
    this.categoryDialogVisible = true;
  }

  closeCategoryDialog(): void {
    this.categoryDialogVisible = false;
    this.loadCategoryTree();
  }

  // Composition categories are always created directly under the watchlist's own root -- never under whatever
  // sub-category happens to be selected -- to avoid confusion about where they land.
  get composeCategoryParentId(): number | null {
    return this.watchlistCategoryId;
  }

  openComposeCategoryDialog(operator: Operator = 'AND'): void {
    this.composeCategoryOperator = operator;
    this.composeCategoryDialogVisible = true;
  }

  closeComposeCategoryDialog(): void {
    this.composeCategoryDialogVisible = false;
    this.loadCategoryTree();
  }

  // Ancestor chain from the picker's own top level down to the selected node (inclusive) -- e.g. a private
  // sub-category "Genres -> Drama" becomes ["Genres", "Drama"]; a subscribed category is always just itself
  // (the picker never nests anything under a subscribed entry).
  get selectedCategoryPath(): MovieCategory[] {
    if (this.selectedCategory == null) return [];
    return findCategoryPath(this.categoryTree, this.selectedCategory) ?? [];
  }

  get selectedCategoryLeaf(): MovieCategory | null {
    const path = this.selectedCategoryPath;
    return path.length ? path[path.length - 1] : null;
  }

  private loadCategoryTree(): void {
    this.api.getWatchlistCategoryPicker(this.watchlistId).subscribe({
      next: categories => { this.categoryTree = categories; },
      error: () => {}
    });
  }

  // Navigates to the friendly URL for the picked sub-category (or back to the watchlist's own root URL when
  // cleared) rather than setting selectedCategory directly -- the paramMap subscription in ngOnInit is the single
  // source of truth that actually applies the change and re-fetches movies.
  onCategorySelectionChanged(categoryIds: number[]): void {
    const subId = categoryIds.length ? categoryIds[0] : null;
    if (subId == null) {
      this.router.navigate(this.watchlistRootSegments());
      return;
    }
    const path = findCategoryPath(this.categoryTree, subId) ?? [];
    this.router.navigate(this.watchlistBreadcrumbSegments(path));
  }

  openImportCsv(): void {
    this.csvDialogVisible = true;
  }

  closeImportCsv(): void {
    this.csvDialogVisible = false;
  }

  onCsvImported(): void {
    this.csvDialogVisible = false;
    this.refreshAfterImport();
  }

  onCsvImportFailed(message: string): void {
    this.csvDialogVisible = false;
    this.refreshAfterImport();
    this.errorMessage = message;
  }

  openDeleteMovies(): void {
    this.deleteMoviesSelectorVisible = true;
  }

  closeDeleteMovies(): void {
    this.deleteMoviesSelectorVisible = false;
  }

  onMoviesDeleted(): void {
    this.refreshAfterImport();
  }

  private refreshAfterImport(): void {
    this.filterSearch.clear();
    this.loadMovies(1);
  }

  openDescriptionDialog(): void {
    this.descriptionDialogVisible = true;
  }

  closeDescriptionDialog(): void {
    this.descriptionDialogVisible = false;
  }

  selectedCategoryDescriptionVisible = false;

  openSelectedCategoryDescription(): void {
    this.selectedCategoryDescriptionVisible = true;
  }

  closeSelectedCategoryDescription(): void {
    this.selectedCategoryDescriptionVisible = false;
  }

  private loadWatchlist(): void {
    this.loading = true;
    this.api.getWatchlist(this.watchlistId).subscribe({
      next: watchlist => {
        this.watchlist = watchlist;
        this.isOwner = watchlist.owner === this.auth.currentUser?.username;
        this.subscribedCategoryIds = watchlist.subscribedCategoryIds;
        this.loading = false;
        this.applySeoMetadata();
      },
      error: err => {
        this.errorMessage = err?.error?.message ?? err?.message ?? 'Could not load this watchlist';
        this.loading = false;
      }
    });
  }

  loadMovies(page = this.currentPage): void {
    this.moviesLoading = true;
    this.errorMessage = '';
    const categoryIds = this.selectedCategory != null ? [this.selectedCategory] : this.activeCategoryIds;
    this.api.listWatchlistMovies(this.watchlistId, categoryIds, page, this.pageSize, this.activeFilter, this.activeYear).subscribe({
      next: moviePage => {
        this.movies = moviePage.movies;
        this.totalCount = moviePage.totalCount;
        this.currentPage = page;
        this.moviesLoading = false;
      },
      error: err => {
        this.errorMessage = err?.error?.message ?? err?.message ?? 'Could not load movies';
        this.moviesLoading = false;
      }
    });
  }

  applyFilter(search: ParsedMovieSearch): void {
    const categories = search.selectedCategories ?? [];
    const onlyNotRecommended = search.onlyNotRecommended ?? false;
    if (search.keyword === this.activeFilter && search.year === this.activeYear
      && categories.join() === this.activeCategoryIds.join() && onlyNotRecommended === this.activeOnlyNotRecommended) return;
    this.activeFilter = search.keyword;
    this.activeYear = search.year;
    this.activeCategoryIds = categories;
    this.activeOnlyNotRecommended = onlyNotRecommended;
    this.hasActiveFilter = search.hasActiveFilter ?? false;
    this.loadMovies(1);
  }

  emptyMessage(): string {
    return this.hasActiveFilter ? 'No movies found' : 'No movies in this watchlist yet';
  }

  openShareDialog(): void {
    this.shareDialogVisible = true;
  }

  closeShareDialog(): void {
    this.shareDialogVisible = false;
  }

  // Fetches up to maxMovies movies in the exact order shown on-screen for the current filter/category
  // selection, without touching this component's own movies/currentPage/totalCount state -- passed into
  // ShareDialogComponent's "Download Poster Collage"/"Download CSV file" features.
  fetchOrderedMovies = (maxMovies: number): Observable<Movie[]> => {
    const categoryIds = this.selectedCategory != null ? [this.selectedCategory] : this.activeCategoryIds;
    return this.api.listWatchlistMovies(this.watchlistId, categoryIds, 1, maxMovies, this.activeFilter, this.activeYear)
      .pipe(map(page => page.movies));
  };

  private applySeoMetadata(): void {
    const name = this.watchlist?.name ?? 'Watchlist';
    const pageTitle = `${name} | Movie Challenge`;
    this.title.setTitle(pageTitle);
    this.meta.updateTag({ name: 'robots', content: 'noindex, nofollow' });
  }
}
