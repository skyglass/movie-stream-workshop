import { Routes, UrlMatchResult, UrlSegment } from '@angular/router';
import { MoviesHomeComponent } from './components/movies-home/movies-home';
import { MovieChallengePageComponent } from './components/movie-challenge-page/movie-challenge-page';
import { MovieDetailComponent } from './components/movie-detail/movie-detail';
import { MovieEditComponent } from './components/movie-edit/movie-edit';
import { MovieWizardComponent } from './components/movie-wizard/movie-wizard';
import { FavoriteMoviesComponent } from './components/favorite-movies/favorite-movies';
import { UsersRecommendedMoviesComponent } from './components/users-recommended-movies/users-recommended-movies';
import { SimilarFavoriteMoviesComponent } from './components/similar-favorite-movies/similar-favorite-movies';
import { SimilarMoviesComponent } from './components/similar-movies/similar-movies';
import { AdminLayoutComponent } from './components/admin-layout/admin-layout';
import { AdminUsersComponent } from './components/admin-users/admin-users';
import { ProfileComponent } from './components/profile/profile';
import { adminGuard, authGuard, movieEditGuard } from './services/auth-guard';
import { MovieGuidesComponent } from './components/movie-guides/movie-guides';
import { MovieGuideDetailComponent } from './components/movie-guide-detail/movie-guide-detail';
import { GuideSimilarMoviesComponent } from './components/guide-similar-movies/guide-similar-movies';
import { WatchlistDetailComponent } from './components/watchlist-detail/watchlist-detail';
import { MyWatchlistsPageComponent } from './components/my-watchlists-page/my-watchlists-page';

// Matches 'categories/root' (nothing selected) or 'categories/<id>/<any trailing segments>' -- the trailing
// segments are a cosmetic, human-readable category path (e.g. 'Movies/Action/Neo-Noir') built by
// categoryPageSegments; resolution is always by the numeric id, so their exact content is never validated here.
export function categoryPageMatcher(segments: UrlSegment[]): UrlMatchResult | null {
  if (segments.length < 2 || segments[0].path !== 'categories') return null;
  if (segments[1].path === 'root') return { consumed: segments, posParams: {} };
  if (/^\d+$/.test(segments[1].path)) return { consumed: segments, posParams: { id: segments[1] } };
  return null;
}

// Matches 'movie-guides/<rootId>' (nothing selected) or 'movie-guides/<rootId>/<subCategoryId>/<cosmetic slugs>'.
// The third segment must be all-digits to match here, so 'movie-guides/<id>/similar' (a literal, non-numeric third
// segment) never matches and instead falls through to the dedicated 'movie-guides/:id/similar' route below.
export function movieGuideDetailMatcher(segments: UrlSegment[]): UrlMatchResult | null {
  if (segments.length < 2 || segments[0].path !== 'movie-guides') return null;
  if (!/^\d+$/.test(segments[1].path)) return null;
  if (segments.length === 2) return { consumed: segments, posParams: { id: segments[1] } };
  if (/^\d+$/.test(segments[2].path)) return { consumed: segments, posParams: { id: segments[1], subCategoryId: segments[2] } };
  return null;
}

// Same shape as movieGuideDetailMatcher for 'my-watchlists/<rootId>[/<subCategoryId>/<cosmetic slugs>]'.
export function watchlistDetailMatcher(segments: UrlSegment[]): UrlMatchResult | null {
  if (segments.length < 2 || segments[0].path !== 'my-watchlists') return null;
  if (!/^\d+$/.test(segments[1].path)) return null;
  if (segments.length === 2) return { consumed: segments, posParams: { id: segments[1] } };
  if (/^\d+$/.test(segments[2].path)) return { consumed: segments, posParams: { id: segments[1], subCategoryId: segments[2] } };
  return null;
}

export const routes: Routes = [
  // Lazy-loaded (not a static import above): this is a newly-added page, and eagerly bundling it into main.js
  // pushed the initial chunk over the production budget (angular.json) -- every other route here is small/old
  // enough to already fit, so this is the one that needs to be the exception rather than raising the budget.
  {
    matcher: categoryPageMatcher,
    loadComponent: () => import('./components/movie-category-page/movie-category-page').then(m => m.MovieCategoryPageComponent)
  },
  { path: 'home', component: MoviesHomeComponent },
  { path: 'movie-guides', component: MovieGuidesComponent },
  { path: 'movie-guides/:id/similar', component: GuideSimilarMoviesComponent },
  { matcher: movieGuideDetailMatcher, component: MovieGuideDetailComponent },
  { path: 'movie-challenge', component: MovieChallengePageComponent, canActivate: [authGuard] },
  { path: 'movies/:imdbId/edit', component: MovieEditComponent, canActivate: [movieEditGuard] },
  { path: 'movies/:imdbId/similar', component: SimilarMoviesComponent },
  { path: 'movies/:imdbId', component: MovieDetailComponent },
  { path: 'wizard', component: MovieWizardComponent, canActivate: [adminGuard] },
  { path: 'favorites/similar', component: SimilarFavoriteMoviesComponent, canActivate: [authGuard] },
  { path: 'favorites', component: FavoriteMoviesComponent },
  { path: 'my-favorite-movies/:username/similar', component: SimilarFavoriteMoviesComponent },
  { path: 'my-favorite-movies/:username', component: FavoriteMoviesComponent },
  // No guard: MyWatchlistsSectionComponent already renders its own signed-out "Sign in" prompt, same as the
  // Movie Guides page's panels do -- matches the previous (unguarded) Movie Journeys page this replaces.
  { path: 'my-watchlists', component: MyWatchlistsPageComponent },
  { matcher: watchlistDetailMatcher, component: WatchlistDetailComponent, canActivate: [authGuard] },
  { path: 'users-recommended', component: UsersRecommendedMoviesComponent },
  { path: 'my-recommended-movies/:username', component: UsersRecommendedMoviesComponent },
  { path: 'profile', component: ProfileComponent, canActivate: [authGuard] },
  {
    path: 'admin',
    component: AdminLayoutComponent,
    canActivate: [adminGuard],
    children: [
      { path: 'users', component: AdminUsersComponent },
      { path: '', redirectTo: 'users', pathMatch: 'full' }
    ]
  },
  { path: '', redirectTo: '/home', pathMatch: 'full' },
  { path: '**', redirectTo: '/home' }
];
