import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
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
  comments: MovieComment[];
}

export interface MovieUser {
  username: string;
  email: string;
  avatar: string;
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
  private readonly userExtrasBase: string;
  private readonly usersBase: string;

  constructor(private http: HttpClient, private cfg: AppConfigService) {
    const c = cfg.config;
    this.moviesBase = `${c.apiBaseUrl}${c.moviesApiPath}`;
    this.userExtrasBase = `${c.apiBaseUrl}${c.userExtrasPath}`;
    this.usersBase = `${c.apiBaseUrl}${c.usersApiPath}`;
  }

  listMovies(): Observable<Movie[]> {
    return this.http.get<Movie[]>(this.moviesBase);
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
}
