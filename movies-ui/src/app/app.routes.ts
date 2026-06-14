import { Routes } from '@angular/router';
import { MoviesHomeComponent } from './components/movies-home/movies-home';
import { MovieDetailComponent } from './components/movie-detail/movie-detail';
import { MovieWizardComponent } from './components/movie-wizard/movie-wizard';
import { FavoriteMoviesComponent } from './components/favorite-movies/favorite-movies';
import { UsersFavoriteMoviesComponent } from './components/users-favorite-movies/users-favorite-movies';
import { AdminLayoutComponent } from './components/admin-layout/admin-layout';
import { AdminUsersComponent } from './components/admin-users/admin-users';
import { ProfileComponent } from './components/profile/profile';
import { adminGuard, authGuard } from './services/auth-guard';

export const routes: Routes = [
  { path: 'home', component: MoviesHomeComponent },
  { path: 'movies/:imdbId', component: MovieDetailComponent },
  { path: 'wizard', component: MovieWizardComponent, canActivate: [authGuard] },
  { path: 'favorites', component: FavoriteMoviesComponent, canActivate: [authGuard] },
  { path: 'users-favorites', component: UsersFavoriteMoviesComponent, canActivate: [authGuard] },
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
