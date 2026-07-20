import { CommonModule } from '@angular/common';
import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { Meta, Title } from '@angular/platform-browser';
import { Observable, Subscription, map } from 'rxjs';
import { AuthService } from '../../services/auth';
import { Movie, MoviesApiService, ParsedMovieSearch, UsersRecommendedMoviesShare } from '../../services/movies-api';
import { MoviePageNavigatorComponent } from '../movie-page-navigator/movie-page-navigator';
import { MovieFilterSearchComponent } from '../movie-filter-search/movie-filter-search';
import { ShareDialogComponent } from '../share-dialog/share-dialog';

// Owner's own authenticated view (default) plus a public, read-only view at /my-recommended-movies/:username
// once the owner has opted into sharing -- same dual-mode shape as FavoriteMoviesComponent. "Share" (and the
// "Download Poster Collage" it hosts) is only ever shown to the authenticated owner looking at their own list;
// public visitors never see it, matching the ownership boundary already enforced server-side.
@Component({
  standalone: true,
  selector: 'app-users-recommended-movies',
  imports: [CommonModule, RouterLink, MoviePageNavigatorComponent, MovieFilterSearchComponent, ShareDialogComponent],
  templateUrl: './users-recommended-movies.html',
  styleUrl: './users-recommended-movies.css'
})
export class UsersRecommendedMoviesComponent implements OnInit, OnDestroy {
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
  shareStatus?: UsersRecommendedMoviesShare;
  shareLoading = false;
  shareUrl = '';
  shareDialogVisible = false;

  ngOnInit(): void {
    this.routeSub = this.route.paramMap.subscribe(params => {
      const encodedUsername = params.get('username');
      this.isPublicView = encodedUsername !== null;
      this.publicUsername = encodedUsername ? this.decodeUsername(encodedUsername) : '';
      this.applySeoMetadata();
      this.resetState();
      if (this.isPublicView) {
        this.loadMovies(1);
      } else if (this.auth.token) {
        this.loadMovies(1);
        this.loadShareStatus();
      }
    });

    this.authSub = this.auth.isAuthenticated$.subscribe(authenticated => {
      if (this.isPublicView) return;
      if (authenticated) {
        this.loadMovies(1);
        this.loadShareStatus();
      } else {
        this.resetState();
      }
    });
  }

  ngOnDestroy(): void {
    this.authSub?.unsubscribe();
    this.routeSub?.unsubscribe();
  }

  loadMovies(page = this.currentPage): void {
    this.loading = true;
    this.errorMessage = '';
    const request = this.isPublicView
      ? this.moviesApi.listPublicUsersRecommendedMovies(this.publicUsername, page, this.pageSize, this.activeFilter, this.activeYear, this.activeCategories)
      : this.moviesApi.listUsersRecommendedMovies(page, this.pageSize, this.activeFilter, this.activeYear, this.activeCategories);
    request.subscribe({
      next: moviePage => {
        this.movies = moviePage.movies;
        this.totalCount = moviePage.totalCount;
        this.currentPage = page;
        this.loading = false;
      },
      error: err => {
        this.errorMessage = this.isPublicView && err?.status === 404
          ? 'This recommended movies link is private or unavailable'
          : err?.error?.message ?? err?.message ?? 'Could not load users recommended movies';
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
    this.loadMovies(1);
  }

  emptyMessage(): string {
    return this.hasActiveFilter ? 'No movies found' : 'No users recommended movies yet';
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

  loadShareStatus(): void {
    this.shareLoading = true;
    this.moviesApi.getUsersRecommendedMoviesShare().subscribe({
      next: share => {
        this.shareStatus = share;
        this.shareLoading = false;
      },
      error: () => { this.shareLoading = false; }
    });
  }

  share(): void {
    if (!this.shareStatus?.myRecommendedMoviesPublic) return;
    this.shareUrl = this.moviesApi.usersRecommendedMoviesShareUrl(this.shareStatus);
    this.shareDialogVisible = true;
  }

  closeShareDialog(): void {
    this.shareDialogVisible = false;
  }

  // Fetches up to maxMovies movies in the exact order shown on-screen for the current filter, without
  // touching this component's own movies/currentPage/totalCount state.
  fetchOrderedMovies = (maxMovies: number): Observable<Movie[]> =>
    this.moviesApi.listUsersRecommendedMovies(1, maxMovies, this.activeFilter, this.activeYear, this.activeCategories)
      .pipe(map(page => page.movies));

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
    this.shareStatus = undefined;
    this.shareUrl = '';
    this.shareDialogVisible = false;
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
      ? `${this.publicUsername}'s Recommended Movies | Movie Challenge`
      : 'Users Recommended Movies | Movie Challenge';
    this.title.setTitle(pageTitle);
    this.meta.updateTag({ name: 'robots', content: this.isPublicView ? 'index, follow' : 'noindex' });
  }
}
