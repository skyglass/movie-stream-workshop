import { CommonModule } from '@angular/common';
import { Component, OnDestroy, OnInit, ViewChild, inject } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { Subscription } from 'rxjs';
import { Movie, MovieCategory, MoviesApiService, ParsedMovieSearch } from '../../services/movies-api';
import { AuthService } from '../../services/auth';
import { MovieFilterSearchComponent } from '../movie-filter-search/movie-filter-search';
import { MovieGridComponent } from '../movie-grid/movie-grid';
import { MovieSelectorComponent } from '../movie-selector/movie-selector';
import { CategoryTreeNavComponent } from '../category-tree-nav/category-tree-nav';
import { categoryPageSegments, findCategoryPath } from '../../utils/category-path';

// Browse movies scoped to one category, picked from a persistent (not modal) tree on the left -- reached via a
// friendly URL (/categories/:id/Parent/Child/...) built with categoryPageSegments, or /categories/root when
// nothing is selected. The tree (CategoryTreeNavComponent) is plain routerLinks, not a picker -- navigation
// happens natively through them, so this component only needs to react to the resulting route change and derive
// its own breadcrumb path from the same tree data (via the tree's categoriesLoaded output, avoiding a second fetch).
@Component({
  standalone: true,
  selector: 'app-movie-category-page',
  imports: [CommonModule, RouterLink, MovieFilterSearchComponent, MovieGridComponent, MovieSelectorComponent, CategoryTreeNavComponent],
  templateUrl: './movie-category-page.html',
  styleUrl: './movie-category-page.css'
})
export class MovieCategoryPageComponent implements OnInit, OnDestroy {
  private readonly route = inject(ActivatedRoute);
  private readonly api = inject(MoviesApiService);
  readonly auth = inject(AuthService);
  private paramsSub?: Subscription;
  private categories: MovieCategory[] = [];
  // categoryId starts out null, same as the "id absent" (root) case -- comparing requestedId against categoryId
  // directly would skip the very first load when landing on /categories/root (null === null), so this tracks
  // whether a load has actually happened yet, independent of what categoryId happens to equal.
  private hasLoadedOnce = false;
  @ViewChild(MovieFilterSearchComponent) filterSearch!: MovieFilterSearchComponent;

  categoryId: number | null = null;
  selectedCategoryIds: number[] = [];
  categoryPath: MovieCategory[] = [];
  movies: Movie[] = [];
  moviesLoading = false;
  errorMessage = '';
  currentPage = 1;
  totalCount = 0;
  readonly pageSize = this.api.moviePageSize;
  filterText = '';
  activeFilter = '';
  activeYear = '';
  activeOnlyNotRecommended = false;
  hasActiveFilter = false;
  movieSelectorVisible = false;

  ngOnInit(): void {
    this.paramsSub = this.route.paramMap.subscribe(params => {
      const idParam = params.get('id');
      const requestedId = idParam == null ? null : Number(idParam);
      if (this.hasLoadedOnce && this.categoryId === requestedId) return;
      this.hasLoadedOnce = true;
      this.categoryId = requestedId;
      this.selectedCategoryIds = requestedId != null ? [requestedId] : [];
      this.updateCategoryPath();
      // A category switch (including back to "All Movies") starts from a clean filter rather than carrying over
      // whatever was typed for the previous category -- filterSearch is unset on this very first firing (view not
      // initialized yet), which is fine since there's nothing to clear at that point anyway.
      this.filterSearch?.clear();
      this.loadMovies(1);
    });
  }

  ngOnDestroy(): void {
    this.paramsSub?.unsubscribe();
  }

  get selectedCategory(): MovieCategory | null {
    return this.categoryPath.length ? this.categoryPath[this.categoryPath.length - 1] : null;
  }

  // Always visible now; disabled whenever there's no real category to add to -- either nothing is selected at all
  // (categoryPath empty -> selectedCategory null -> parentId reads as undefined) or the selected category is
  // itself a root (parentId null, a plain container for sub-categories, never a target for movies). `== null`
  // catches both undefined and null in one comparison.
  get addMoviesDisabled(): boolean {
    return this.selectedCategory?.parentId == null;
  }

  get addMoviesTitle(): string {
    return this.addMoviesDisabled ? 'Please select Category' : 'Add Movies';
  }

  categoryPageSegments(path: MovieCategory[]): (string | number)[] {
    return categoryPageSegments(path);
  }

  // The sidebar tree's own fetch, shared here purely to derive the breadcrumb path -- avoids a second GET for the
  // same data. Fires once when the tree first loads; re-derives the path immediately in case the route's category
  // id was already known before the tree data arrived.
  onCategoriesLoaded(categories: MovieCategory[]): void {
    this.categories = categories;
    this.updateCategoryPath();
  }

  private updateCategoryPath(): void {
    this.categoryPath = this.categoryId != null ? findCategoryPath(this.categories, this.categoryId) ?? [] : [];
  }

  openMovieSelector(): void {
    if (this.addMoviesDisabled) return;
    this.movieSelectorVisible = true;
  }

  onMoviesSelected(imdbIds: string[]): void {
    this.movieSelectorVisible = false;
    if (this.categoryId == null || imdbIds.length === 0) return;
    this.errorMessage = '';
    this.api.addMoviesToCategory(this.categoryId, imdbIds).subscribe({
      next: () => this.loadMovies(1),
      error: err => {
        this.errorMessage = err?.error?.message ?? err?.message ?? 'Could not add the selected movies';
      }
    });
  }

  loadMovies(page = this.currentPage): void {
    this.moviesLoading = true;
    this.errorMessage = '';
    this.api.listMovies(page, this.pageSize, this.activeFilter, this.activeYear, this.selectedCategoryIds, this.activeOnlyNotRecommended)
      .subscribe({
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
    const onlyNotRecommended = search.onlyNotRecommended ?? false;
    if (search.keyword === this.activeFilter && search.year === this.activeYear && onlyNotRecommended === this.activeOnlyNotRecommended) return;
    this.activeFilter = search.keyword;
    this.activeYear = search.year;
    this.activeOnlyNotRecommended = onlyNotRecommended;
    this.hasActiveFilter = search.hasActiveFilter ?? false;
    this.loadMovies(1);
  }

  emptyMessage(): string {
    return this.hasActiveFilter ? 'No movies found' : 'No movies in this category yet';
  }
}
