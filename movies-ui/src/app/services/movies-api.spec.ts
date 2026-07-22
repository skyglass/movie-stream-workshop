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
    publicFavoriteMoviesPath: '/my-favorite-movies',
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

  it('parses only a separate trailing four-digit year after a keyword', () => {
    expect(service.parseMovieSearch('1984')).toEqual({ keyword: '1984', year: '' });
    expect(service.parseMovieSearch('1984 1984')).toEqual({ keyword: '1984', year: '1984' });
    expect(service.parseMovieSearch('The Matrix 1999')).toEqual({ keyword: 'The Matrix', year: '1999' });
    expect(service.parseMovieSearch('The Matrix1999')).toEqual({ keyword: 'The Matrix1999', year: '' });
    expect(service.parseMovieSearch('The Matrix 199x')).toEqual({ keyword: 'The Matrix 199x', year: '' });
    expect(service.parseMovieSearch('The Matrix\t1999')).toEqual({ keyword: 'The Matrix\t1999', year: '' });
  });

  it('combines year, exact-title and broad OMDb filter searches in order without duplicates', (done) => {
    service.searchOmdbFromFilter('Matrix 1999').subscribe(movies => {
      expect(movies.map(movie => movie.imdbId)).toEqual(['tt-year', 'tt-exact', 'tt-broad']);
      done();
    });

    const requests = http.match(req => req.urlWithParams.startsWith(appConfig.omdbBaseUrl));
    expect(requests.length).toBe(3);
    requests.forEach(request => {
      const url = new URL(request.request.urlWithParams);
      if (url.searchParams.get('y') === '1999') {
        request.flush({ Search: [omdbItem('tt-year', 'Matrix', '1999')], totalResults: '1', Response: 'True' });
      } else if (url.searchParams.has('t')) {
        request.flush({
          imdbID: 'tt-exact', Title: 'Matrix', Director: 'Director', Writer: 'Writer', Year: '2000',
          Poster: 'N/A', Type: 'movie', Response: 'True'
        });
      } else {
        request.flush({
          Search: [omdbItem('tt-year', 'Matrix', '1999'), omdbItem('tt-broad', 'Matrix Reloaded', '2003')],
          totalResults: '2', Response: 'True'
        });
      }
    });
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

  it('URL-encodes usernames when requesting public favorite movies', (done) => {
    service.listPublicFavoriteMovies('sky composer', 2, 5).subscribe(page => {
      expect(page.movies).toEqual([]);
      expect(page.totalCount).toBe(0);
      done();
    });

    const request = http.expectOne(`${appConfig.apiBaseUrl}${appConfig.publicFavoriteMoviesPath}/sky%20composer?page=2&pageSize=5`);
    request.flush({ movies: [], totalCount: 0 });
  });

  it('sends trimmed filters for paginated movie list endpoints', (done) => {
    let completedRequests = 0;
    const completeRequest = () => {
      completedRequests++;
      if (completedRequests === 5) {
        done();
      }
    };

    service.listMovies(2, 5, ' matrix ').subscribe(() => completeRequest());
    service.listFavoriteMovies(2, 5, ' matrix ').subscribe(() => completeRequest());
    service.listPublicFavoriteMovies('sky composer', 2, 5, ' matrix ').subscribe(() => completeRequest());
    service.listUsersFavoriteMovies(2, 5, ' matrix ').subscribe(() => completeRequest());
    service.listUsersRecommendedMovies(2, 5, ' matrix ').subscribe(() => completeRequest());

    [
      `${appConfig.apiBaseUrl}${appConfig.moviesApiPath}`,
      `${appConfig.apiBaseUrl}${appConfig.favoriteMoviesPath}`,
      `${appConfig.apiBaseUrl}${appConfig.publicFavoriteMoviesPath}/sky%20composer`,
      `${appConfig.apiBaseUrl}${appConfig.usersFavoriteMoviesPath}`,
      `${appConfig.apiBaseUrl}${appConfig.usersRecommendedMoviesPath}`
    ].forEach(url => {
      const request = http.expectOne(req =>
        req.url === url
        && req.params.get('page') === '2'
        && req.params.get('pageSize') === '5'
        && req.params.get('filter') === 'matrix');
      request.flush({ movies: [], totalCount: 0 });
    });
  });

  it('submits only the loaded favorite ranking prefix', (done) => {
    service.submitFavoriteMoviesRanking(['tt2', 'tt1']).subscribe(() => done());

    const request = http.expectOne(`${appConfig.apiBaseUrl}${appConfig.favoriteMoviesPath}/ranking`);
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ orderedImdbIds: ['tt2', 'tt1'] });
    request.flush(null);
  });

  it('sends the parsed year separately when listing catalog movies', (done) => {
    service.listMovies(1, 20, 'The Matrix', '1999').subscribe(() => done());

    const request = http.expectOne(req =>
      req.url === `${appConfig.apiBaseUrl}${appConfig.moviesApiPath}`
      && req.params.get('filter') === 'The Matrix'
      && req.params.get('year') === '1999');
    request.flush({ movies: [], totalCount: 0 });
  });

  it('requests similar to favorite movies', (done) => {
    service.listSimilarToFavoriteMovies(2, 5, ' matrix ').subscribe(page => {
      expect(page.movies).toEqual([]);
      expect(page.totalCount).toBe(0);
      done();
    });

    const request = http.expectOne(req =>
      req.url === `${appConfig.apiBaseUrl}${appConfig.favoriteMoviesPath}/similar`
      && req.params.get('page') === '2'
      && req.params.get('pageSize') === '5'
      && req.params.get('filter') === 'matrix');
    expect(request.request.method).toBe('GET');
    request.flush({ movies: [], totalCount: 0 });
  });

  it('requests movies similar to a given movie', (done) => {
    service.listSimilarMovies('tt101', 2, 5, ' matrix ').subscribe(page => {
      expect(page.movies).toEqual([]);
      expect(page.totalCount).toBe(0);
      done();
    });

    const request = http.expectOne(req =>
      req.url === `${appConfig.apiBaseUrl}${appConfig.moviesApiPath}/tt101/similar-movies`
      && req.params.get('page') === '2'
      && req.params.get('pageSize') === '5'
      && req.params.get('filter') === 'matrix');
    expect(request.request.method).toBe('GET');
    request.flush({ movies: [], totalCount: 0 });
  });

  it('requests paginated suggested movie challenges', (done) => {
    service.listSuggestedMovieChallenges(3, 7).subscribe(page => {
      expect(page.challenges).toEqual([]);
      expect(page.totalCount).toBe(0);
      done();
    });

    const request = http.expectOne(`${appConfig.apiBaseUrl}${appConfig.movieChallengesPath}/suggested?page=3&pageSize=7`);
    request.flush({ challenges: [], totalCount: 0 });
  });

  it('requests suggested movie challenges with higher ranked first ordering', (done) => {
    service.listSuggestedMovieChallenges(3, 7, true).subscribe(page => {
      expect(page.challenges).toEqual([]);
      expect(page.totalCount).toBe(0);
      done();
    });

    const request = http.expectOne(
      `${appConfig.apiBaseUrl}${appConfig.movieChallengesPath}/suggested?page=3&pageSize=7&higherRankedFirst=true`
    );
    request.flush({ challenges: [], totalCount: 0 });
  });

  it('requests suggested movie challenges with higher ranks boosted', (done) => {
    service.listSuggestedMovieChallenges(3, 7, false, true).subscribe(page => {
      expect(page.challenges).toEqual([]);
      expect(page.totalCount).toBe(0);
      done();
    });

    const request = http.expectOne(
      `${appConfig.apiBaseUrl}${appConfig.movieChallengesPath}/suggested?page=3&pageSize=7&boostHigherRanks=true`
    );
    request.flush({ challenges: [], totalCount: 0 });
  });

  it('requests suggested movie challenges with more interesting pairs first', (done) => {
    service.listSuggestedMovieChallenges(3, 7, false, false, true).subscribe(page => {
      expect(page.challenges).toEqual([]);
      expect(page.totalCount).toBe(0);
      done();
    });

    const request = http.expectOne(
      `${appConfig.apiBaseUrl}${appConfig.movieChallengesPath}/suggested?page=3&pageSize=7&moreInterestingFirst=true`
    );
    request.flush({ challenges: [], totalCount: 0 });
  });

  it('submits selected movie challenges in a batch', (done) => {
    service.submitMovieChallengeSelections([
      { movie1Id: 'tt101', movie2Id: 'tt102', selectedMovieId: 'tt101' },
      { movie1Id: 'tt103', movie2Id: 'tt104', selectedMovieId: 'tt104' }
    ]).subscribe(() => {
      done();
    });

    const request = http.expectOne(`${appConfig.apiBaseUrl}${appConfig.movieChallengesPath}/votes/batch`);
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({
      selections: [
        { movie1Id: 'tt101', movie2Id: 'tt102', selectedMovieId: 'tt101' },
        { movie1Id: 'tt103', movie2Id: 'tt104', selectedMovieId: 'tt104' }
      ]
    });
    request.flush(null);
  });

  it('requests movie replay through recommendation replay endpoint', (done) => {
    service.replayMovie('tt101').subscribe(movie => {
      expect(movie.imdbId).toBe('tt101');
      expect(movie.recommended).toBeTrue();
      done();
    });

    const request = http.expectOne(`${appConfig.apiBaseUrl}${appConfig.moviesApiPath}/tt101/recommendation/replay`);
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({});
    request.flush({
      imdbId: 'tt101',
      title: 'Replay Movie',
      director: 'N/A',
      writer: 'N/A',
      year: 'N/A',
      poster: '',
      genre: '',
      country: '',
      type: 'MOVIE',
      typeDescription: 'Movie',
      recommended: true,
      disliked: false,
      rankPosition: null,
      rating: null,
      comments: []
    });
  });

  it('builds the public favorite movies share URL from the configured UI base URL', () => {
    expect(service.favoriteMoviesShareUrl({
      myFavoriteMoviesPublic: true,
      encodedUsername: 'sky%20composer',
      sharePath: '/my-favorite-movies/sky%20composer'
    })).toBe('https://ui.example.test/my-favorite-movies/sky%20composer');
  });

  function omdbItem(imdbID: string, Title: string, Year: string) {
    return { imdbID, Title, Year, Type: 'movie', Poster: 'N/A' };
  }
});
