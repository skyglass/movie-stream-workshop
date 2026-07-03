import { CommonModule } from '@angular/common';
import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Meta, Title } from '@angular/platform-browser';
import { AuthService } from '../../services/auth';
import { MoviesApiService, Movie } from '../../services/movies-api';
import { Subscription } from 'rxjs';
import { MoviePageNavigatorComponent } from '../movie-page-navigator/movie-page-navigator';

@Component({
  standalone: true,
  selector: 'app-movies-home',
  imports: [CommonModule, RouterLink, MoviePageNavigatorComponent],
  templateUrl: './movies-home.html',
  styleUrl: './movies-home.css'
})
export class MoviesHomeComponent implements OnInit, OnDestroy {
  private readonly moviesApi = inject(MoviesApiService);
  private readonly meta = inject(Meta);
  private readonly title = inject(Title);
  readonly auth = inject(AuthService);

  movies: Movie[] = [];
  loading = false;
  errorMessage = '';
  currentPage = 1;
  totalCount = 0;
  readonly pageSize = this.moviesApi.moviePageSize;
  recommendationBusy: Record<string, boolean> = {};
  private authSub?: Subscription;

  ngOnInit(): void {
    this.applySeoMetadata();
    this.authSub = this.auth.isAuthenticated$.subscribe(() => {
      this.loadMovies(1);
    });
  }

  ngOnDestroy(): void {
    this.authSub?.unsubscribe();
  }

  loadMovies(page = this.currentPage): void {
    this.loading = true;
    this.errorMessage = '';
    this.moviesApi.listMovies(page, this.pageSize).subscribe({
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

  poster(movie: Movie): string {
    return movie.poster && movie.poster !== 'N/A' ? movie.poster : '/images/movie-poster.jpg';
  }

  toggleRecommendation(movie: Movie): void {
    if (!this.auth.token || this.recommendationBusy[movie.imdbId]) return;

    this.recommendationBusy[movie.imdbId] = true;
    const request = movie.recommended || movie.disliked
      ? this.moviesApi.unrecommendMovie(movie.imdbId)
      : this.moviesApi.recommendMovie(movie.imdbId);

    request.subscribe({
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

  recommendationTitle(movie: Movie): string {
    if (movie.disliked) return 'Clear dislike';
    return movie.recommended ? 'Unrecommend' : 'Recommend';
  }

  recommendationIcon(movie: Movie): string {
    if (movie.disliked) return 'thumb_down';
    return movie.recommended ? 'check_circle' : 'favorite_border';
  }

  recommendationLabel(movie: Movie): string {
    if (movie.disliked) return 'Disliked';
    return movie.recommended ? 'Recommended' : 'Recommend';
  }

  private applySeoMetadata(): void {
    const pageTitle = 'Movie Challenge | Community Recommendations and Favorites';
    const description = 'Movie Challenge helps movie fans discover films, browse recommendations, save favorite movies, and discuss titles with other movie fans.';
    this.title.setTitle(pageTitle);
    this.meta.updateTag({ name: 'description', content: description });
    this.meta.updateTag({ name: 'robots', content: 'index, follow' });
    this.meta.updateTag({ property: 'og:title', content: pageTitle });
    this.meta.updateTag({ property: 'og:description', content: description });
    this.meta.updateTag({ name: 'twitter:title', content: pageTitle });
    this.meta.updateTag({ name: 'twitter:description', content: description });
  }
}
