export interface AppConfig {
  apiBaseUrl: string;
  authTokenPath: string;
  clientId: string;
  keycloakBaseUrl: string;
  keycloakRealm: string;
  uiBaseUrl: string;
  moviesApiPath: string;
  movieChallengesPath: string;
  favoriteMoviesPath: string;
  usersFavoriteMoviesPath: string;
  usersRecommendedMoviesPath: string;
  moviesPerPage?: number | string;
  userExtrasPath: string;
  usersApiPath: string;
  omdbBaseUrl: string;
  omdbApiKey: string;
  pricingApiPath: string;
  pricingEventsPath: string;
  competitorApiPath: string;
  accountApiPath: string;
  transferApiPath: string;
  wsPath: string;
}
