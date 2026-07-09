import { CommonModule } from '@angular/common';
import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { Subscription } from 'rxjs';
import { AuthService } from '../../services/auth';
import { FavoriteMoviesShare, Movie, MoviesApiService } from '../../services/movies-api';
import { MoviePageNavigatorComponent } from '../movie-page-navigator/movie-page-navigator';

@Component({
  standalone: true,
  selector: 'app-favorite-movies',
  imports: [CommonModule, RouterLink, MoviePageNavigatorComponent],
  templateUrl: './favorite-movies.html',
  styleUrl: './favorite-movies.css'
})
export class FavoriteMoviesComponent implements OnInit, OnDestroy {
  private readonly moviesApi = inject(MoviesApiService);
  readonly auth = inject(AuthService);
  private readonly route = inject(ActivatedRoute);
  private authSub?: Subscription;
  private routeSub?: Subscription;

  movies: Movie[] = [];
  loading = false;
  errorMessage = '';
  shareLoading = false;
  shareErrorMessage = '';
  shareUrl = '';
  copiedShareUrl = false;
  share?: FavoriteMoviesShare;
  isPublicView = false;
  publicUsername = '';
  currentPage = 1;
  totalCount = 0;
  readonly pageSize = this.moviesApi.moviePageSize;

  ngOnInit(): void {
    this.routeSub = this.route.paramMap.subscribe(params => {
      const encodedUsername = params.get('username');
      this.isPublicView = encodedUsername !== null;
      this.publicUsername = encodedUsername ? this.decodeUsername(encodedUsername) : '';
      this.resetMovies();
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
      ? this.moviesApi.listPublicFavoriteMovies(this.publicUsername, page, this.pageSize)
      : this.moviesApi.listFavoriteMovies(page, this.pageSize);

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
    this.shareLoading = true;
    this.shareErrorMessage = '';
    this.copiedShareUrl = false;
    this.moviesApi.shareFavoriteMovies().subscribe({
      next: share => {
        this.applyShare(share);
        this.shareLoading = false;
      },
      error: err => {
        this.shareErrorMessage = err?.error?.message ?? err?.message ?? 'Could not share favorite movies';
        this.shareLoading = false;
      }
    });
  }

  makeFavoriteMoviesPrivate(): void {
    this.shareLoading = true;
    this.shareErrorMessage = '';
    this.copiedShareUrl = false;
    this.moviesApi.makeFavoriteMoviesPrivate().subscribe({
      next: share => {
        this.applyShare(share);
        this.shareLoading = false;
      },
      error: err => {
        this.shareErrorMessage = err?.error?.message ?? err?.message ?? 'Could not make favorite movies private';
        this.shareLoading = false;
      }
    });
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

  private applyShare(share: FavoriteMoviesShare): void {
    this.share = share;
    this.shareUrl = share.myFavoriteMoviesPublic ? this.moviesApi.favoriteMoviesShareUrl(share) : '';
  }

  private resetMovies(): void {
    this.movies = [];
    this.totalCount = 0;
    this.currentPage = 1;
    this.errorMessage = '';
    this.loading = false;
  }

  private resetShareState(): void {
    this.share = undefined;
    this.shareUrl = '';
    this.shareErrorMessage = '';
    this.shareLoading = false;
    this.copiedShareUrl = false;
  }

  private decodeUsername(value: string): string {
    try {
      return decodeURIComponent(value);
    } catch {
      return value;
    }
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
