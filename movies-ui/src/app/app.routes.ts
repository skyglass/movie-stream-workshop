import { Routes } from '@angular/router';
import { MoviesHomeComponent } from './components/movies-home/movies-home';
import { MovieDetailComponent } from './components/movie-detail/movie-detail';
import { MovieWizardComponent } from './components/movie-wizard/movie-wizard';
import { AdminUsersComponent } from './components/admin-users/admin-users';
import { adminGuard, authGuard } from './services/auth-guard';

export const routes: Routes = [
  { path: 'home', component: MoviesHomeComponent },
  { path: 'movies/:imdbId', component: MovieDetailComponent },
  { path: 'wizard', component: MovieWizardComponent, canActivate: [authGuard] },
  { path: 'admin/users', component: AdminUsersComponent, canActivate: [adminGuard] },
  { path: '', redirectTo: '/home', pathMatch: 'full' },
  { path: '**', redirectTo: '/home' }
];
