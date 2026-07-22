import { CommonModule } from '@angular/common';
import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { Subscription, combineLatest } from 'rxjs';
import { AuthService } from '../../services/auth';
import { Movie, MovieCategory, MoviesApiService } from '../../services/movies-api';
import { BackButtonComponent } from '../back-button/back-button';
import { CategoryTreeDialogComponent } from '../category-tree-dialog/category-tree-dialog';
import { MovieCategoryPathViewComponent } from '../movie-category-path-view/movie-category-path-view';
import { RankFormatPipe } from '../../pipes/rank-format.pipe';
import { RatingFormatPipe } from '../../pipes/rating-format.pipe';
import { ShowRatingRankPipe } from '../../pipes/show-rating-rank.pipe';

@Component({
  standalone: true,
  selector: 'app-movie-detail',
  imports: [
    CommonModule, ReactiveFormsModule, RouterLink, BackButtonComponent, CategoryTreeDialogComponent,
    MovieCategoryPathViewComponent, RankFormatPipe, RatingFormatPipe, ShowRatingRankPipe
  ],
  templateUrl: './movie-detail.html',
  styleUrl: './movie-detail.css'
})
export class MovieDetailComponent implements OnInit, OnDestroy {
  private readonly route = inject(ActivatedRoute);
  private readonly fb = inject(FormBuilder);
  private readonly moviesApi = inject(MoviesApiService);
  readonly auth = inject(AuthService);
  private pageSub?: Subscription;
  private movieSub?: Subscription;
  private categoriesSub?: Subscription;
  private imdbId = '';

  movie: Movie | null = null;
  categories: MovieCategory[] = [];
  categoriesLoading = false;
  categoriesErrorMessage = '';
  editCategoriesOpen = false;
  loading = false;
  saving = false;
  recommendationSaving = false;
  replaySaving = false;
  errorMessage = '';

  readonly commentForm = this.fb.group({
    text: ['', [Validators.required, Validators.maxLength(4000)]]
  });

  // Movie details are public (viewable by anonymous visitors); isAuthenticated$ stays in the trigger so that
  // signing in or out while already on the page reloads the movie/categories with the viewer's own
  // recommended/rating fields (or without them, on logout) rather than leaving stale data on screen.
  ngOnInit(): void {
    this.pageSub = combineLatest([this.route.paramMap, this.auth.isAuthenticated$]).subscribe(([params]) => {
      this.imdbId = params.get('imdbId') ?? '';
      if (!this.imdbId) {
        this.clearMovie();
      } else {
        this.loadMovie(this.imdbId);
      }
    });
  }

  ngOnDestroy(): void {
    this.pageSub?.unsubscribe();
    this.movieSub?.unsubscribe();
    this.categoriesSub?.unsubscribe();
  }

  loadMovie(imdbId: string): void {
    this.movieSub?.unsubscribe();
    this.categoriesSub?.unsubscribe();
    this.loading = true;
    this.errorMessage = '';
    this.movieSub = this.moviesApi.getMovie(imdbId).subscribe({
      next: movie => {
        if (this.imdbId !== imdbId) return;
        this.movie = movie;
        this.loading = false;
        this.loadCategories(imdbId);
      },
      error: err => {
        if (this.imdbId !== imdbId) return;
        this.errorMessage = err?.error?.message ?? err?.message ?? 'Could not load movie';
        this.loading = false;
      }
    });
  }

  loadCategories(imdbId: string): void {
    this.categoriesSub?.unsubscribe();
    this.categoriesLoading = true;
    this.categoriesErrorMessage = '';
    this.categoriesSub = this.moviesApi.getCategoryTree(imdbId).subscribe({
      next: categories => {
        if (this.imdbId !== imdbId) return;
        this.categories = categories;
        this.categoriesLoading = false;
      },
      error: err => {
        if (this.imdbId !== imdbId) return;
        this.categoriesErrorMessage = err?.error?.message ?? err?.message ?? 'Could not load categories';
        this.categoriesLoading = false;
      }
    });
  }

  hasCategoryPath(): boolean {
    return this.categories.some(category => category.checked || this.categoryHasCheckedDescendant(category));
  }

  openEditCategories(): void { this.editCategoriesOpen = true; }

  closeEditCategories(): void { this.editCategoriesOpen = false; }

  onCategoriesSaved(): void {
    this.editCategoriesOpen = false;
    if (this.movie) this.loadCategories(this.movie.imdbId);
  }

  private categoryHasCheckedDescendant(category: MovieCategory): boolean {
    return category.children.some(child => child.checked || this.categoryHasCheckedDescendant(child));
  }

  addComment(): void {
    if (!this.movie || this.commentForm.invalid) return;
    this.saving = true;
    const text = this.commentForm.getRawValue().text ?? '';
    this.moviesApi.addComment(this.movie.imdbId, text).subscribe({
      next: movie => {
        if (!this.auth.token) return;
        this.movie = movie;
        this.commentForm.reset();
        this.saving = false;
      },
      error: err => {
        if (!this.auth.token) return;
        this.errorMessage = err?.error?.message ?? err?.message ?? 'Could not save comment';
        this.saving = false;
      }
    });
  }

  likeMovie(): void {
    if (!this.movie || !this.auth.token || this.recommendationSaving) return;
    this.updateRecommendation(() => this.moviesApi.recommendMovie(this.movie!.imdbId));
  }

  dislikeMovie(): void {
    if (!this.movie || !this.auth.token || this.recommendationSaving) return;
    this.updateRecommendation(() => this.moviesApi.dislikeMovie(this.movie!.imdbId));
  }

  clearRecommendation(): void {
    if (!this.movie || !this.auth.token || this.recommendationSaving) return;
    this.updateRecommendation(() => this.moviesApi.unrecommendMovie(this.movie!.imdbId));
  }

  private updateRecommendation(requestFactory: () => ReturnType<MoviesApiService['recommendMovie']>): void {
    this.recommendationSaving = true;
    requestFactory().subscribe({
      next: movie => {
        if (!this.auth.token) return;
        this.movie = movie;
        this.recommendationSaving = false;
      },
      error: err => {
        if (!this.auth.token) return;
        this.errorMessage = err?.error?.message ?? err?.message ?? 'Could not update recommendation';
        this.recommendationSaving = false;
      }
    });
  }

  replayMovie(): void {
    if (!this.movie || !this.auth.token || this.replaySaving || this.recommendationSaving) return;

    this.replaySaving = true;
    this.errorMessage = '';
    this.moviesApi.replayMovie(this.movie.imdbId).subscribe({
      next: movie => {
        if (!this.auth.token) return;
        this.movie = movie;
        this.replaySaving = false;
      },
      error: err => {
        if (!this.auth.token) return;
        this.errorMessage = err?.error?.message ?? err?.message ?? 'Could not replay movie';
        this.replaySaving = false;
      }
    });
  }

  poster(movie: Movie): string {
    return movie.poster && movie.poster !== 'N/A' ? movie.poster : '/images/movie-poster.jpg';
  }

  avatar(seed: string): string {
    return `https://api.dicebear.com/6.x/avataaars/svg?seed=${encodeURIComponent(seed || 'user')}`;
  }

  private clearMovie(): void {
    this.movie = null;
    this.categories = [];
    this.categoriesLoading = false;
    this.categoriesErrorMessage = '';
    this.editCategoriesOpen = false;
    this.loading = false;
    this.saving = false;
    this.recommendationSaving = false;
    this.replaySaving = false;
    this.errorMessage = '';
    this.commentForm.reset();
  }
}
