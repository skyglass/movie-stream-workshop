import { CommonModule } from '@angular/common';
import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { Subscription, combineLatest } from 'rxjs';
import { AuthService } from '../../services/auth';
import { Movie, MoviesApiService, ParsedMovieSearch } from '../../services/movies-api';
import { BackButtonComponent } from '../back-button/back-button';
import { MoviePageNavigatorComponent } from '../movie-page-navigator/movie-page-navigator';
import { MovieFilterSearchComponent } from '../movie-filter-search/movie-filter-search';

@Component({
  standalone: true,
  selector: 'app-similar-movies',
  imports: [CommonModule, RouterLink, BackButtonComponent, MoviePageNavigatorComponent, MovieFilterSearchComponent],
  templateUrl: './similar-movies.html',
  styleUrl: './similar-movies.css'
})
export class SimilarMoviesComponent implements OnInit, OnDestroy {
  private readonly route = inject(ActivatedRoute);
  private readonly moviesApi = inject(MoviesApiService);
  readonly auth = inject(AuthService);
  private pageSub?: Subscription;
  private imdbId = '';

  seedMovie: Movie | null = null;
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

  // This page is public (reachable by anonymous visitors from the Movie Details page); isAuthenticated$ stays in
  // the trigger so that signing in or out while already here reloads the results with/without the viewer's own
  // rating history, rather than leaving a stale list on screen.
  ngOnInit(): void {
    this.pageSub = combineLatest([this.route.paramMap, this.auth.isAuthenticated$]).subscribe(([params]) => {
      this.imdbId = params.get('imdbId') ?? '';
      this.resetState();
      if (this.imdbId) {
        this.loadSeedMovie();
        this.loadSimilarMovies(1);
      }
    });
  }

  ngOnDestroy(): void {
    this.pageSub?.unsubscribe();
  }

  loadSeedMovie(): void {
    this.moviesApi.getMovie(this.imdbId).subscribe({
      next: movie => { this.seedMovie = movie; },
      error: () => {}
    });
  }

  loadSimilarMovies(page = this.currentPage): void {
    this.loading = true;
    this.errorMessage = '';
    this.moviesApi.listSimilarMovies(this.imdbId, page, this.pageSize, this.activeFilter, this.activeYear, this.activeCategories).subscribe({
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
    this.loadSimilarMovies(1);
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
    this.seedMovie = null;
    this.movies = [];
    this.totalCount = 0;
    this.currentPage = 1;
    this.errorMessage = '';
    this.loading = false;
    this.filterText = '';
    this.activeFilter = '';
    this.activeYear = '';
    this.activeCategories = [];
  }
}
