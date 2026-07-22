import { CommonModule } from '@angular/common';
import { Component, OnInit, ViewChild, inject } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { Meta, Title } from '@angular/platform-browser';
import { Observable, map } from 'rxjs';
import { AuthService } from '../../services/auth';
import { Movie, MovieCategory, MoviesApiService, OmdbMovieSearchResult, ParsedMovieSearch } from '../../services/movies-api';
import { MovieFilterSearchComponent } from '../movie-filter-search/movie-filter-search';
import { MovieGridComponent } from '../movie-grid/movie-grid';
import { MovieSelectorComponent } from '../movie-selector/movie-selector';
import { CategoryTreeDialogComponent } from '../category-tree-dialog/category-tree-dialog';
import { ImportCsvDialogComponent } from '../import-csv-dialog/import-csv-dialog';
import { DeleteMoviesSelectorComponent } from '../delete-movies-selector/delete-movies-selector';
import { ShareDialogComponent } from '../share-dialog/share-dialog';
import { RankMoviesDialogComponent } from '../rank-movies-dialog/rank-movies-dialog';

@Component({
  standalone: true,
  selector: 'app-movie-guide-detail',
  imports: [CommonModule, RouterLink, MovieFilterSearchComponent, MovieGridComponent, MovieSelectorComponent,
    CategoryTreeDialogComponent, ImportCsvDialogComponent, DeleteMoviesSelectorComponent, ShareDialogComponent,
    RankMoviesDialogComponent],
  templateUrl: './movie-guide-detail.html',
  styleUrl: './movie-guide-detail.css'
})
export class MovieGuideDetailComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly api = inject(MoviesApiService);
  private readonly meta = inject(Meta);
  private readonly title = inject(Title);
  readonly auth = inject(AuthService);
  @ViewChild(MovieFilterSearchComponent) filterSearch!: MovieFilterSearchComponent;

  categoryId = 0;
  category: MovieCategory | null = null;
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
  activeCategories: number[] = [];
  activeOnlyNotRecommended = false;
  hasActiveFilter = false;
  // Fixed for the lifetime of the page (never reassigned) — this is what "Clear" restores, independent of
  // whatever activeCategories drifts to as the user checks/unchecks categories.
  defaultCategories: number[] = [];

  shareUrl = '';
  shareDialogVisible = false;
  entryType: 'Guide' | 'Personality' | null = null;
  movieGuideId: number | null = null;
  // Populated from the guide DTO itself (loadGuideInfo), not the category-tree walk that computes entryType --
  // this is what loadMovies() branches on, since it needs to be reliably known before the very first movie fetch
  // fires (see loadGuideInfo's own next/error handlers, which are what actually trigger that first loadMovies now).
  guideType: 'Guide' | 'Personality' | null = null;
  guideName = '';
  rankingUsername: string | null = null;
  isOwner = false;
  movieSelectorVisible = false;
  guideSubscribedCategoryIds: number[] = [];
  selectedCategory: number | null = null;
  categoryDialogVisible = false;
  csvDialogVisible = false;
  deleteMoviesSelectorVisible = false;
  rankMoviesDialogVisible = false;
  subscribeCategoriesVisible = false;
  subscribingCategoryIds: number[] = [];
  subscribing = false;
  descriptionDialogVisible = false;
  selectedCategoryDescriptionVisible = false;

  ngOnInit(): void {
    this.categoryId = Number(this.route.snapshot.paramMap.get('id'));
    this.activeCategories = [this.categoryId];
    this.defaultCategories = [this.categoryId];
    this.loadCategory();
    // loadMovies(1) is deliberately NOT called here: its sort depends on knowing movieGuideId/guideType first
    // (a Personality sorts by its own ranking), so it's triggered from loadGuideInfo()'s own response instead --
    // otherwise the very first paint would race ahead of that data and silently use the wrong sort.
    this.loadGuideInfo();
  }

  // Gates "Subscribe to Categories"/"Select Category" -- these are the controls that change the selection itself,
  // so unlike showAddMovies below they must stay visible no matter which category is currently selected;
  // otherwise selecting a subscribed category would hide the only way to pick a different one again.
  get canManageGuide(): boolean {
    if (!this.movieGuideId) return false;
    return this.isOwner || this.auth.canEditMovies;
  }

  // "Select Category" stays visible whenever there's something to select (any viewer can browse an existing
  // tree) or whenever the viewer could populate an empty one (owner, or MOVIES_GUIDE/MOVIES_ADMIN) -- it only
  // hides for a non-owner, non-privileged viewer facing an empty "No categories yet." tree, where clicking it
  // would open a dialog with nothing to do.
  get showSelectCategory(): boolean {
    return this.canManageGuide || !!this.category?.children?.length;
  }

  // "Rank Movies as Personality" -- Personality-only, and only for someone who could actually submit a ranking
  // (owner/MOVIES_GUIDE/MOVIES_ADMIN, same as canManageGuide). Shown even for an empty personality (opens to an
  // empty state with Submit disabled) rather than hidden, matching showSelectCategory's own reasoning.
  get showRankMovies(): boolean {
    return this.entryType === 'Personality' && this.canManageGuide;
  }

  get showAddMovies(): boolean {
    if (!this.canManageGuide) return false;
    // Subscribed/default categories are read-only references to a category that lives (and is managed)
    // elsewhere — "Add Movies"/"Import from CSV" are hidden for absolutely everyone (owner, non-owner,
    // MOVIES_GUIDE, MOVIES_ADMIN) while one is selected, no exceptions.
    const targetingSubscribedCategory = this.selectedCategory != null
      && this.guideSubscribedCategoryIds.includes(this.selectedCategory);
    return !targetingSubscribedCategory;
  }

  openMovieSelector(): void {
    this.movieSelectorVisible = true;
  }

  onMoviesSelected(imdbIds: string[]): void {
    this.movieSelectorVisible = false;
    if (!this.movieGuideId || imdbIds.length === 0) return;
    this.errorMessage = '';
    this.api.assignMoviesToGuide(this.movieGuideId, imdbIds, this.selectedCategory).subscribe({
      next: () => this.loadMovies(1),
      error: err => {
        this.errorMessage = err?.error?.message ?? err?.message ?? 'Could not add the selected movies';
      }
    });
  }

  // The inline "Search OMDb" box's own "Add to this Guide" button (allowExternalActions=false + externalActions
  // = ['addToCategory'] on the filter below) -- unlike the generic 'add' action (create in the catalog only),
  // this must also assign the movie to the guide, so it goes through the same two-step, properly-authorized path
  // as onMoviesSelected above (create-in-catalog, then assignMoviesToGuide) rather than the weaker, unauthenticated
  // -by-ownership CategoryService.addMovieFromSearchToCategory endpoint.
  onExternalAddToGuide(event: { movie: OmdbMovieSearchResult }): void {
    if (!this.movieGuideId) return;
    this.errorMessage = '';
    this.api.createMovieFromSearch(event.movie).subscribe({
      next: created => {
        this.api.assignMoviesToGuide(this.movieGuideId!, [created.imdbId], this.selectedCategory).subscribe({
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
  }

  // Fires on every check/uncheck (not just on OK) -- same live-refresh pattern as the Filter "Select Categories"
  // dialog, so the movie list behind the dialog updates immediately instead of only after closing it.
  onCategorySelectionChanged(categoryIds: number[]): void {
    this.selectedCategory = categoryIds.length ? categoryIds[0] : null;
    // Unchecking without picking another category clears selectedCategory back to empty, which loadMovies()
    // already treats as "no selectedCategory filter applied" — falls back to the regular activeCategories scope.
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
    // filterSearch.clear() above synchronously triggers a fresh loadMovies(1), whose own error handling would
    // otherwise leave errorMessage blank on a successful reload — set it after, so the failure is still visible.
    this.errorMessage = message;
  }

  openDeleteMovies(): void {
    this.deleteMoviesSelectorVisible = true;
  }

  closeDeleteMovies(): void {
    this.deleteMoviesSelectorVisible = false;
  }

  // The Delete Movies dialog refreshes its own list after every deletion -- this just keeps the guide page's own
  // "Movie Results" grid behind it in sync too, same as after a CSV import.
  onMoviesDeleted(): void {
    this.refreshAfterImport();
  }

  // Resets filter text, the "Search OMDb" checkbox, and External Results, and returns the category scope to the
  // guide's own default -- but never touches selectedCategory (this component's own state, independent of
  // movie-filter-search), so loadMovies() keeps scoping to it when it's set.
  private refreshAfterImport(): void {
    this.filterSearch.clear();
    // filterSearch.clear() only reloads via applyFilter()'s own change-detection guard, which silently no-ops
    // when the filter was already at its default state (e.g. importing right after opening the page, without
    // ever touching the filter first) -- an import always changes the underlying movies, never just the filter,
    // so reload unconditionally here regardless of whether clear() already triggered one.
    this.loadMovies(1);
  }

  openDescriptionDialog(): void {
    this.descriptionDialogVisible = true;
  }

  closeDescriptionDialog(): void {
    this.descriptionDialogVisible = false;
  }

  // Ancestor chain from the guide's own direct children down to the selected sub-category (inclusive) -- e.g.
  // "Guides -> Heist Movies -> Genres -> Drama" becomes just ["Genres", "Drama"], relative to the guide itself.
  get selectedCategoryPath(): MovieCategory[] {
    if (this.selectedCategory == null || !this.category) return [];
    return this.findCategoryPath(this.category.children, this.selectedCategory) ?? [];
  }

  get selectedCategoryLeaf(): MovieCategory | null {
    const path = this.selectedCategoryPath;
    return path.length ? path[path.length - 1] : null;
  }

  openSelectedCategoryDescription(): void {
    this.selectedCategoryDescriptionVisible = true;
  }

  closeSelectedCategoryDescription(): void {
    this.selectedCategoryDescriptionVisible = false;
  }

  private findCategoryPath(categories: MovieCategory[], targetId: number): MovieCategory[] | null {
    for (const category of categories) {
      if (category.id === targetId) return [category];
      const found = this.findCategoryPath(category.children, targetId);
      if (found) return [category, ...found];
    }
    return null;
  }

  openSubscribeCategories(): void {
    this.subscribingCategoryIds = [...this.guideSubscribedCategoryIds];
    this.subscribeCategoriesVisible = true;
  }

  closeSubscribeCategories(): void {
    this.subscribeCategoriesVisible = false;
  }

  onSubscribeCategorySelectionChanged(categoryIds: number[]): void {
    this.subscribingCategoryIds = categoryIds;
  }

  saveSubscribeCategories(): void {
    if (!this.movieGuideId || this.subscribing) return;
    this.subscribing = true;
    this.errorMessage = '';
    this.api.subscribeGuideToCategories(this.movieGuideId, this.subscribingCategoryIds).subscribe({
      next: guide => {
        this.subscribing = false;
        this.guideSubscribedCategoryIds = guide.subscribedCategoryIds;
        this.subscribeCategoriesVisible = false;
        this.loadMovies(1);
      },
      error: err => {
        this.subscribing = false;
        this.errorMessage = err?.error?.message ?? err?.message ?? 'Could not subscribe to the selected categories';
      }
    });
  }

  private loadGuideInfo(): void {
    this.api.getMovieGuideByCategory(this.categoryId).subscribe({
      next: guide => {
        this.movieGuideId = guide.id;
        this.guideType = guide.type;
        this.guideName = guide.name;
        this.rankingUsername = guide.rankingUsername;
        this.isOwner = guide.owner === this.auth.currentUser?.username;
        this.guideSubscribedCategoryIds = guide.subscribedCategoryIds;
        this.loadMovies(1);
      },
      // 404 (not a movie_guide-backed entry) or any other failure just means: show the normal page, with the
      // regular (non-personality) sort.
      error: () => this.loadMovies(1)
    });
  }

  loadCategory(): void {
    this.loading = true;
    this.api.getCategoryTree().subscribe({
      next: categories => {
        this.category = this.findCategory(categories, this.categoryId) ?? null;
        this.entryType = this.findEntryType(categories);
        this.loading = false;
        this.applySeoMetadata();
      },
      error: err => {
        this.errorMessage = err?.error?.message ?? err?.message ?? 'Could not load this Movie Guide';
        this.loading = false;
      }
    });
  }

  loadMovies(page = this.currentPage): void {
    this.moviesLoading = true;
    this.errorMessage = '';
    // The owner's sub-category pick overrides the regular filter's category scope while it's set; once it's
    // cleared (unchecked with nothing else selected), this naturally falls back to activeCategories.
    const categories = this.selectedCategory != null ? [this.selectedCategory] : this.activeCategories;
    // A Personality sorts by its own ranking (personality_movie_rank) first, falling back to the regular
    // popularity order for anything unranked -- Guides (and pages where guide info hasn't resolved) keep the
    // plain popularity sort.
    const request = (this.movieGuideId != null && this.guideType === 'Personality')
      ? this.api.listPersonalityMovies(this.movieGuideId, page, this.pageSize, this.activeFilter, this.activeYear, categories, this.activeOnlyNotRecommended)
      : this.api.listMovies(page, this.pageSize, this.activeFilter, this.activeYear, categories, this.activeOnlyNotRecommended);
    request.subscribe({
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
    // Unchecking this Guide/Personality's own category (and every other one) is a deliberate, allowed choice —
    // only the "Clear" button restores the default [categoryId] selection (see initialSelectedCategories below).
    const categories = search.selectedCategories ?? [];
    const onlyNotRecommended = search.onlyNotRecommended ?? false;
    if (search.keyword === this.activeFilter && search.year === this.activeYear
      && categories.join() === this.activeCategories.join() && onlyNotRecommended === this.activeOnlyNotRecommended) return;
    this.activeFilter = search.keyword;
    this.activeYear = search.year;
    this.activeCategories = categories;
    this.activeOnlyNotRecommended = onlyNotRecommended;
    this.hasActiveFilter = search.hasActiveFilter ?? false;
    this.loadMovies(1);
  }

  emptyMessage(): string {
    return this.hasActiveFilter ? 'No movies found' : 'No movies in this Movie Guide yet';
  }

  share(): void {
    this.shareUrl = window.location.href;
    this.shareDialogVisible = true;
  }

  closeShareDialog(): void {
    this.shareDialogVisible = false;
  }

  // Fetches up to maxMovies movies in the exact order shown on-screen for the current filter/category
  // selection, without touching this component's own movies/currentPage/totalCount state -- passed into
  // ShareDialogComponent's "Download Poster Collage" and "Download CSV file" features.
  fetchOrderedMovies = (maxMovies: number): Observable<Movie[]> => {
    const categories = this.selectedCategory != null ? [this.selectedCategory] : this.activeCategories;
    const request = (this.movieGuideId != null && this.guideType === 'Personality')
      ? this.api.listPersonalityMovies(this.movieGuideId, 1, maxMovies, this.activeFilter, this.activeYear, categories, this.activeOnlyNotRecommended)
      : this.api.listMovies(1, maxMovies, this.activeFilter, this.activeYear, categories, this.activeOnlyNotRecommended);
    return request.pipe(map(page => page.movies));
  };

  openRankMovies(): void {
    this.rankMoviesDialogVisible = true;
  }

  closeRankMovies(): void {
    this.rankMoviesDialogVisible = false;
  }

  // The dialog already persisted the ranking server-side; refresh the guide info (to pick up a newly-allocated
  // rankingUsername on the very first submit) and the movie grid (to reflect the new sort) behind it.
  onRankingSubmitted(): void {
    this.rankMoviesDialogVisible = false;
    this.loadGuideInfo();
  }

  private findEntryType(categories: MovieCategory[]): 'Guide' | 'Personality' | null {
    const guides = categories.find(c => c.name === 'Guides');
    const personalities = categories.find(c => c.name === 'Personalities');
    if (guides && this.findCategory(guides.children, this.categoryId)) return 'Guide';
    if (personalities && this.findCategory(personalities.children, this.categoryId)) return 'Personality';
    return null;
  }

  private findCategory(categories: MovieCategory[], id: number): MovieCategory | undefined {
    for (const category of categories) {
      if (category.id === id) return category;
      const found = this.findCategory(category.children, id);
      if (found) return found;
    }
    return undefined;
  }

  private applySeoMetadata(): void {
    const name = this.category?.name ?? 'Movie Guide';
    const pageTitle = `${name} | Movie Challenge`;
    const description = this.category?.description || `Browse the "${name}" Movie Guide on Movie Challenge.`;
    this.title.setTitle(pageTitle);
    this.meta.updateTag({ name: 'description', content: description });
    this.meta.updateTag({ name: 'robots', content: 'index, follow' });
    this.meta.updateTag({ property: 'og:title', content: pageTitle });
    this.meta.updateTag({ property: 'og:description', content: description });
  }
}
