import { Routes } from '@angular/router';
import { MoviesHomeComponent } from './components/movies-home/movies-home';
import { MovieSearchComponent } from './components/movie-search/movie-search';
import { MovieChallengePageComponent } from './components/movie-challenge-page/movie-challenge-page';
import { MovieDetailComponent } from './components/movie-detail/movie-detail';
import { MovieEditComponent } from './components/movie-edit/movie-edit';
import { MovieWizardComponent } from './components/movie-wizard/movie-wizard';
import { FavoriteMoviesComponent } from './components/favorite-movies/favorite-movies';
import { UsersFavoriteMoviesComponent } from './components/users-favorite-movies/users-favorite-movies';
import { UsersRecommendedMoviesComponent } from './components/users-recommended-movies/users-recommended-movies';
import { AdminLayoutComponent } from './components/admin-layout/admin-layout';
import { AdminUsersComponent } from './components/admin-users/admin-users';
import { ProfileComponent } from './components/profile/profile';
import { adminGuard, authGuard } from './services/auth-guard';

export const routes: Routes = [
  { path: 'home', component: MoviesHomeComponent },
  { path: 'search', component: MovieSearchComponent },
  { path: 'movie-challenge', component: MovieChallengePageComponent, canActivate: [authGuard] },
  { path: 'movies/:imdbId/edit', component: MovieEditComponent, canActivate: [adminGuard] },
  { path: 'movies/:imdbId', component: MovieDetailComponent, canActivate: [authGuard] },
  { path: 'wizard', component: MovieWizardComponent, canActivate: [adminGuard] },
  { path: 'favorites', component: FavoriteMoviesComponent, canActivate: [authGuard] },
  { path: 'my-favorite-movies/:username', component: FavoriteMoviesComponent },
  { path: 'users-favorites', component: UsersFavoriteMoviesComponent, canActivate: [authGuard] },
  { path: 'users-recommended', component: UsersRecommendedMoviesComponent, canActivate: [authGuard] },
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
