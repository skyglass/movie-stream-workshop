import { CommonModule } from '@angular/common';
import { Component, OnInit, ViewChild, inject } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { Meta, Title } from '@angular/platform-browser';
import { AuthService } from '../../services/auth';
import { Movie, MovieCategory, MoviesApiService, ParsedMovieSearch } from '../../services/movies-api';
import { MovieFilterSearchComponent } from '../movie-filter-search/movie-filter-search';
import { MovieGridComponent } from '../movie-grid/movie-grid';
import { CreateGuideWizardComponent, GuideWizardResume } from '../create-guide-wizard/create-guide-wizard';
import { MovieSelectorComponent } from '../movie-selector/movie-selector';
import { CategoryTreeDialogComponent } from '../category-tree-dialog/category-tree-dialog';
import { ImportCsvDialogComponent } from '../import-csv-dialog/import-csv-dialog';

@Component({
  standalone: true,
  selector: 'app-movie-guide-detail',
  imports: [CommonModule, MovieFilterSearchComponent, MovieGridComponent, CreateGuideWizardComponent, MovieSelectorComponent,
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
  resumeWizard: GuideWizardResume | null = null;
  wizardType: 'Guide' | 'Personality' = 'Guide';
  movieGuideId: number | null = null;
  isOwner = false;
  movieSelectorVisible = false;
  guideSubscribedCategoryIds: number[] = [];
  selectedCategory: number | null = null;
  categoryDialogVisible = false;
  csvDialogVisible = false;

  ngOnInit(): void {
    this.categoryId = Number(this.route.snapshot.paramMap.get('id'));
    this.activeCategories = [this.categoryId];
    this.defaultCategories = [this.categoryId];
    this.loadCategory();
    this.loadMovies(1);
    this.checkWizardResume();
  }

  get showAddMovies(): boolean {
    if (!this.movieGuideId) return false;
    // A plain owner may add movies to the guide's own category or a native sub-category, but never directly to
    // one of the guide's default/subscribed categories — those are read-only references to a category that
    // lives (and is managed) elsewhere. Only MOVIES_GUIDE/MOVIES_ADMIN can bypass that (matches the backend's
    // resolveAssignmentTarget check).
    const targetingDefaultCategory = this.selectedCategory != null
      && this.guideSubscribedCategoryIds.includes(this.selectedCategory);
    return targetingDefaultCategory ? this.auth.canEditMovies : (this.isOwner || this.auth.canEditMovies);
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

  onCategorySelected(categoryIds: number[]): void {
    this.selectedCategory = categoryIds.length ? categoryIds[0] : null;
    this.categoryDialogVisible = false;
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

  onWizardCompleted(): void {
    this.resumeWizard = null;
    this.loadCategory();
    this.loadMovies(1);
    this.checkWizardResume();
  }

  private checkWizardResume(): void {
    this.api.getMovieGuideByCategory(this.categoryId).subscribe({
      next: guide => {
        this.movieGuideId = guide.id;
        this.isOwner = guide.owner === this.auth.currentUser?.username;
        this.guideSubscribedCategoryIds = guide.subscribedCategoryIds;
        if (this.isOwner && guide.status === 'STARTED') {
          this.wizardType = guide.type;
          this.resumeWizard = { movieGuideId: guide.id, categoryId: guide.categoryId };
        }
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
