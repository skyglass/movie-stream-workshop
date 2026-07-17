import { CommonModule } from '@angular/common';
import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Meta, Title } from '@angular/platform-browser';
import { AuthService } from '../../services/auth';
import { MoviesApiService, Movie, ParsedMovieSearch } from '../../services/movies-api';
import { Subscription } from 'rxjs';
import { MoviePageNavigatorComponent } from '../movie-page-navigator/movie-page-navigator';
import { MovieFilterSearchComponent } from '../movie-filter-search/movie-filter-search';

@Component({
  standalone: true,
  selector: 'app-movies-home',
  imports: [CommonModule, RouterLink, MoviePageNavigatorComponent, MovieFilterSearchComponent],
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
  filterText = '';
  activeFilter = '';
  activeYear = '';
  activeCategories: number[] = [];
  activeOnlyNotRecommended = false;
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
    this.moviesApi.listMovies(page, this.pageSize, this.activeFilter, this.activeYear, this.activeCategories, this.activeOnlyNotRecommended).subscribe({
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

  applyFilter(search: ParsedMovieSearch): void {
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

  private updateRecommendation(movie: Movie, requestFactory: () => ReturnType<MoviesApiService['recommendMovie']>): void {
    if (!this.auth.token) return;
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
