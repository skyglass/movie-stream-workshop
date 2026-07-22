import { CommonModule } from '@angular/common';
import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { Subscription, combineLatest } from 'rxjs';
import { AuthService } from '../../services/auth';
import { Movie, MovieGuideDto, MoviesApiService, ParsedMovieSearch } from '../../services/movies-api';
import { BackButtonComponent } from '../back-button/back-button';
import { MoviePageNavigatorComponent } from '../movie-page-navigator/movie-page-navigator';
import { MovieFilterSearchComponent } from '../movie-filter-search/movie-filter-search';
import { CategoryTreeDialogComponent } from '../category-tree-dialog/category-tree-dialog';
import { RankFormatPipe } from '../../pipes/rank-format.pipe';
import { RatingFormatPipe } from '../../pipes/rating-format.pipe';

// Reachable from the "Recommend Similar Movies" footer link on a Movie Guide/Personality page. Public, same
// personalization rule as SimilarMoviesComponent (Movie Details' own "Similar Movies"): categories touched by
// the guide's own movies get scored against the signed-in viewer's own rating history when there is one, or
// against catalog-wide averages for an anonymous visitor -- see MoviesApiService.listSimilarToGuideMovies. The
// seed itself (which categories are even considered) is always every movie already in the guide, regardless of
// viewer -- that's the one thing generalized here versus the single-seed-movie variant.
@Component({
  standalone: true,
  selector: 'app-guide-similar-movies',
  imports: [
    CommonModule, RouterLink, BackButtonComponent, MoviePageNavigatorComponent, MovieFilterSearchComponent,
    CategoryTreeDialogComponent, RankFormatPipe, RatingFormatPipe
  ],
  templateUrl: './guide-similar-movies.html',
  styleUrl: './guide-similar-movies.css'
})
export class GuideSimilarMoviesComponent implements OnInit, OnDestroy {
  private readonly route = inject(ActivatedRoute);
  private readonly moviesApi = inject(MoviesApiService);
  readonly auth = inject(AuthService);
  private pageSub?: Subscription;
  private categoryId = 0;

  guide: MovieGuideDto | null = null;
  movies: Movie[] = [];
  loading = false;
  errorMessage = '';
  currentPage = 1;
  totalCount = 0;
  readonly pageSize = this.moviesApi.moviePageSize;
  recommendationBusy: Record<string, boolean> = {};
  filterText = '';
  activeFilter = '';
  activeYear = '';
  activeCategories: number[] = [];
  hasActiveFilter = false;
  categoryMovie: Movie | null = null;

  ngOnInit(): void {
    this.pageSub = combineLatest([this.route.paramMap, this.auth.isAuthenticated$]).subscribe(([params]) => {
      this.categoryId = Number(params.get('id'));
      this.resetState();
      if (this.categoryId) {
        this.loadGuide();
      }
    });
  }

  ngOnDestroy(): void {
    this.pageSub?.unsubscribe();
  }

  loadGuide(): void {
    this.moviesApi.getMovieGuideByCategory(this.categoryId).subscribe({
      next: guide => {
        this.guide = guide;
        this.loadSimilarMovies(1);
      },
      error: err => {
        this.errorMessage = err?.error?.message ?? err?.message ?? 'Could not load this Movie Guide';
      }
    });
  }

  loadSimilarMovies(page = this.currentPage): void {
    if (!this.guide) return;
    this.loading = true;
    this.errorMessage = '';
    this.moviesApi.listSimilarToGuideMovies(this.guide.id, page, this.pageSize, this.activeFilter, this.activeYear, this.activeCategories).subscribe({
      next: moviePage => {
        this.movies = moviePage.movies;
        this.totalCount = moviePage.totalCount;
        this.currentPage = page;
        this.loading = false;
      },
      error: err => {
        this.errorMessage = err?.error?.message ?? err?.message ?? 'Could not load similar movies';
        this.loading = false;
      }
    });
  }

  applyFilter(search: ParsedMovieSearch): void {
    const categories = search.selectedCategories ?? [];
    if (search.keyword === this.activeFilter && search.year === this.activeYear && categories.join() === this.activeCategories.join()) return;
    this.activeFilter = search.keyword;
    this.activeYear = search.year;
    this.activeCategories = categories;
    this.hasActiveFilter = search.hasActiveFilter ?? false;
    this.loadSimilarMovies(1);
  }

  emptyMessage(): string {
    return this.hasActiveFilter ? 'No movies found' : 'No similar movies yet';
  }

  poster(movie: Movie): string {
    return movie.poster && movie.poster !== 'N/A' ? movie.poster : '/images/movie-poster.jpg';
  }

  likeMovie(movie: Movie): void {
    if (this.recommendationBusy[movie.imdbId]) return;
    this.updateRecommendation(movie, () => this.moviesApi.recommendMovie(movie.imdbId));
  }

  dislikeMovie(movie: Movie): void {
    if (this.recommendationBusy[movie.imdbId]) return;
    this.updateRecommendation(movie, () => this.moviesApi.dislikeMovie(movie.imdbId));
  }

  clearRecommendation(movie: Movie): void {
    if (this.recommendationBusy[movie.imdbId]) return;
    this.updateRecommendation(movie, () => this.moviesApi.unrecommendMovie(movie.imdbId));
  }

  openCategories(movie: Movie): void { this.categoryMovie = movie; }
  closeCategories(): void { this.categoryMovie = null; }

  private updateRecommendation(movie: Movie, requestFactory: () => ReturnType<MoviesApiService['recommendMovie']>): void {
    this.recommendationBusy[movie.imdbId] = true;
    this.errorMessage = '';
    requestFactory().subscribe({
      next: updatedMovie => {
        movie.recommended = updatedMovie.recommended;
        movie.disliked = updatedMovie.disliked;
        this.recommendationBusy[movie.imdbId] = false;
      },
      error: err => {
        this.errorMessage = err?.error?.message ?? err?.message ?? 'Could not update recommendation';
        this.recommendationBusy[movie.imdbId] = false;
      }
    });
  }

  private resetState(): void {
    this.guide = null;
    this.movies = [];
    this.totalCount = 0;
    this.currentPage = 1;
    this.errorMessage = '';
    this.loading = false;
    this.filterText = '';
    this.activeFilter = '';
    this.activeYear = '';
    this.activeCategories = [];
    this.hasActiveFilter = false;
  }
}
