import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { catchError, forkJoin, map, Observable, of, switchMap } from 'rxjs';
import { AppConfigService } from '../config/app-config.service';

export interface MovieComment {
  username: string;
  avatar: string;
  text: string;
  timestamp: string;
}

export type MovieType = 'MOVIE' | 'SERIES' | 'EPISODE';

export interface Movie {
  imdbId: string;
  title: string;
  director: string;
  writer: string;
  year: string;
  poster: string;
  genre: string;
  country: string;
  type: MovieType;
  typeDescription: string;
  recommended: boolean;
  disliked: boolean;
  comments: MovieComment[];
}

export interface MoviePage {
  movies: Movie[];
  totalCount: number;
}

export interface MovieUser {
  username: string;
  email: string;
  avatar: string;
}

export interface MovieChallengeMovie {
  imdbId: string;
  title: string;
  poster: string;
}

export interface MovieChallenge {
  movie1: MovieChallengeMovie;
  movie2: MovieChallengeMovie;
}

export interface OmdbMovie {
  imdbID: string;
  Title: string;
  Director: string;
  Writer?: string;
  Creator?: string;
  Year: string;
  Poster: string;
  Country?: string;
  Language?: string;
  Genre?: string;
  Runtime?: string;
  imdbRating?: string;
  Type?: string;
  Response: string;
  Error?: string;
}

export interface OmdbSearchItem {
  imdbID: string;
  Title: string;
  Year: string;
  Type: string;
  Poster: string;
}

export interface OmdbSearchResponse {
  Search?: OmdbSearchItem[];
  totalResults?: string;
  Response: string;
  Error?: string;
}

export type OmdbSearchType = 'movie' | 'series' | 'episode';
export type OmdbPlotType = 'short' | 'full';

export interface OmdbPlotResponse {
  Plot?: string;
  Response: string;
  Error?: string;
}

export interface OmdbMovieSearchCriteria {
  title: string;
  year: string;
  type: OmdbSearchType;
  exactTitleMatch: boolean;
}

export interface OmdbMovieSearchResult {
  imdbId: string;
  originalTitle: string;
  englishTitle: string;
  directors: string;
  writers: string;
  year: string;
  country: string;
  poster: string;
  language: string;
  runtime: string;
  genre: string;
  imdbRating: string;
  type: MovieType;
  typeDescription: string;
}

export interface OmdbMovieSearchPage {
  movies: OmdbMovieSearchResult[];
  totalResults: number;
  hasNext: boolean;
}

export interface RecommendMovieFromSearchRequest {
  imdbId: string;
  title: string;
  originalTitle: string;
  director: string;
  writer: string;
  year: string;
  country: string;
  genre: string;
  poster: string;
  type: MovieType;
}

@Injectable({ providedIn: 'root' })
export class MoviesApiService {
  private readonly moviesBase: string;
  private readonly movieChallengesBase: string;
  private readonly favoriteMoviesBase: string;
  private readonly usersFavoriteMoviesBase: string;
  private readonly usersRecommendedMoviesBase: string;
  private readonly userExtrasBase: string;
  private readonly usersBase: string;

  constructor(private http: HttpClient, private cfg: AppConfigService) {
    const c = cfg.config;
    this.moviesBase = `${c.apiBaseUrl}${c.moviesApiPath}`;
    this.movieChallengesBase = `${c.apiBaseUrl}${c.movieChallengesPath}`;
    this.favoriteMoviesBase = `${c.apiBaseUrl}${c.favoriteMoviesPath}`;
    this.usersFavoriteMoviesBase = `${c.apiBaseUrl}${c.usersFavoriteMoviesPath}`;
    this.usersRecommendedMoviesBase = `${c.apiBaseUrl}${c.usersRecommendedMoviesPath}`;
    this.userExtrasBase = `${c.apiBaseUrl}${c.userExtrasPath}`;
    this.usersBase = `${c.apiBaseUrl}${c.usersApiPath}`;
  }

  get moviePageSize(): number {
    const configuredPageSize = Number(this.cfg.config.moviesPerPage ?? 50);
    return Number.isFinite(configuredPageSize) && configuredPageSize > 0 ? Math.floor(configuredPageSize) : 50;
  }

  listMovies(page = 1, pageSize = this.moviePageSize): Observable<MoviePage> {
    return this.http.get<MoviePage>(this.moviesBase, { params: this.pageParams(page, pageSize) });
  }

  listFavoriteMovies(page = 1, pageSize = this.moviePageSize): Observable<MoviePage> {
    return this.http.get<MoviePage>(this.favoriteMoviesBase, { params: this.pageParams(page, pageSize) });
  }

  listUsersFavoriteMovies(page = 1, pageSize = this.moviePageSize): Observable<MoviePage> {
    return this.http.get<MoviePage>(this.usersFavoriteMoviesBase, { params: this.pageParams(page, pageSize) });
  }

  listUsersRecommendedMovies(page = 1, pageSize = this.moviePageSize): Observable<MoviePage> {
    return this.http.get<MoviePage>(this.usersRecommendedMoviesBase, { params: this.pageParams(page, pageSize) });
  }

  getMovie(imdbId: string): Observable<Movie> {
    return this.http.get<Movie>(`${this.moviesBase}/${imdbId}`);
  }

  createMovie(movie: Partial<Movie>): Observable<Movie> {
    return this.http.post<Movie>(this.moviesBase, movie);
  }

  updateMovie(imdbId: string, movie: Partial<Movie>): Observable<Movie> {
    return this.http.put<Movie>(`${this.moviesBase}/${imdbId}`, movie);
  }

  deleteMovie(imdbId: string): Observable<Movie> {
    return this.http.delete<Movie>(`${this.moviesBase}/${imdbId}`);
  }

  addComment(imdbId: string, text: string): Observable<Movie> {
    return this.http.post<Movie>(`${this.moviesBase}/${imdbId}/comments`, { text });
  }

  recommendMovie(imdbId: string): Observable<Movie> {
    return this.http.post<Movie>(`${this.moviesBase}/${imdbId}/recommendation`, {});
  }

  recommendMovieFromSearch(movie: RecommendMovieFromSearchRequest): Observable<Movie> {
    return this.http.post<Movie>(`${this.moviesBase}/recommendation`, movie);
  }

  unrecommendMovie(imdbId: string): Observable<Movie> {
    return this.http.delete<Movie>(`${this.moviesBase}/${imdbId}/recommendation`);
  }

  dislikeMovie(imdbId: string): Observable<Movie> {
    return this.http.post<Movie>(`${this.moviesBase}/${imdbId}/recommendation/dislike`, {});
  }

  nextMovieChallenge(): Observable<MovieChallenge | null> {
    return this.http.get<MovieChallenge>(`${this.movieChallengesBase}/next`, {
      observe: 'response'
    }).pipe(map(response => response.status === 204 ? null : response.body));
  }

  selectMovieChallengeWinner(movie1Id: string, movie2Id: string, selectedMovieId: string): Observable<void> {
    return this.http.post<void>(`${this.movieChallengesBase}/votes`, { movie1Id, movie2Id, selectedMovieId });
  }

  syncMe(): Observable<MovieUser> {
    return this.http.get<MovieUser>(`${this.userExtrasBase}/me`);
  }

  changeAvatar(avatar: string): Observable<MovieUser> {
    return this.http.post<MovieUser>(`${this.userExtrasBase}/me`, { avatar });
  }

  listUsers(): Observable<MovieUser[]> {
    return this.http.get<MovieUser[]>(this.usersBase);
  }

  searchOmdb(title: string): Observable<OmdbMovie> {
    const c = this.cfg.config;
    const params = new URLSearchParams({
      apikey: c.omdbApiKey,
      t: title
    });
    return this.http.get<OmdbMovie>(`${c.omdbBaseUrl}?${params.toString()}`);
  }

  getOmdbPlot(imdbId: string, plot: OmdbPlotType): Observable<string> {
    const c = this.cfg.config;
    const params = new URLSearchParams({
      apikey: c.omdbApiKey,
      i: imdbId,
      plot
    });
    return this.http.get<OmdbPlotResponse>(`${c.omdbBaseUrl}?${params.toString()}`).pipe(
      map(response => {
        if (response.Response === 'False') {
          throw new Error(response.Error || 'OMDb plot was not found');
        }
        return this.cleanOmdbValue(response.Plot);
      })
    );
  }

  searchOmdbMovies(criteria: OmdbMovieSearchCriteria, page = 1): Observable<OmdbMovieSearchPage> {
    const titleQuery = criteria.title.trim();
    const year = criteria.year.trim();
    const type = criteria.type;
    const exact = criteria.exactTitleMatch;
    const omdbPage = Math.max(1, Math.floor((Math.max(1, page) - 1) / 2) + 1);
    const sliceStart = ((Math.max(1, page) - 1) % 2) * 5;
    const exactMovie$ = exact && page === 1
      ? this.lookupOmdbTitle(titleQuery, year, type)
      : of(null);
    const primarySearch$ = this.searchOmdbPage(titleQuery, omdbPage, type, year);

    return forkJoin({
      exactMovie: exactMovie$,
      primarySearch: primarySearch$,
    }).pipe(
      switchMap(({ exactMovie, primarySearch }) => {
        const searchItems = this.searchItems(primarySearch);
        if (searchItems.length === 0) {
          const exactResults = exactMovie && this.successfulOmdbMovie(exactMovie)
            ? [this.toSearchResult(exactMovie)]
            : [];
          return of(this.toSearchPage(exactResults, criteria, titleQuery, sliceStart, page, primarySearch));
        }

        const details$ = forkJoin(searchItems.map(item => this.lookupOmdbById(item.imdbID)));
        return details$.pipe(
          map(details => {
            const results = details
              .filter((movie): movie is OmdbMovie => movie !== null && this.successfulOmdbMovie(movie))
              .map(movie => this.toSearchResult(movie));
            if (exactMovie && this.successfulOmdbMovie(exactMovie)) {
              results.unshift(this.toSearchResult(exactMovie));
            }
            return this.toSearchPage(results, criteria, titleQuery, sliceStart, page, primarySearch);
          })
        );
      })
    );
  }

  private pageParams(page: number, pageSize: number): Record<string, string> {
    return {
      page: String(page),
      pageSize: String(pageSize)
    };
  }

  private searchOmdbPage(query: string, page: number, type: OmdbSearchType, year: string): Observable<OmdbSearchResponse> {
    const c = this.cfg.config;
    const params = new URLSearchParams({
      apikey: c.omdbApiKey,
      s: query,
      type,
      page: String(page)
    });
    if (year) {
      params.set('y', year);
    }
    return this.http.get<OmdbSearchResponse>(`${c.omdbBaseUrl}?${params.toString()}`).pipe(
      catchError(() => of({ Response: 'False', Error: 'OMDb search failed' }))
    );
  }

  private lookupOmdbById(imdbId: string): Observable<OmdbMovie | null> {
    const c = this.cfg.config;
    const params = new URLSearchParams({
      apikey: c.omdbApiKey,
      i: imdbId
    });
    return this.http.get<OmdbMovie>(`${c.omdbBaseUrl}?${params.toString()}`).pipe(
      catchError(() => of(null))
    );
  }

  private lookupOmdbTitle(title: string, year: string, type: OmdbSearchType): Observable<OmdbMovie | null> {
    const c = this.cfg.config;
    const params = new URLSearchParams({
      apikey: c.omdbApiKey,
      t: title,
      type
    });
    if (year) {
      params.set('y', year);
    }
    return this.http.get<OmdbMovie>(`${c.omdbBaseUrl}?${params.toString()}`).pipe(
      map(movie => this.successfulOmdbMovie(movie) ? movie : null),
      catchError(() => of(null))
    );
  }

  private searchItems(primarySearch: OmdbSearchResponse): OmdbSearchItem[] {
    const byId = new Map<string, OmdbSearchItem>();
    (primarySearch.Search ?? [])
      .forEach(item => byId.set(item.imdbID, item));
    return [...byId.values()];
  }

  private toSearchPage(
    results: OmdbMovieSearchResult[],
    criteria: OmdbMovieSearchCriteria,
    titleQuery: string,
    sliceStart: number,
    page: number,
    primarySearch: OmdbSearchResponse
  ): OmdbMovieSearchPage {
    const uniqueResults = this.uniqueResults(results);
    const scoredResults = uniqueResults
      .map(movie => ({ movie, score: this.searchScore(movie, criteria, titleQuery) }))
      .filter(result => result.score > 0 || !criteria.exactTitleMatch)
      .sort((left, right) => {
        if (right.score !== left.score) return right.score - left.score;
        const leftTitle = this.normalizedTitle(left.movie.englishTitle);
        const rightTitle = this.normalizedTitle(right.movie.englishTitle);
        return leftTitle.localeCompare(rightTitle)
          || left.movie.year.localeCompare(right.movie.year)
          || left.movie.imdbId.localeCompare(right.movie.imdbId);
      })
      .map(result => result.movie);
    const totalResults = this.totalSearchResults(primarySearch, uniqueResults.length);

    return {
      movies: scoredResults.slice(sliceStart, sliceStart + 5),
      totalResults,
      hasNext: page * 5 < totalResults
    };
  }

  private toSearchResult(movie: OmdbMovie): OmdbMovieSearchResult {
    const type = this.movieTypeFromOmdb(movie.Type);
    return {
      imdbId: movie.imdbID,
      originalTitle: '',
      englishTitle: this.cleanOmdbValue(movie.Title),
      directors: this.directorOrCreator(movie),
      writers: this.cleanOmdbValue(movie.Writer),
      year: this.cleanOmdbValue(movie.Year),
      country: this.cleanOmdbValue(movie.Country),
      poster: this.cleanOmdbValue(movie.Poster),
      language: this.cleanOmdbValue(movie.Language),
      runtime: this.cleanOmdbValue(movie.Runtime),
      genre: this.cleanOmdbValue(movie.Genre),
      imdbRating: this.cleanOmdbValue(movie.imdbRating),
      type,
      typeDescription: this.movieTypeDescription(type)
    };
  }

  private uniqueResults(results: OmdbMovieSearchResult[]): OmdbMovieSearchResult[] {
    const byId = new Map<string, OmdbMovieSearchResult>();
    results.forEach(result => {
      if (!byId.has(result.imdbId)) {
        byId.set(result.imdbId, result);
      }
    });
    return [...byId.values()];
  }

  private searchScore(
    movie: OmdbMovieSearchResult,
    criteria: OmdbMovieSearchCriteria,
    titleQuery: string
  ): number {
    const normalizedTitleQuery = this.normalize(titleQuery);
    const normalizedEnglishTitle = this.normalize(movie.englishTitle);
    const normalizedOriginalTitle = this.normalize(movie.originalTitle);
    const haystack = this.normalize([
      movie.englishTitle,
      movie.originalTitle,
      movie.directors,
      movie.writers,
      movie.year,
      movie.country,
      movie.language
    ].join(' '));
    const tokens = this.normalize(criteria.title)
      .split(' ')
      .filter(token => token.length > 0);
    let score = 0;

    if (normalizedEnglishTitle === normalizedTitleQuery || normalizedOriginalTitle === normalizedTitleQuery) {
      score += criteria.exactTitleMatch ? 300 : 120;
    }
    if (normalizedEnglishTitle.startsWith(normalizedTitleQuery) || normalizedOriginalTitle.startsWith(normalizedTitleQuery)) {
      score += 80;
    }
    if (normalizedEnglishTitle.includes(normalizedTitleQuery) || normalizedOriginalTitle.includes(normalizedTitleQuery)) {
      score += 60;
    }
    if (criteria.year && movie.year.includes(criteria.year)) {
      score += 100;
    }
    score += tokens.filter(token => haystack.includes(token)).length * 12;
    return score;
  }

  private totalSearchResults(
    primarySearch: OmdbSearchResponse,
    fallbackTotal: number
  ): number {
    const primaryTotal = Number(primarySearch.totalResults ?? 0);
    const total = Math.max(primaryTotal, fallbackTotal);
    return Number.isFinite(total) ? total : fallbackTotal;
  }

  private successfulOmdbMovie(movie: OmdbMovie): boolean {
    return movie.Response !== 'False';
  }

  private normalizedTitle(title: string): string {
    return this.normalize(title).replace(/^(the|a)\s+/, '');
  }

  private normalize(value: string): string {
    return value.toLowerCase().replace(/[^\p{L}\p{N}]+/gu, ' ').replace(/\s+/g, ' ').trim();
  }

  private cleanOmdbValue(value: string | undefined): string {
    return value && value !== 'N/A' ? value : '';
  }

  private directorOrCreator(movie: OmdbMovie): string {
    const director = this.cleanOmdbValue(movie.Director);
    if (director) {
      return director;
    }

    if (this.cleanOmdbValue(movie.Type).toLowerCase() === 'series') {
      return this.cleanOmdbValue(movie.Creator) || this.cleanOmdbValue(movie.Writer);
    }

    return '';
  }

  private movieTypeFromOmdb(type: string | undefined): MovieType {
    switch (this.cleanOmdbValue(type).toLowerCase()) {
      case 'series':
        return 'SERIES';
      case 'episode':
        return 'EPISODE';
      case 'movie':
      default:
        return 'MOVIE';
    }
  }

  private movieTypeDescription(type: MovieType): string {
    switch (type) {
      case 'SERIES':
        return 'Series';
      case 'EPISODE':
        return 'Episode';
      case 'MOVIE':
      default:
        return 'Movie';
    }
  }
}
