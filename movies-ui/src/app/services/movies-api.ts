import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { map, Observable } from 'rxjs';
import { AppConfigService } from '../config/app-config.service';

export interface MovieComment {
  username: string;
  avatar: string;
  text: string;
  timestamp: string;
}

export interface Movie {
  imdbId: string;
  title: string;
  director: string;
  year: string;
  poster: string;
  recommended: boolean;
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
  Year: string;
  Poster: string;
  Response: string;
  Error?: string;
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

  unrecommendMovie(imdbId: string): Observable<Movie> {
    return this.http.delete<Movie>(`${this.moviesBase}/${imdbId}/recommendation`);
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

  private pageParams(page: number, pageSize: number): Record<string, string> {
    return {
      page: String(page),
      pageSize: String(pageSize)
    };
  }
}
