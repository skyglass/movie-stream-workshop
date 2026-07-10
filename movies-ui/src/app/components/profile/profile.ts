import { CommonModule } from '@angular/common';
import { Component, OnInit, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { AuthService } from '../../services/auth';
import { FavoriteMoviesShare, MovieUser, MoviesApiService } from '../../services/movies-api';

@Component({
  standalone: true,
  selector: 'app-profile',
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule
  ],
  templateUrl: './profile.html',
  styleUrl: './profile.css'
})
export class ProfileComponent implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly moviesApi = inject(MoviesApiService);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  profile: MovieUser | null = null;
  originalAvatar = '';
  loading = false;
  saving = false;
  imageLoading = false;
  privacyLoading = false;
  privacySaving = false;
  errorMessage = '';
  savedMessage = '';
  privacyErrorMessage = '';
  privacySavedMessage = '';
  favoriteMoviesShare: FavoriteMoviesShare | null = null;

  readonly avatarForm = this.fb.group({
    avatar: ['', Validators.required]
  });

  ngOnInit(): void {
    this.loadProfile();
    this.loadFavoriteMoviesPrivacy();
  }

  loadProfile(): void {
    this.loading = true;
    this.errorMessage = '';
    this.savedMessage = '';

    this.moviesApi.syncMe().subscribe({
      next: profile => {
        this.profile = profile;
        this.originalAvatar = profile.avatar;
        this.auth.applyProfile(profile);
        this.avatarForm.setValue({ avatar: profile.avatar });
        this.loading = false;
      },
      error: err => {
        this.errorMessage = err?.error?.message ?? err?.message ?? 'Could not load profile';
        this.loading = false;
      }
    });
  }

  loadFavoriteMoviesPrivacy(): void {
    this.privacyLoading = true;
    this.privacyErrorMessage = '';
    this.privacySavedMessage = '';

    this.moviesApi.getFavoriteMoviesShare().subscribe({
      next: share => {
        this.favoriteMoviesShare = share;
        this.privacyLoading = false;
      },
      error: err => {
        this.privacyErrorMessage = err?.error?.message ?? err?.message ?? 'Could not load favorite movies privacy';
        this.privacyLoading = false;
      }
    });
  }

  shuffleAvatar(): void {
    const username = this.profile?.username || this.auth.currentUser?.username || 'user';
    const nextSeed = `${username}${Math.floor(Math.random() * 1000) + 1}`;
    this.imageLoading = true;
    this.avatarForm.setValue({ avatar: nextSeed });
    this.savedMessage = '';
  }

  cancel(): void {
    this.router.navigateByUrl('/home');
  }

  saveAvatar(): void {
    if (this.avatarForm.invalid || this.saving) return;

    const avatar = this.avatarForm.getRawValue().avatar?.trim() ?? '';
    if (!avatar) return;

    this.saving = true;
    this.errorMessage = '';
    this.savedMessage = '';

    this.moviesApi.changeAvatar(avatar).subscribe({
      next: profile => {
        this.profile = profile;
        this.originalAvatar = profile.avatar;
        this.auth.applyProfile(profile);
        this.avatarForm.setValue({ avatar: profile.avatar });
        this.saving = false;
        this.router.navigateByUrl('/home');
      },
      error: err => {
        this.errorMessage = err?.error?.message ?? err?.message ?? 'Could not update avatar';
        this.saving = false;
      }
    });
  }

  get favoriteMoviesPrivate(): boolean {
    return this.favoriteMoviesShare ? !this.favoriteMoviesShare.myFavoriteMoviesPublic : true;
  }

  setFavoriteMoviesPrivate(event: Event): void {
    if (this.privacySaving || this.privacyLoading) return;

    const checkbox = event.target as HTMLInputElement;
    const makePrivate = checkbox.checked;
    this.privacySaving = true;
    this.privacyErrorMessage = '';
    this.privacySavedMessage = '';

    const request = makePrivate
      ? this.moviesApi.makeFavoriteMoviesPrivate()
      : this.moviesApi.shareFavoriteMovies();

    request.subscribe({
      next: share => {
        this.favoriteMoviesShare = share;
        this.privacySaving = false;
        this.privacySavedMessage = 'Favorite movies privacy updated';
      },
      error: err => {
        checkbox.checked = this.favoriteMoviesPrivate;
        this.privacyErrorMessage = err?.error?.message ?? err?.message ?? 'Could not update favorite movies privacy';
        this.privacySaving = false;
      }
    });
  }

  avatar(seed: string | null | undefined): string {
    return `https://api.dicebear.com/6.x/avataaars/svg?seed=${encodeURIComponent(seed || 'user')}`;
  }
}
