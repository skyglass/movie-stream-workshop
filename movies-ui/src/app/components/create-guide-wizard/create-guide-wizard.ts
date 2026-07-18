import { CommonModule } from '@angular/common';
import { Component, EventEmitter, Input, OnInit, Output, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Movie, MoviesApiService, ParsedMovieSearch } from '../../services/movies-api';
import { CategoryTreeDialogComponent } from '../category-tree-dialog/category-tree-dialog';
import { MovieFilterSearchComponent } from '../movie-filter-search/movie-filter-search';
import { MoviePageNavigatorComponent } from '../movie-page-navigator/movie-page-navigator';
import { MovieSelectorComponent } from '../movie-selector/movie-selector';

export interface GuideWizardResume {
  movieGuideId: number;
  categoryId: number;
}

@Component({
  standalone: true,
  selector: 'app-create-guide-wizard',
  imports: [CommonModule, FormsModule, CategoryTreeDialogComponent, MovieFilterSearchComponent, MoviePageNavigatorComponent, MovieSelectorComponent],
  templateUrl: './create-guide-wizard.html',
  styleUrl: './create-guide-wizard.css'
})
export class CreateGuideWizardComponent implements OnInit {
  private readonly api = inject(MoviesApiService);

  @Input() initialType: 'Guide' | 'Personality' = 'Guide';
  @Input() resume?: GuideWizardResume;
  @Output() closed = new EventEmitter<void>();
  @Output() guideCreated = new EventEmitter<number>();
  @Output() completed = new EventEmitter<void>();

  // Step 1 (creation) state
  name = '';
  description = '';
  icon = '';
  subscribedCategoryIds: number[] = [];
  saving = false;
  errorMessage = '';

  // Step 2 (movie assignment) state
  movieGuideId: number | null = null;
  guideSubscribedCategoryIds: number[] = [];
  selectedCategory: number | null = null;
  categoryDialogVisible = false;
  completing = false;
  filterText = '';
  activeFilter = '';
  activeYear = '';
  movies: Movie[] = [];
  moviesLoading = false;
  currentPage = 1;
  totalCount = 0;
  readonly pageSize = this.api.moviePageSize;
  movieSelectorVisible = false;

  get step(): 1 | 2 {
    return this.resume ? 2 : 1;
  }

  ngOnInit(): void {
    if (this.resume) {
      this.movieGuideId = this.resume.movieGuideId;
      this.api.getMovieGuideByCategory(this.resume.categoryId).subscribe({
        next: guide => { this.guideSubscribedCategoryIds = guide.subscribedCategoryIds; },
        error: () => {}
      });
      this.loadStep2Movies(1);
    }
  }

  onCategorySelectionChanged(categoryIds: number[]): void {
    this.subscribedCategoryIds = categoryIds;
  }

  skip(): void {
    this.subscribedCategoryIds = [];
    this.create();
  }

  create(): void {
    if (!this.name.trim()) {
      this.errorMessage = 'Name is required.';
      return;
    }
    this.saving = true;
    this.errorMessage = '';
    this.api.createGuide(this.initialType, this.name.trim(), this.description.trim(), this.icon.trim(),
      this.subscribedCategoryIds).subscribe({
      next: guide => {
        this.saving = false;
        this.guideCreated.emit(guide.categoryId);
      },
      error: error => {
        this.saving = false;
        this.errorMessage = error?.error?.message ?? error?.message ?? 'Could not create the guide';
      }
    });
  }

  cancel(): void {
    this.closed.emit();
  }

  onInternalSearch(search: ParsedMovieSearch): void {
    if (search.keyword === this.activeFilter && search.year === this.activeYear) return;
    this.activeFilter = search.keyword;
    this.activeYear = search.year;
    this.loadStep2Movies(1);
  }

  openCategoryDialog(): void {
    this.categoryDialogVisible = true;
  }

  closeCategoryDialog(): void {
    this.categoryDialogVisible = false;
  }

  // Picking a sub-category here scopes both the displayed list and where newly added movies get assigned; an
  // empty selection means "the guide's own top-level category" (the default).
  onCategorySelected(categoryIds: number[]): void {
    this.selectedCategory = categoryIds.length ? categoryIds[0] : null;
    this.categoryDialogVisible = false;
    this.activeFilter = '';
    this.activeYear = '';
    this.loadStep2Movies(1);
  }

  loadStep2Movies(page = this.currentPage): void {
    if (!this.movieGuideId) return;
    this.moviesLoading = true;
    this.errorMessage = '';
    const request = this.selectedCategory != null
      ? this.api.listMovies(page, this.pageSize, this.activeFilter, this.activeYear, [this.selectedCategory])
      : this.api.listGuideMovies(this.movieGuideId, page, this.pageSize, this.activeFilter, this.activeYear);
    request.subscribe({
      next: moviePage => {
        this.movies = moviePage.movies;
        this.totalCount = moviePage.totalCount;
        this.currentPage = page;
        this.moviesLoading = false;
      },
      error: error => {
        this.errorMessage = error?.error?.message ?? error?.message ?? 'Could not load movies';
        this.moviesLoading = false;
      }
    });
  }

  openMovieSelector(): void {
    this.movieSelectorVisible = true;
  }

  onMoviesSelected(imdbIds: string[]): void {
    this.movieSelectorVisible = false;
    if (!this.movieGuideId || imdbIds.length === 0) return;
    this.errorMessage = '';
    this.api.assignMoviesToGuide(this.movieGuideId, imdbIds, this.selectedCategory).subscribe({
      next: () => this.loadStep2Movies(1),
      error: error => {
        this.errorMessage = error?.error?.message ?? error?.message ?? 'Could not add the selected movies';
      }
    });
  }

  complete(): void {
    if (!this.movieGuideId) return;
    this.completing = true;
    this.errorMessage = '';
    this.api.completeGuide(this.movieGuideId).subscribe({
      next: () => {
        this.completing = false;
        this.completed.emit();
      },
      error: error => {
        this.completing = false;
        this.errorMessage = error?.error?.message ?? error?.message ?? 'Could not complete the guide';
      }
    });
  }
}
