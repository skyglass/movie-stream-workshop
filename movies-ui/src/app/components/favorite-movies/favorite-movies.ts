import { CommonModule } from '@angular/common';
import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { Meta, Title } from '@angular/platform-browser';
import { Subscription } from 'rxjs';
import { AuthService } from '../../services/auth';
import { FavoriteMoviesShare, Movie, MoviesApiService, ParsedMovieSearch } from '../../services/movies-api';
import { MoviePageNavigatorComponent } from '../movie-page-navigator/movie-page-navigator';
import { MovieFilterSearchComponent } from '../movie-filter-search/movie-filter-search';
import { CategoryTreeDialogComponent } from '../category-tree-dialog/category-tree-dialog';

@Component({
  standalone: true,
  selector: 'app-favorite-movies',
  imports: [CommonModule, RouterLink, MoviePageNavigatorComponent, MovieFilterSearchComponent, CategoryTreeDialogComponent],
  templateUrl: './favorite-movies.html',
  styleUrl: './favorite-movies.css'
})
export class FavoriteMoviesComponent implements OnInit, OnDestroy {
  private readonly moviesApi = inject(MoviesApiService);
  readonly auth = inject(AuthService);
  private readonly route = inject(ActivatedRoute);
  private readonly meta = inject(Meta);
  private readonly title = inject(Title);
  private authSub?: Subscription;
  private routeSub?: Subscription;

  movies: Movie[] = [];
  loading = false;
  errorMessage = '';
  shareLoading = false;
  shareErrorMessage = '';
  shareUrl = '';
  copiedShareUrl = false;
  shareDetailsVisible = false;
  share?: FavoriteMoviesShare;
  isPublicView = false;
  publicUsername = '';
  currentPage = 1;
  totalCount = 0;
  readonly pageSize = this.moviesApi.moviePageSize;
  filterText = '';
  activeFilter = '';
  activeYear = '';
  activeCategories: number[] = [];
  categoryMovie: Movie | null = null;

  ngOnInit(): void {
    this.routeSub = this.route.paramMap.subscribe(params => {
      const encodedUsername = params.get('username');
      this.isPublicView = encodedUsername !== null;
      this.publicUsername = encodedUsername ? this.decodeUsername(encodedUsername) : '';
      this.applySeoMetadata();
      this.resetMovies();
      this.resetFilter();
      this.resetShareState();
      if (this.isPublicView) {
        this.loadFavoriteMovies(1);
      } else if (this.auth.token) {
        this.loadFavoriteMovies(1);
        this.loadShareStatus();
      }
    });

    this.authSub = this.auth.isAuthenticated$.subscribe(authenticated => {
      if (this.isPublicView) return;

      if (authenticated) {
        this.loadFavoriteMovies(1);
        this.loadShareStatus();
      } else {
        this.resetMovies();
        this.resetFilter();
        this.resetShareState();
      }
    });
  }

  ngOnDestroy(): void {
    this.authSub?.unsubscribe();
    this.routeSub?.unsubscribe();
  }

  loadFavoriteMovies(page = this.currentPage): void {
    this.loading = true;
    this.errorMessage = '';
    const moviesRequest = this.isPublicView
      ? this.moviesApi.listPublicFavoriteMovies(this.publicUsername, page, this.pageSize, this.activeFilter, this.activeYear, this.activeCategories)
      : this.moviesApi.listFavoriteMovies(page, this.pageSize, this.activeFilter, this.activeYear, this.activeCategories);

    moviesRequest.subscribe({
      next: moviePage => {
        this.movies = moviePage.movies;
        this.totalCount = moviePage.totalCount;
        this.currentPage = page;
        this.loading = false;
      },
      error: err => {
        this.errorMessage = this.isPublicView && err?.status === 404
          ? 'This favorite movies link is private or unavailable'
          : err?.error?.message ?? err?.message ?? 'Could not load favorite movies';
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
    this.loadFavoriteMovies(1);
  }

  loadShareStatus(): void {
    this.shareLoading = true;
    this.shareErrorMessage = '';
    this.moviesApi.getFavoriteMoviesShare().subscribe({
      next: share => {
        this.applyShare(share);
        this.shareLoading = false;
      },
      error: err => {
        this.shareErrorMessage = err?.error?.message ?? err?.message ?? 'Could not load sharing status';
        this.shareLoading = false;
      }
    });
  }

  shareFavoriteMovies(): void {
    if (!this.share?.myFavoriteMoviesPublic) return;
    this.copiedShareUrl = false;
    this.shareUrl = this.moviesApi.favoriteMoviesShareUrl(this.share);
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

  poster(movie: Movie): string {
    return movie.poster && movie.poster !== 'N/A' ? movie.poster : '/images/movie-poster.jpg';
  }

  openCategories(movie: Movie): void { this.categoryMovie = movie; }
  closeCategories(): void { this.categoryMovie = null; }

  private applyShare(share: FavoriteMoviesShare): void {
    this.share = share;
    if (!share.myFavoriteMoviesPublic) {
      this.closeShareDetails();
    }
  }

  private resetMovies(): void {
    this.movies = [];
    this.totalCount = 0;
    this.currentPage = 1;
    this.errorMessage = '';
    this.loading = false;
  }

  private resetFilter(): void {
    this.filterText = '';
    this.activeFilter = '';
    this.activeYear = '';
    this.activeCategories = [];
  }

  private resetShareState(): void {
    this.share = undefined;
    this.shareUrl = '';
    this.shareErrorMessage = '';
    this.shareLoading = false;
    this.copiedShareUrl = false;
    this.shareDetailsVisible = false;
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
      ? `${this.publicUsername}'s Favorite Movies | Movie Challenge`
      : 'My Favorite Movies | Movie Challenge';
    const description = this.isPublicView
      ? `Browse ${this.publicUsername}'s shared favorite movies from Movie Challenge.`
      : 'View and share your favorite movies from Movie Challenge.';

    this.title.setTitle(pageTitle);
    this.meta.updateTag({ name: 'description', content: description });
    this.meta.updateTag({ name: 'robots', content: this.isPublicView ? 'index, follow' : 'noindex' });
    this.meta.updateTag({ property: 'og:title', content: pageTitle });
    this.meta.updateTag({ property: 'og:description', content: description });
    this.meta.updateTag({ name: 'twitter:title', content: pageTitle });
    this.meta.updateTag({ name: 'twitter:description', content: description });
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
