import { CommonModule } from '@angular/common';
import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { Meta, Title } from '@angular/platform-browser';
import { Subscription } from 'rxjs';
import { AuthService } from '../../services/auth';
import { Movie, MoviesApiService, ParsedMovieSearch } from '../../services/movies-api';
import { MoviePageNavigatorComponent } from '../movie-page-navigator/movie-page-navigator';
import { MovieFilterSearchComponent } from '../movie-filter-search/movie-filter-search';
import { CategoryTreeDialogComponent } from '../category-tree-dialog/category-tree-dialog';

// Owner's own "Similar to My Favorite Movies" (default, authenticated) plus a public, read-only view at
// /my-favorite-movies/:username/similar -- "movies similar to THIS public user's favorites", reachable from the
// "Recommend Similar Movies" link on their shared favorites page. Public visitors never see like/dislike
// controls here (those act on the viewer's own recommendations, not the page owner's).
@Component({
  standalone: true,
  selector: 'app-similar-favorite-movies',
  imports: [CommonModule, RouterLink, MoviePageNavigatorComponent, MovieFilterSearchComponent, CategoryTreeDialogComponent],
  templateUrl: './similar-favorite-movies.html',
  styleUrl: './similar-favorite-movies.css'
})
export class SimilarFavoriteMoviesComponent implements OnInit, OnDestroy {
  private readonly moviesApi = inject(MoviesApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly meta = inject(Meta);
  private readonly title = inject(Title);
  readonly auth = inject(AuthService);
  private authSub?: Subscription;
  private routeSub?: Subscription;

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

  isPublicView = false;
  publicUsername = '';
  categoryMovie: Movie | null = null;

  ngOnInit(): void {
    this.routeSub = this.route.paramMap.subscribe(params => {
      const encodedUsername = params.get('username');
      this.isPublicView = encodedUsername !== null;
      this.publicUsername = encodedUsername ? this.decodeUsername(encodedUsername) : '';
      this.applySeoMetadata();
      this.resetState();
      if (this.isPublicView) {
        this.loadSimilarMovies(1);
      } else if (this.auth.token) {
        this.loadSimilarMovies(1);
      }
    });

    this.authSub = this.auth.isAuthenticated$.subscribe(authenticated => {
      if (this.isPublicView) return;
      if (authenticated) {
        this.loadSimilarMovies(1);
      } else {
        this.resetState();
      }
    });
  }

  ngOnDestroy(): void {
    this.authSub?.unsubscribe();
    this.routeSub?.unsubscribe();
  }

  loadSimilarMovies(page = this.currentPage): void {
    this.loading = true;
    this.errorMessage = '';
    const request = this.isPublicView
      ? this.moviesApi.listPublicSimilarToFavoriteMovies(this.publicUsername, page, this.pageSize, this.activeFilter, this.activeYear, this.activeCategories)
      : this.moviesApi.listSimilarToFavoriteMovies(page, this.pageSize, this.activeFilter, this.activeYear, this.activeCategories);
    request.subscribe({
      next: moviePage => {
        this.movies = moviePage.movies;
        this.totalCount = moviePage.totalCount;
        this.currentPage = page;
        this.loading = false;
      },
      error: err => {
        this.errorMessage = this.isPublicView && err?.status === 404
          ? 'This favorite movies link is private or unavailable'
          : err?.error?.message ?? err?.message ?? 'Could not load similar movies';
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

  private decodeUsername(value: string): string {
    try {
      return decodeURIComponent(value);
    } catch {
      return value;
    }
  }

  private applySeoMetadata(): void {
    const pageTitle = this.isPublicView
      ? `Similar to ${this.publicUsername}'s Favorite Movies | Movie Challenge`
      : 'Similar to My Favorite Movies | Movie Challenge';
    this.title.setTitle(pageTitle);
    this.meta.updateTag({ name: 'robots', content: this.isPublicView ? 'index, follow' : 'noindex' });
  }
}
