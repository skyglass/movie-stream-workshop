import { CommonModule } from '@angular/common';
import { Component, OnInit, inject } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { Meta, Title } from '@angular/platform-browser';
import { AuthService } from '../../services/auth';
import { Movie, MovieCategory, MoviesApiService, ParsedMovieSearch } from '../../services/movies-api';
import { MovieFilterSearchComponent } from '../movie-filter-search/movie-filter-search';
import { MovieGridComponent } from '../movie-grid/movie-grid';

@Component({
  standalone: true,
  selector: 'app-movie-guide-detail',
  imports: [CommonModule, MovieFilterSearchComponent, MovieGridComponent],
  templateUrl: './movie-guide-detail.html',
  styleUrl: './movie-guide-detail.css'
})
export class MovieGuideDetailComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly api = inject(MoviesApiService);
  private readonly meta = inject(Meta);
  private readonly title = inject(Title);
  readonly auth = inject(AuthService);

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

  ngOnInit(): void {
    this.categoryId = Number(this.route.snapshot.paramMap.get('id'));
    this.activeCategories = [this.categoryId];
    this.defaultCategories = [this.categoryId];
    this.loadCategory();
    this.loadMovies(1);
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
    this.api.listMovies(page, this.pageSize, this.activeFilter, this.activeYear, this.activeCategories, this.activeOnlyNotRecommended).subscribe({
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
