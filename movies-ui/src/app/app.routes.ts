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
import { MovieCoursesComponent } from './components/movie-courses/movie-courses';
import { MovieCourseDetailComponent } from './components/movie-course-detail/movie-course-detail';
import { MovieCourseEditComponent } from './components/movie-course-edit/movie-course-edit';
import { MovieGuidesComponent } from './components/movie-guides/movie-guides';
import { MovieGuideDetailComponent } from './components/movie-guide-detail/movie-guide-detail';
import { GuideSimilarMoviesComponent } from './components/guide-similar-movies/guide-similar-movies';
import { WatchlistDetailComponent } from './components/watchlist-detail/watchlist-detail';

// Matches 'categories/root' (nothing selected) or 'categories/<id>/<any trailing segments>' -- the trailing
// segments are a cosmetic, human-readable category path (e.g. 'Movies/Action/Neo-Noir') built by
// categoryPageSegments; resolution is always by the numeric id, so their exact content is never validated here.
export function categoryPageMatcher(segments: UrlSegment[]): UrlMatchResult | null {
  if (segments.length < 2 || segments[0].path !== 'categories') return null;
  if (segments[1].path === 'root') return { consumed: segments, posParams: {} };
  if (/^\d+$/.test(segments[1].path)) return { consumed: segments, posParams: { id: segments[1] } };
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
  { path: 'movie-guides/:id', component: MovieGuideDetailComponent },
  { path: 'movie-challenge', component: MovieChallengePageComponent, canActivate: [authGuard] },
  { path: 'movies/:imdbId/edit', component: MovieEditComponent, canActivate: [movieEditGuard] },
  { path: 'movies/:imdbId/similar', component: SimilarMoviesComponent },
  { path: 'movies/:imdbId', component: MovieDetailComponent },
  { path: 'wizard', component: MovieWizardComponent, canActivate: [adminGuard] },
  { path: 'favorites/similar', component: SimilarFavoriteMoviesComponent, canActivate: [authGuard] },
  { path: 'favorites', component: FavoriteMoviesComponent },
  { path: 'my-favorite-movies/:username/similar', component: SimilarFavoriteMoviesComponent },
  { path: 'my-favorite-movies/:username', component: FavoriteMoviesComponent },
  { path: 'movie-journeys', component: MovieCoursesComponent },
  { path: 'my-watchlists/:id', component: WatchlistDetailComponent, canActivate: [authGuard] },
  { path: 'movie-journeys/:id', component: MovieCourseDetailComponent },
  { path: 'movie-journeys/:id/edit', component: MovieCourseEditComponent, canActivate: [authGuard] },
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
