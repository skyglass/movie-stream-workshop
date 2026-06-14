import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthPanel } from './components/auth-panel/auth-panel';
import { AuthService } from './services/auth';
import { MoviesApiService } from './services/movies-api';
import { MovieChallengeDialogComponent } from './components/movie-challenge-dialog/movie-challenge-dialog';
import { MovieChallengeSocketService } from './services/movie-challenge-socket';
import { Subscription } from 'rxjs';

@Component({
  selector: 'app-root',
  imports: [
    CommonModule,
    RouterOutlet,
    RouterLink,
    RouterLinkActive,
    AuthPanel,
    MovieChallengeDialogComponent
  ],
  templateUrl: './app.html',
  styleUrls: ['./app.css']
})
export class App implements OnInit, OnDestroy {
  readonly auth = inject(AuthService);
  private readonly moviesApi = inject(MoviesApiService);
  private readonly movieChallengeSocket = inject(MovieChallengeSocketService);
  private readonly subscriptions: Subscription[] = [];
  private toastTimer?: ReturnType<typeof setTimeout>;

  movieChallengeOpen = false;
  movieChallengeAvailable = false;
  movieChallengeAvailabilityVersion = 0;
  movieChallengeToastVisible = false;

  ngOnInit(): void {
    this.subscriptions.push(this.auth.isAuthenticated$.subscribe(authenticated => {
      if (authenticated) {
        this.moviesApi.syncMe().subscribe({
          next: profile => {
            this.auth.applyProfile(profile);
            this.movieChallengeSocket.connect(profile.username);
          },
          error: () => {
            const username = this.auth.currentUser?.username;
            if (username) {
              this.movieChallengeSocket.connect(username);
            }
          }
        });
      } else {
        this.movieChallengeSocket.disconnect();
        this.movieChallengeOpen = false;
        this.movieChallengeAvailable = false;
        this.hideMovieChallengeToast();
      }
    }));

    this.subscriptions.push(this.movieChallengeSocket.availability$.subscribe(available => {
      if (!available) return;

      this.movieChallengeAvailable = true;
      this.movieChallengeAvailabilityVersion += 1;
      this.showMovieChallengeToast();
    }));
  }

  ngOnDestroy(): void {
    this.subscriptions.forEach(subscription => subscription.unsubscribe());
    this.hideMovieChallengeToast();
    this.movieChallengeSocket.disconnect();
  }

  openMovieChallenge(): void {
    this.movieChallengeOpen = true;
    this.movieChallengeAvailable = false;
    this.movieChallengeSocket.clearAvailability();
    this.hideMovieChallengeToast();
  }

  closeMovieChallenge(): void {
    this.movieChallengeOpen = false;
  }

  markMovieChallengeConsumed(): void {
    this.movieChallengeAvailable = false;
    this.movieChallengeSocket.clearAvailability();
    this.hideMovieChallengeToast();
  }

  private showMovieChallengeToast(): void {
    this.movieChallengeToastVisible = true;
    if (this.toastTimer) {
      clearTimeout(this.toastTimer);
    }
    this.toastTimer = setTimeout(() => {
      this.movieChallengeToastVisible = false;
      this.toastTimer = undefined;
    }, 5000);
  }

  private hideMovieChallengeToast(): void {
    this.movieChallengeToastVisible = false;
    if (this.toastTimer) {
      clearTimeout(this.toastTimer);
      this.toastTimer = undefined;
    }
  }

}
