import { CommonModule } from '@angular/common';
import { Component, EventEmitter, Input, Output, ViewChild, inject, OnInit } from '@angular/core';
import { Movie, MoviesApiService, OmdbMovieSearchResult, ParsedMovieSearch } from '../../services/movies-api';
import { MovieFilterSearchComponent, ExternalMovieAction } from '../movie-filter-search/movie-filter-search';
import { MoviePageNavigatorComponent } from '../movie-page-navigator/movie-page-navigator';
import { CategoryTreeDialogComponent } from '../category-tree-dialog/category-tree-dialog';

@Component({
  standalone: true,
  selector: 'app-movie-selector',
  imports: [CommonModule, MovieFilterSearchComponent, MoviePageNavigatorComponent, CategoryTreeDialogComponent],
  templateUrl: './movie-selector.html',
  styleUrl: './movie-selector.css'
})
export class MovieSelectorComponent implements OnInit {
  private readonly api = inject(MoviesApiService);
  @ViewChild(MovieFilterSearchComponent) filterSearch!: MovieFilterSearchComponent;
  @Input() categoryName?: string;
  @Output() moviesSelected = new EventEmitter<string[]>();
  @Output() closed = new EventEmitter<void>();

  filterText = '';
  activeFilter = '';
  activeYear = '';
  selectedCategoryIds: number[] = [];
  categoryDialogVisible = false;

  movies: Movie[] = [];
  loading = false;
  errorMessage = '';
  currentPage = 1;
  totalCount = 0;
  readonly pageSize = this.api.moviePageSize;

  selectedMovieIds = new Set<string>();
  selectedMovies: Movie[] = [];

  ngOnInit(): void {
    this.loadMovies(1);
  }

  loadMovies(page = this.currentPage): void {
    this.loading = true;
    this.errorMessage = '';
    this.api.listMovies(page, this.pageSize, this.activeFilter, this.activeYear, this.selectedCategoryIds).subscribe({
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

  // Fires on every check/uncheck (not just OK) -- same live-refresh pattern used by the other category dialogs,
  // so the movie list behind this picker updates immediately instead of only after closing it.
  onCategorySelectionChanged(categoryIds: number[]): void {
    this.selectedCategoryIds = categoryIds;
    this.loadMovies(1);
  }

  onCategoriesSelected(categoryIds: number[]): void {
    this.selectedCategoryIds = categoryIds;
    this.categoryDialogVisible = false;
    // Per spec: closing the category picker clears the rest of the filter (text/OMDb/external results) but keeps
    // the newly selected categories, and always resets to page 1.
    this.activeFilter = '';
    this.activeYear = '';
    this.filterSearch.clear();
    this.loadMovies(1);
  }

  onExternalAction(event: { action: ExternalMovieAction; movie: OmdbMovieSearchResult }): void {
    this.api.createMovieFromSearch(event.movie).subscribe({
      next: movie => {
        this.filterSearch.completeExternalAction();
        this.addSelected(movie);
      },
      error: error => this.filterSearch.failExternalAction(error)
    });
  }

  isSelected(movie: Movie): boolean {
    return this.selectedMovieIds.has(movie.imdbId);
  }

  toggleSelect(movie: Movie, checked: boolean): void {
    checked ? this.addSelected(movie) : this.removeSelected(movie.imdbId);
  }

  removeSelected(imdbId: string): void {
    this.selectedMovieIds.delete(imdbId);
    this.selectedMovies = this.selectedMovies.filter(movie => movie.imdbId !== imdbId);
  }

  submit(): void {
    this.moviesSelected.emit([...this.selectedMovieIds]);
  }

  cancel(): void {
    this.closed.emit();
  }

  private addSelected(movie: Movie): void {
    if (this.selectedMovieIds.has(movie.imdbId)) return;
    this.selectedMovieIds.add(movie.imdbId);
    this.selectedMovies.push(movie);
  }
}
