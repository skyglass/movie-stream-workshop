import { CommonModule } from '@angular/common';
import { Component, OnInit, ViewChild, inject } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { Meta, Title } from '@angular/platform-browser';
import { AuthService } from '../../services/auth';
import { Movie, MovieCategory, MoviesApiService, ParsedMovieSearch } from '../../services/movies-api';
import { MovieFilterSearchComponent } from '../movie-filter-search/movie-filter-search';
import { MovieGridComponent } from '../movie-grid/movie-grid';
import { MovieSelectorComponent } from '../movie-selector/movie-selector';
import { CategoryTreeDialogComponent } from '../category-tree-dialog/category-tree-dialog';
import { ImportCsvDialogComponent } from '../import-csv-dialog/import-csv-dialog';

@Component({
  standalone: true,
  selector: 'app-movie-guide-detail',
  imports: [CommonModule, MovieFilterSearchComponent, MovieGridComponent, MovieSelectorComponent,
    CategoryTreeDialogComponent, ImportCsvDialogComponent],
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
  // Fixed for the lifetime of the page (never reassigned) — this is what "Clear" restores, independent of
  // whatever activeCategories drifts to as the user checks/unchecks categories.
  defaultCategories: number[] = [];

  shareUrl = '';
  shareDetailsVisible = false;
  copiedShareUrl = false;
  entryType: 'Guide' | 'Personality' | null = null;
  movieGuideId: number | null = null;
  isOwner = false;
  movieSelectorVisible = false;
  guideSubscribedCategoryIds: number[] = [];
  selectedCategory: number | null = null;
  categoryDialogVisible = false;
  csvDialogVisible = false;
  subscribeCategoriesVisible = false;
  subscribingCategoryIds: number[] = [];
  subscribing = false;

  ngOnInit(): void {
    this.categoryId = Number(this.route.snapshot.paramMap.get('id'));
    this.activeCategories = [this.categoryId];
    this.defaultCategories = [this.categoryId];
    this.loadCategory();
    this.loadMovies(1);
    this.loadGuideInfo();
  }

  get showAddMovies(): boolean {
    if (!this.movieGuideId) return false;
    // Subscribed/default categories are read-only references to a category that lives (and is managed)
    // elsewhere — "Add Movies"/"Import from CSV" are hidden for absolutely everyone (owner, non-owner,
    // MOVIES_GUIDE, MOVIES_ADMIN) while one is selected, no exceptions.
    const targetingSubscribedCategory = this.selectedCategory != null
      && this.guideSubscribedCategoryIds.includes(this.selectedCategory);
    if (targetingSubscribedCategory) return false;
    // Otherwise (no sub-category selected, or a native one): the guide's owner, MOVIES_GUIDE, or MOVIES_ADMIN
    // may add movies.
    return this.isOwner || this.auth.canEditMovies;
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

  // Resets filter text, the "Search OMDb" checkbox, and External Results, and returns the category scope to the
  // guide's own default -- but never touches selectedCategory (this component's own state, independent of
  // movie-filter-search), so loadMovies() keeps scoping to it when it's set.
  private refreshAfterImport(): void {
    this.filterSearch.clear();
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
        this.isOwner = guide.owner === this.auth.currentUser?.username;
        this.guideSubscribedCategoryIds = guide.subscribedCategoryIds;
      },
      // 404 (not a movie_guide-backed entry) or any other failure just means: show the normal page.
      error: () => {}
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
    this.api.listMovies(page, this.pageSize, this.activeFilter, this.activeYear, categories, this.activeOnlyNotRecommended).subscribe({
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
    this.loadMovies(1);
  }

  share(): void {
    this.copiedShareUrl = false;
    this.shareUrl = window.location.href;
    this.shareDetailsVisible = true;
  }

  closeShareDetails(): void {
    this.shareDetailsVisible = false;
    this.shareUrl = '';
    this.copiedShareUrl = false;
  }

  async copyShareUrl(): Promise<void> {
    if (!this.shareUrl) return;
    try {
      await navigator.clipboard.writeText(this.shareUrl);
    } catch {
      this.copyShareUrlWithFallback();
    }
    this.copiedShareUrl = true;
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

  private copyShareUrlWithFallback(): void {
    const textarea = document.createElement('textarea');
    textarea.value = this.shareUrl;
    textarea.setAttribute('readonly', '');
    textarea.style.position = 'fixed';
    textarea.style.left = '-9999px';
    document.body.appendChild(textarea);
    textarea.select();
    document.execCommand('copy');
    document.body.removeChild(textarea);
  }
}
