import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { catchError, forkJoin, map, Observable, of, throwError } from 'rxjs';
import { AppConfigService } from '../config/app-config.service';

export interface MovieComment {
  username: string;
  avatar: string;
  text: string;
  timestamp: string;
}

export type MovieType = 'MOVIE' | 'SERIES';

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
  rankPosition: number | null;
  rating: number | null;
  comments: MovieComment[];
}

export interface MoviePage {
  movies: Movie[];
  totalCount: number;
}

export interface MovieCategory {
  id: number;
  name: string;
  description: string;
  icon: string;
  parentId: number | null;
  checked: boolean;
  leaf: boolean;
  empty: boolean;
  children: MovieCategory[];
}

export interface SaveMovieCategory {
  name: string;
  description: string;
  icon: string;
  parentId: number | null;
}

export interface MovieRankHistoryMovie {
  imdbId: string;
  title: string;
  poster: string;
  year: string;
  director: string;
  rankPosition: number | null;
  rating: number | null;
}

export interface MovieRankHistory {
  higherRanks: MovieRankHistoryMovie[];
  lowerRanks: MovieRankHistoryMovie[];
}

export interface FavoriteMoviesShare {
  myFavoriteMoviesPublic: boolean;
  encodedUsername: string;
  sharePath: string;
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

export interface SuggestedMovieChallengeMovie {
  imdbId: string;
  title: string;
  poster: string;
  year: string;
  director: string;
  winProbabilityPercent: number;
  rankPosition: number | null;
  rating: number | null;
}

export interface SuggestedMovieChallenge {
  movie1: SuggestedMovieChallengeMovie;
  movie2: SuggestedMovieChallengeMovie;
}

export interface SuggestedMovieChallengePage {
  challenges: SuggestedMovieChallenge[];
  totalCount: number;
}

export interface MovieChallengeSelection {
  movie1Id: string;
  movie2Id: string;
  selectedMovieId: string;
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

export type OmdbSearchType = 'movie' | 'series';
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
  detailsLoaded: boolean;
}

export interface OmdbMovieSearchPage {
  movies: OmdbMovieSearchResult[];
  totalResults: number;
  hasNext: boolean;
}

export interface ParsedMovieSearch {
  keyword: string;
  year: string;
  selectedCategories?: number[];
  onlyNotRecommended?: boolean;
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

export interface GuideMovieRef {
  imdbId: string;
  categories: string[];
}

export interface CreateMovieGuideResponse {
  guideCategoryId: number;
  failedImdbIds: string[];
}

export interface GuideMovieDetails {
  movie: RecommendMovieFromSearchRequest;
  categories: string[];
}

export interface CourseMovie {
  imdbId: string; title: string; header: string; description: string; year: string; director: string;
  writer: string; genre: string; poster: string; watchOrder: number;
  linkedCourseId: number | null; linkedCourseTitle: string | null;
  liked: boolean; disliked: boolean; rankPosition: number | null; rating: number | null;
}

export interface MovieCourse {
  id: number; header: string; title: string; description: string; type: MovieJourneyType;
  typeDescription: string; creator: string; owner: boolean;
  applied: boolean; expert: boolean; averageRating: number | null; movieCount: number;
  movies: CourseMovie[]; suggestedCourses: { id: number; title: string }[];
}

export type MovieJourneyType = 'JOURNEY' | 'GUIDE' | 'COURSE' | 'FESTIVAL' | 'TOUR';

export interface CourseMovieInput {
  movieId: string; header: string; description: string; watchOrder: number; linkedCourseId: number | null;
}

@Injectable({ providedIn: 'root' })
export class MoviesApiService {
  private readonly omdbSearchPageSize = 10;
  private readonly moviesBase: string;
  private readonly movieChallengesBase: string;
  private readonly favoriteMoviesBase: string;
  private readonly publicFavoriteMoviesBase: string;
  private readonly usersFavoriteMoviesBase: string;
  private readonly usersRecommendedMoviesBase: string;
  private readonly userExtrasBase: string;
  private readonly usersBase: string;
  private readonly movieCoursesBase: string;
  private readonly categoriesBase: string;
  private readonly movieGuidesBase: string;

  constructor(private http: HttpClient, private cfg: AppConfigService) {
    const c = cfg.config;
    this.moviesBase = `${c.apiBaseUrl}${c.moviesApiPath}`;
    this.movieChallengesBase = `${c.apiBaseUrl}${c.movieChallengesPath}`;
    this.favoriteMoviesBase = `${c.apiBaseUrl}${c.favoriteMoviesPath}`;
    this.publicFavoriteMoviesBase = `${c.apiBaseUrl}${c.publicFavoriteMoviesPath}`;
    this.usersFavoriteMoviesBase = `${c.apiBaseUrl}${c.usersFavoriteMoviesPath}`;
    this.usersRecommendedMoviesBase = `${c.apiBaseUrl}${c.usersRecommendedMoviesPath}`;
    this.userExtrasBase = `${c.apiBaseUrl}${c.userExtrasPath}`;
    this.usersBase = `${c.apiBaseUrl}${c.usersApiPath}`;
    this.movieCoursesBase = `${c.apiBaseUrl}/api/movies/movie-journeys`;
    this.categoriesBase = `${c.apiBaseUrl}/api/movies/categories`;
    this.movieGuidesBase = `${c.apiBaseUrl}/api/movies/movie-guides`;
  }

  get moviePageSize(): number {
    return this.positiveConfigNumber(this.cfg.config.moviesPerPage, 'moviesPerPage');
  }

  listMovies(page = 1, pageSize = this.moviePageSize, filter = '', year = '', selectedCategories: number[] = [], onlyNotRecommended = false): Observable<MoviePage> {
    const params = this.pageParams(page, pageSize, filter, year, selectedCategories);
    if (onlyNotRecommended) {
      params['only_not_recommended'] = 'true';
    }
    return this.http.get<MoviePage>(this.moviesBase, { params });
  }

  listMovieCourses(): Observable<MovieCourse[]> { return this.http.get<MovieCourse[]>(this.movieCoursesBase); }
  getMovieCourse(id: number): Observable<MovieCourse> { return this.http.get<MovieCourse>(`${this.movieCoursesBase}/${id}`); }
  manageMovieCourse(id: number): Observable<MovieCourse> { return this.http.get<MovieCourse>(`${this.movieCoursesBase}/${id}/manage`); }
  createMovieCourse(header: string, title: string, description: string, type: MovieJourneyType): Observable<MovieCourse> {
    return this.http.post<MovieCourse>(this.movieCoursesBase, { header, title, description, type });
  }
  updateMovieCourse(id: number, header: string, title: string, description: string, type: MovieJourneyType): Observable<MovieCourse> {
    return this.http.put<MovieCourse>(`${this.movieCoursesBase}/${id}`, { header, title, description, type });
  }
  deleteMovieCourse(id: number): Observable<void> { return this.http.delete<void>(`${this.movieCoursesBase}/${id}`); }
  applyToMovieCourse(id: number): Observable<MovieCourse> {
    return this.http.post<MovieCourse>(`${this.movieCoursesBase}/${id}/applications`, {});
  }
  addCourseMovie(id: number, input: CourseMovieInput): Observable<MovieCourse> {
    return this.http.post<MovieCourse>(`${this.movieCoursesBase}/${id}/movies`, input);
  }
  addCourseMovieFromSearch(id: number, movie: OmdbMovieSearchResult, header: string, description: string, linkedCourseId: number | null): Observable<MovieCourse> {
    return this.http.post<MovieCourse>(`${this.movieCoursesBase}/${id}/movies-from-search`, {
      movie: this.movieFromOmdb(movie), header, description, linkedCourseId
    });
  }
  updateCourseMovie(id: number, movieId: string, input: CourseMovieInput): Observable<MovieCourse> {
    return this.http.put<MovieCourse>(`${this.movieCoursesBase}/${id}/movies/${movieId}`, input);
  }
  removeCourseMovie(id: number, movieId: string): Observable<MovieCourse> {
    return this.http.delete<MovieCourse>(`${this.movieCoursesBase}/${id}/movies/${movieId}`);
  }

  getCategoryTree(movieId?: string): Observable<MovieCategory[]> {
    return this.http.get<MovieCategory[]>(movieId
      ? `${this.categoriesBase}/movies/${encodeURIComponent(movieId)}`
      : this.categoriesBase);
  }

  createCategory(category: SaveMovieCategory): Observable<MovieCategory> {
    return this.http.post<MovieCategory>(this.categoriesBase, category);
  }

  updateCategory(id: number, category: SaveMovieCategory): Observable<MovieCategory> {
    return this.http.put<MovieCategory>(`${this.categoriesBase}/${id}`, category);
  }

  deleteCategory(id: number): Observable<void> {
    return this.http.delete<void>(`${this.categoriesBase}/${id}`);
  }

  saveMovieCategories(movieId: string, addedCategories: number[], removedCategories: number[]): Observable<MovieCategory[]> {
    return this.http.put<MovieCategory[]>(`${this.categoriesBase}/movies/${encodeURIComponent(movieId)}`, {
      addedCategories, removedCategories
    });
  }

  addMovieFromSearchToCategory(categoryId: number, movie: OmdbMovieSearchResult): Observable<void> {
    return this.http.post<void>(`${this.categoriesBase}/${categoryId}/movies-from-search`, this.movieFromOmdb(movie));
  }

  createMovieGuide(type: string, name: string, description: string, movies: GuideMovieRef[]): Observable<CreateMovieGuideResponse> {
    return this.http.post<CreateMovieGuideResponse>(this.movieGuidesBase, { type, name, description, movies });
  }

  completeMovieGuide(guideCategoryId: number, movies: GuideMovieDetails[]): Observable<void> {
    return this.http.post<void>(`${this.movieGuidesBase}/${guideCategoryId}/movies`, { movies });
  }

  createMovieGuideExistingOnly(type: string, name: string, description: string, movies: GuideMovieRef[]): Observable<CreateMovieGuideResponse> {
    return this.http.post<CreateMovieGuideResponse>(`${this.movieGuidesBase}/existing`, { type, name, description, movies });
  }

  completeMovieGuideExistingOnly(guideCategoryId: number, movies: GuideMovieDetails[]): Observable<void> {
    return this.http.post<void>(`${this.movieGuidesBase}/${guideCategoryId}/movies/existing`, { movies });
  }

  listFavoriteMovies(page = 1, pageSize = this.moviePageSize, filter = '', year = '', selectedCategories: number[] = []): Observable<MoviePage> {
    return this.http.get<MoviePage>(this.favoriteMoviesBase, { params: this.pageParams(page, pageSize, filter, year, selectedCategories) });
  }

  listPublicFavoriteMovies(username: string, page = 1, pageSize = this.moviePageSize, filter = '', year = '', selectedCategories: number[] = []): Observable<MoviePage> {
    return this.http.get<MoviePage>(`${this.publicFavoriteMoviesBase}/${encodeURIComponent(username)}`, {
      params: this.pageParams(page, pageSize, filter, year, selectedCategories)
    });
  }

  getFavoriteMoviesShare(): Observable<FavoriteMoviesShare> {
    return this.http.get<FavoriteMoviesShare>(`${this.favoriteMoviesBase}/share`);
  }

  shareFavoriteMovies(): Observable<FavoriteMoviesShare> {
    return this.http.post<FavoriteMoviesShare>(`${this.favoriteMoviesBase}/share`, {});
  }

  makeFavoriteMoviesPrivate(): Observable<FavoriteMoviesShare> {
    return this.http.delete<FavoriteMoviesShare>(`${this.favoriteMoviesBase}/share`);
  }

  favoriteMoviesShareUrl(share: FavoriteMoviesShare): string {
    return `${this.trimTrailingSlash(this.cfg.config.uiBaseUrl)}${share.sharePath}`;
  }

  listUsersFavoriteMovies(page = 1, pageSize = this.moviePageSize, filter = '', year = '', selectedCategories: number[] = []): Observable<MoviePage> {
    return this.http.get<MoviePage>(this.usersFavoriteMoviesBase, { params: this.pageParams(page, pageSize, filter, year, selectedCategories) });
  }

  listUsersRecommendedMovies(page = 1, pageSize = this.moviePageSize, filter = '', year = '', selectedCategories: number[] = []): Observable<MoviePage> {
    return this.http.get<MoviePage>(this.usersRecommendedMoviesBase, { params: this.pageParams(page, pageSize, filter, year, selectedCategories) });
  }

  getMovie(imdbId: string): Observable<Movie> {
    return this.http.get<Movie>(`${this.moviesBase}/${imdbId}`);
  }

  getMovieRankHistory(imdbId: string): Observable<MovieRankHistory> {
    return this.http.get<MovieRankHistory>(`${this.moviesBase}/${imdbId}/rank-history`);
  }

  createMovie(movie: Partial<Movie>): Observable<Movie> {
    return this.http.post<Movie>(this.moviesBase, movie);
  }

  createMovieFromSearch(movie: OmdbMovieSearchResult): Observable<Movie> {
    const source = this.movieFromOmdb(movie);
    return this.createMovie({
      imdbId: source.imdbId,
      title: source.title,
      director: source.director,
      writer: source.writer,
      year: source.year,
      poster: source.poster,
      genre: source.genre,
      country: source.country,
      type: source.type
    });
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

  likeMovieFromSearch(movie: OmdbMovieSearchResult): Observable<Movie> {
    return this.recommendMovie(movie.imdbId).pipe(
      catchError(error => error?.status === 404
        ? this.recommendMovieFromSearch(this.movieFromOmdb(movie))
        : throwError(() => error))
    );
  }

  unrecommendMovie(imdbId: string): Observable<Movie> {
    return this.http.delete<Movie>(`${this.moviesBase}/${imdbId}/recommendation`);
  }

  replayMovie(imdbId: string): Observable<Movie> {
    return this.http.post<Movie>(`${this.moviesBase}/${imdbId}/recommendation/replay`, {});
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

  listSuggestedMovieChallenges(
    page = 1,
    pageSize = this.moviePageSize,
    higherRankedFirst = false,
    boostHigherRanks = false,
    moreInterestingFirst = false
  ): Observable<SuggestedMovieChallengePage> {
    const params = this.pageParams(page, pageSize);
    if (higherRankedFirst) {
      params['higherRankedFirst'] = 'true';
    }
    if (boostHigherRanks) {
      params['boostHigherRanks'] = 'true';
    }
    if (moreInterestingFirst) {
      params['moreInterestingFirst'] = 'true';
    }
    return this.http.get<SuggestedMovieChallengePage>(`${this.movieChallengesBase}/suggested`, {
      params
    });
  }

  submitMovieChallengeSelections(selections: MovieChallengeSelection[]): Observable<void> {
    return this.http.post<void>(`${this.movieChallengesBase}/votes/batch`, { selections });
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

  getOmdbMovieById(imdbId: string): Observable<OmdbMovieSearchResult> {
    return this.lookupOmdbById(imdbId).pipe(
      map(movie => {
        if (!movie || !this.successfulOmdbMovie(movie)) {
          throw new Error(this.omdbErrorMessage(movie?.Error, 'OMDb movie was not found'));
        }
        return this.toSearchResult(movie);
      })
    );
  }

  // OMDb has no multi-id batch lookup, so this fires the per-id calls in parallel; any
  // imdbId OMDb can't resolve is skipped (mapped to null) rather than failing the whole batch.
  getOmdbMoviesByIds(imdbIds: string[]): Observable<(OmdbMovieSearchResult | null)[]> {
    return forkJoin(imdbIds.map(id => this.getOmdbMovieById(id).pipe(catchError(() => of(null)))));
  }

  searchOmdbMovies(criteria: OmdbMovieSearchCriteria, page = 1): Observable<OmdbMovieSearchPage> {
    const titleQuery = criteria.title.trim();
    const year = criteria.year.trim();
    const type = criteria.type;
    const exact = criteria.exactTitleMatch;
    const normalizedPage = Math.max(1, Math.floor(page));
    const pageSize = this.omdbSearchPageSize;
    if (exact) {
      const exactMovie$ = normalizedPage === 1
        ? this.lookupOmdbTitle(titleQuery, year, type)
        : of(null);

      return exactMovie$.pipe(
        map(exactMovie => {
          const results = exactMovie && this.successfulOmdbMovie(exactMovie)
            ? [this.toSearchResult(exactMovie)]
            : [];
          return this.toSearchPage(results, criteria, titleQuery, normalizedPage, pageSize, []);
        })
      );
    }

    return this.searchOmdbPage(titleQuery, normalizedPage, type, year).pipe(
      map(primarySearch => {
        const primarySearches = [primarySearch];
        const searchError = this.omdbSearchError(primarySearches);
        if (searchError) {
          throw new Error(searchError);
        }

        const searchItems = this.searchItems(primarySearches);
        const results = searchItems.map(item => this.toSearchResultFromItem(item));
        return this.toSearchPage(results, criteria, titleQuery, normalizedPage, pageSize, primarySearches);
      })
    );
  }

  /** Parse a trailing year only when it is a separate second-or-later word. */
  parseMovieSearch(value: string): ParsedMovieSearch {
    const normalized = value.trim();
    const match = normalized.match(/^(.+) (\d{4})$/);
    return match
      ? { keyword: match[1].trim(), year: match[2] }
      : { keyword: normalized, year: '' };
  }

  /**
   * Filter-box OMDb search: year search, exact title, then broad title search.
   * Each source is capped at OMDb's ten-result page and duplicates keep the
   * position of their first occurrence.
   */
  searchOmdbFromFilter(value: string): Observable<OmdbMovieSearchResult[]> {
    const parsed = this.parseMovieSearch(value);
    if (!parsed.keyword) return of([]);

    const searches: Observable<OmdbMovieSearchPage>[] = [];
    if (parsed.year) {
      searches.push(this.searchOmdbMovies({
        title: parsed.keyword, year: parsed.year, type: 'movie', exactTitleMatch: false
      }));
    }
    searches.push(this.searchOmdbMovies({
      title: parsed.keyword, year: '', type: 'movie', exactTitleMatch: true
    }));
    searches.push(this.searchOmdbMovies({
      title: parsed.keyword, year: '', type: 'movie', exactTitleMatch: false
    }));

    return forkJoin(searches).pipe(map(pages => this.uniqueResults(pages.flatMap(page => page.movies))));
  }

  movieFromOmdb(movie: OmdbMovieSearchResult): RecommendMovieFromSearchRequest {
    return {
      imdbId: movie.imdbId,
      title: movie.englishTitle || movie.originalTitle,
      originalTitle: movie.originalTitle,
      // The backend requires director/writer/year to be non-blank; OMDb sometimes
      // omits them entirely, so fall back to "N/A" rather than sending "".
      director: movie.directors || 'N/A',
      writer: movie.writers || 'N/A',
      year: movie.year || 'N/A',
      country: movie.country,
      genre: movie.genre,
      poster: movie.poster,
      type: movie.type
    };
  }

  private pageParams(page: number, pageSize: number, filter = '', year = '', selectedCategories: number[] = []): Record<string, string> {
    const params: Record<string, string> = {
      page: String(page),
      pageSize: String(pageSize)
    };
    const trimmedFilter = filter.trim();
    if (trimmedFilter) {
      params['filter'] = trimmedFilter;
    }
    const trimmedYear = year.trim();
    if (trimmedYear) {
      params['year'] = trimmedYear;
    }
    if (selectedCategories.length) {
      params['selectedCategories'] = selectedCategories.join(',');
    }
    return params;
  }

  private trimTrailingSlash(value: string): string {
    return value.replace(/\/+$/, '');
  }

  private positiveConfigNumber(value: number | string | undefined, name: string): number {
    const configuredValue = Number(value);
    if (!Number.isFinite(configuredValue) || configuredValue < 1) {
      throw new Error(`${name} must be configured as a positive number in app-config.json`);
    }
    return Math.floor(configuredValue);
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
      catchError(() => throwError(() => new Error('OMDb search failed')))
    );
  }

  private omdbSearchError(searches: OmdbSearchResponse[]): string {
    const failedSearch = searches.find(search =>
      search.Response === 'False'
      && !!search.Error
      && !this.omdbNoSearchResults(search.Error)
    );
    return failedSearch ? this.omdbErrorMessage(failedSearch.Error, 'OMDb search failed') : '';
  }

  private omdbNoSearchResults(error: string): boolean {
    return error.trim().toLowerCase() === 'movie not found!';
  }

  private omdbErrorMessage(error: string | undefined, fallback: string): string {
    if (error && this.omdbRequestLimitExceeded(error)) {
      return 'OMDB request limit exceeded';
    }

    return fallback;
  }

  private omdbRequestLimitExceeded(error: string): boolean {
    const normalizedError = error.trim().toLowerCase();
    return normalizedError.includes('request limit') || normalizedError.includes('limit reached');
  }

  private lookupOmdbById(imdbId: string): Observable<OmdbMovie> {
    const c = this.cfg.config;
    const params = new URLSearchParams({
      apikey: c.omdbApiKey,
      i: imdbId
    });
    return this.http.get<OmdbMovie>(`${c.omdbBaseUrl}?${params.toString()}`).pipe(
      catchError(() => throwError(() => new Error('OMDb movie lookup failed')))
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
      map(movie => {
        if (this.successfulOmdbMovie(movie)) {
          return movie;
        }

        if (movie.Error && this.omdbRequestLimitExceeded(movie.Error)) {
          throw new Error('OMDB request limit exceeded');
        }

        return null;
      }),
      catchError(error => error instanceof Error && error.message === 'OMDB request limit exceeded'
        ? throwError(() => error)
        : of(null)
      )
    );
  }

  private searchItems(primarySearches: OmdbSearchResponse[]): OmdbSearchItem[] {
    const byId = new Map<string, OmdbSearchItem>();
    primarySearches
      .flatMap(search => search.Search ?? [])
      .forEach(item => byId.set(item.imdbID, item));
    return [...byId.values()];
  }

  private toSearchPage(
    results: OmdbMovieSearchResult[],
    criteria: OmdbMovieSearchCriteria,
    titleQuery: string,
    page: number,
    pageSize: number,
    primarySearches: OmdbSearchResponse[]
  ): OmdbMovieSearchPage {
    const uniqueResults = this.uniqueResults(results);
    const matchedResults = criteria.exactTitleMatch
      ? uniqueResults.filter(movie => this.matchesExactSearch(movie, criteria, titleQuery))
      : uniqueResults;
    const scoredResults = matchedResults
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
    const totalResults = criteria.exactTitleMatch
      ? scoredResults.length
      : this.totalSearchResults(primarySearches, uniqueResults.length);
    const pageResults = scoredResults.slice(0, pageSize);

    return {
      movies: pageResults,
      totalResults,
      hasNext: pageResults.length === pageSize && page * pageSize < totalResults
    };
  }

  private matchesExactSearch(
    movie: OmdbMovieSearchResult,
    criteria: OmdbMovieSearchCriteria,
    titleQuery: string
  ): boolean {
    const normalizedTitleQuery = this.normalize(titleQuery);
    const titleMatches = this.normalize(movie.englishTitle) === normalizedTitleQuery
      || this.normalize(movie.originalTitle) === normalizedTitleQuery;
    const yearMatches = !criteria.year || movie.year.includes(criteria.year);
    return titleMatches && yearMatches;
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
      typeDescription: this.movieTypeDescription(type),
      detailsLoaded: true
    };
  }

  private toSearchResultFromItem(item: OmdbSearchItem): OmdbMovieSearchResult {
    const type = this.movieTypeFromOmdb(item.Type);
    return {
      imdbId: item.imdbID,
      originalTitle: '',
      englishTitle: this.cleanOmdbValue(item.Title),
      directors: '',
      writers: '',
      year: this.cleanOmdbValue(item.Year),
      country: '',
      poster: '',
      language: '',
      runtime: '',
      genre: '',
      imdbRating: '',
      type,
      typeDescription: this.movieTypeDescription(type),
      detailsLoaded: false
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
    primarySearches: OmdbSearchResponse[],
    fallbackTotal: number
  ): number {
    const primaryTotal = Math.max(...primarySearches.map(search => Number(search.totalResults ?? 0)), 0);
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
      case 'movie':
      default:
        return 'MOVIE';
    }
  }

  private movieTypeDescription(type: MovieType): string {
    switch (type) {
      case 'SERIES':
        return 'Series';
      case 'MOVIE':
      default:
        return 'Movie';
    }
  }
}
