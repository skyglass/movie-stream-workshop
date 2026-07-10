import { AppConfigService } from './app-config.service';

describe('AppConfigService', () => {
  let originalFetch: typeof fetch;

  const validConfig = {
    apiBaseUrl: 'https://example.test',
    authTokenPath: '/auth/token',
    clientId: 'movies-ui',
    keycloakBaseUrl: 'https://example.test/keycloak',
    keycloakRealm: 'movies',
    uiBaseUrl: 'https://example.test',
    moviesApiPath: '/api/movies/movies',
    movieChallengesPath: '/api/movies/movie-challenges',
    favoriteMoviesPath: '/api/movies/favorite-movies',
    publicFavoriteMoviesPath: '/api/movies/my-favorite-movies',
    usersFavoriteMoviesPath: '/api/movies/users-favorite-movies',
    usersRecommendedMoviesPath: '/api/movies/users-recommended-movies',
    moviesPerPage: 50,
    userExtrasPath: '/api/movies/userextras',
    usersApiPath: '/api/movies/users',
    omdbBaseUrl: 'https://www.omdbapi.com/',
    omdbApiKey: '',
    pricingApiPath: '',
    pricingEventsPath: '',
    competitorApiPath: '',
    accountApiPath: '',
    transferApiPath: '',
    wsPath: '/api/movies/ws'
  };

  beforeEach(() => {
    originalFetch = globalThis.fetch;
  });

  afterEach(() => {
    globalThis.fetch = originalFetch;
  });

  it('loads valid runtime configuration', async () => {
    globalThis.fetch = jasmine.createSpy('fetch').and.resolveTo({
      ok: true,
      json: () => Promise.resolve(validConfig)
    } as Response);

    const service = new AppConfigService();
    await service.load();

    expect(service.config.publicFavoriteMoviesPath).toBe('/api/movies/my-favorite-movies');
  });

  it('fails when the public favorite movies API path is missing', async () => {
    const config = { ...validConfig };
    delete (config as Partial<typeof validConfig>).publicFavoriteMoviesPath;
    globalThis.fetch = jasmine.createSpy('fetch').and.resolveTo({
      ok: true,
      json: () => Promise.resolve(config)
    } as Response);

    const service = new AppConfigService();

    await expectAsync(service.load()).toBeRejectedWithError(
      'app-config.json is missing required config values: publicFavoriteMoviesPath'
    );
  });
});
