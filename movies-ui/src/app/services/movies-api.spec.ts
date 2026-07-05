import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { AppConfig } from '../config/app-config.model';
import { AppConfigService } from '../config/app-config.service';
import { MoviesApiService, OmdbMovieSearchCriteria } from './movies-api';

describe('MoviesApiService', () => {
  const appConfig: AppConfig = {
    apiBaseUrl: 'https://api.example.test',
    authTokenPath: '/auth/token',
    clientId: 'movies-ui',
    keycloakBaseUrl: 'https://keycloak.example.test',
    keycloakRealm: 'movies',
    uiBaseUrl: 'https://ui.example.test',
    moviesApiPath: '/movies',
    movieChallengesPath: '/movie-challenges',
    favoriteMoviesPath: '/favorite-movies',
    usersFavoriteMoviesPath: '/users-favorite-movies',
    usersRecommendedMoviesPath: '/users-recommended-movies',
    moviesPerPage: 12,
    userExtrasPath: '/user-extras',
    usersApiPath: '/users',
    omdbBaseUrl: 'https://omdb.example.test/',
    omdbApiKey: 'omdb-key',
    pricingApiPath: '/pricing',
    pricingEventsPath: '/pricing/events',
    competitorApiPath: '/competitors',
    accountApiPath: '/accounts',
    transferApiPath: '/transfers',
    wsPath: '/ws'
  };
  const exactPiCriteria: OmdbMovieSearchCriteria = {
    title: 'Pi',
    year: '1998',
    type: 'movie',
    exactTitleMatch: true
  };
  const broadPiCriteria: OmdbMovieSearchCriteria = {
    title: 'Pi',
    year: '',
    type: 'movie',
    exactTitleMatch: false
  };

  let service: MoviesApiService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        MoviesApiService,
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: AppConfigService, useValue: { config: appConfig } }
      ]
    });

    service = TestBed.inject(MoviesApiService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
  });

  it('uses only OMDb title lookup for exact-title searches', (done) => {
    service.searchOmdbMovies(exactPiCriteria).subscribe(page => {
      expect(page.totalResults).toBe(1);
      expect(page.hasNext).toBeFalse();
      expect(page.movies.map(movie => movie.imdbId)).toEqual(['tt0138704']);
      expect(page.movies[0]?.englishTitle).toBe('Pi');
      done();
    });

    const request = http.expectOne(req => req.urlWithParams.startsWith(appConfig.omdbBaseUrl));
    const requestUrl = new URL(request.request.urlWithParams);
    expect(requestUrl.searchParams.get('apikey')).toBe(appConfig.omdbApiKey);
    expect(requestUrl.searchParams.get('t')).toBe('Pi');
    expect(requestUrl.searchParams.get('y')).toBe('1998');
    expect(requestUrl.searchParams.get('type')).toBe('movie');
    expect(requestUrl.searchParams.has('s')).toBeFalse();

    request.flush({
      imdbID: 'tt0138704',
      Title: 'Pi',
      Director: 'Darren Aronofsky',
      Writer: 'Darren Aronofsky',
      Year: '1998',
      Poster: 'N/A',
      Country: 'United States',
      Language: 'English',
      Genre: 'Drama, Horror, Mystery',
      Runtime: '84 min',
      imdbRating: '7.3',
      Type: 'movie',
      Response: 'True'
    });
  });

  it('returns an empty exact-title page when OMDb title lookup has no match', (done) => {
    service.searchOmdbMovies(exactPiCriteria).subscribe(page => {
      expect(page.movies).toEqual([]);
      expect(page.totalResults).toBe(0);
      expect(page.hasNext).toBeFalse();
      done();
    });

    const request = http.expectOne(req => req.urlWithParams.startsWith(appConfig.omdbBaseUrl));
    request.flush({
      Response: 'False',
      Error: 'Movie not found!'
    });
  });

  it('surfaces OMDb request limit failures for exact-title lookup', (done) => {
    service.searchOmdbMovies(exactPiCriteria).subscribe({
      next: () => fail('expected exact lookup to fail'),
      error: (error: unknown) => {
        expect(error).toEqual(jasmine.any(Error));
        expect((error as Error).message).toBe('OMDB request limit exceeded');
        done();
      }
    });

    const request = http.expectOne(req => req.urlWithParams.startsWith(appConfig.omdbBaseUrl));
    request.flush({
      Response: 'False',
      Error: 'Request limit reached!'
    });
  });

  it('uses one OMDb search request for each broad-search page', (done) => {
    service.searchOmdbMovies(broadPiCriteria, 2).subscribe(page => {
      expect(page.movies.map(movie => movie.imdbId)).toEqual([
        'tt0000000',
        'tt0000001',
        'tt0000002',
        'tt0000003',
        'tt0000004',
        'tt0000005',
        'tt0000006',
        'tt0000007',
        'tt0000008',
        'tt0000009'
      ]);
      expect(page.hasNext).toBeTrue();
      done();
    });

    const request = http.expectOne(req => req.urlWithParams.startsWith(appConfig.omdbBaseUrl));
    const requestUrl = new URL(request.request.urlWithParams);
    expect(requestUrl.searchParams.get('s')).toBe('Pi');
    expect(requestUrl.searchParams.get('page')).toBe('2');
    expect(requestUrl.searchParams.has('t')).toBeFalse();

    request.flush({
      Search: Array.from({ length: 10 }, (_, index) => ({
        imdbID: `tt000000${index}`,
        Title: `Pi Result ${index}`,
        Year: '1998',
        Type: 'movie',
        Poster: 'N/A'
      })),
      totalResults: '25',
      Response: 'True'
    });
  });
});
