import { CommonModule } from '@angular/common';
import { Component, OnInit, ViewChild, inject } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { Meta, Title } from '@angular/platform-browser';
import { Observable, map } from 'rxjs';
import { AuthService } from '../../services/auth';
import { Movie, MovieCategory, MoviesApiService, OmdbMovieSearchResult, ParsedMovieSearch, WatchlistDto } from '../../services/movies-api';
import { MovieFilterSearchComponent } from '../movie-filter-search/movie-filter-search';
import { MovieGridComponent } from '../movie-grid/movie-grid';
import { MovieSelectorComponent } from '../movie-selector/movie-selector';
import { CategoryTreeDialogComponent } from '../category-tree-dialog/category-tree-dialog';
import { ImportCsvDialogComponent } from '../import-csv-dialog/import-csv-dialog';
import { DeleteMoviesSelectorComponent } from '../delete-movies-selector/delete-movies-selector';
import { ShareDialogComponent } from '../share-dialog/share-dialog';

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
  imports: [CommonModule, MovieFilterSearchComponent, MovieGridComponent, MovieSelectorComponent,
    CategoryTreeDialogComponent, ImportCsvDialogComponent, DeleteMoviesSelectorComponent, ShareDialogComponent],
  templateUrl: './watchlist-detail.html',
  styleUrl: './watchlist-detail.css'
})
export class WatchlistDetailComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly api = inject(MoviesApiService);
  private readonly meta = inject(Meta);
  private readonly title = inject(Title);
  readonly auth = inject(AuthService);
  @ViewChild(MovieFilterSearchComponent) filterSearch!: MovieFilterSearchComponent;

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
  subscribedCategoryIds: number[] = [];
  selectedCategory: number | null = null;
  categoryDialogVisible = false;
  csvDialogVisible = false;
  deleteMoviesSelectorVisible = false;
  subscribeCategoriesVisible = false;
  subscribingCategoryIds: number[] = [];
  subscribing = false;
  descriptionDialogVisible = false;
  shareDialogVisible = false;
  // Cached purely to resolve selectedCategoryPath below (the breadcrumb next to "Select Category") -- refreshed
  // whenever the picker dialog closes, since that's the only time the picked node's identity can change.
  categoryTree: MovieCategory[] = [];

  ngOnInit(): void {
    this.watchlistId = Number(this.route.snapshot.paramMap.get('id'));
    this.loadWatchlist();
    this.loadMovies(1);
    this.loadCategoryTree();
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

  // Ancestor chain from the picker's own top level down to the selected node (inclusive) -- e.g. a private
  // sub-category "Genres -> Drama" becomes ["Genres", "Drama"]; a subscribed category is always just itself
  // (the picker never nests anything under a subscribed entry).
  get selectedCategoryPath(): MovieCategory[] {
    if (this.selectedCategory == null) return [];
    return this.findCategoryPath(this.categoryTree, this.selectedCategory) ?? [];
  }

  get selectedCategoryLeaf(): MovieCategory | null {
    const path = this.selectedCategoryPath;
    return path.length ? path[path.length - 1] : null;
  }

  private findCategoryPath(categories: MovieCategory[], targetId: number): MovieCategory[] | null {
    for (const category of categories) {
      if (category.id === targetId) return [category];
      const found = this.findCategoryPath(category.children, targetId);
      if (found) return [category, ...found];
    }
    return null;
  }

  private loadCategoryTree(): void {
    this.api.getWatchlistCategoryPicker(this.watchlistId).subscribe({
      next: categories => { this.categoryTree = categories; },
      error: () => {}
    });
  }

  onCategorySelectionChanged(categoryIds: number[]): void {
    this.selectedCategory = categoryIds.length ? categoryIds[0] : null;
    this.loadMovies(1);
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

  openSubscribeCategories(): void {
    this.subscribingCategoryIds = [...this.subscribedCategoryIds];
    this.subscribeCategoriesVisible = true;
  }

  closeSubscribeCategories(): void {
    this.subscribeCategoriesVisible = false;
  }

  onSubscribeCategorySelectionChanged(categoryIds: number[]): void {
    this.subscribingCategoryIds = categoryIds;
  }

  saveSubscribeCategories(): void {
    if (this.subscribing) return;
    this.subscribing = true;
    this.errorMessage = '';
    this.api.subscribeWatchlistToCategories(this.watchlistId, this.subscribingCategoryIds).subscribe({
      next: watchlist => {
        this.subscribing = false;
        this.watchlist = watchlist;
        this.subscribedCategoryIds = watchlist.subscribedCategoryIds;
        this.subscribeCategoriesVisible = false;
        this.loadMovies(1);
      },
      error: err => {
        this.subscribing = false;
        this.errorMessage = err?.error?.message ?? err?.message ?? 'Could not subscribe to the selected categories';
      }
    });
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
