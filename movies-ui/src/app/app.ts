import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthPanel } from './components/auth-panel/auth-panel';
import { AuthService } from './services/auth';
import { MoviesApiService } from './services/movies-api';

@Component({
  selector: 'app-root',
  imports: [
    CommonModule,
    RouterOutlet,
    RouterLink,
    RouterLinkActive,
    AuthPanel
  ],
  templateUrl: './app.html',
  styleUrls: ['./app.css']
})
export class App implements OnInit {
  readonly auth = inject(AuthService);
  private readonly moviesApi = inject(MoviesApiService);

  ngOnInit(): void {
    this.auth.isAuthenticated$.subscribe(authenticated => {
      if (authenticated) {
        this.moviesApi.syncMe().subscribe({
          next: profile => this.auth.applyProfile(profile),
          error: () => undefined
        });
      }
    });
  }

}
