import { CommonModule } from '@angular/common';
import { Component, OnInit, inject } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../services/auth';
import { MoviesApiService, WatchlistDto } from '../../services/movies-api';
import { CreatePrivateWatchlistDialogComponent } from '../create-private-watchlist-dialog/create-private-watchlist-dialog';
import { EditPrivateWatchlistDialogComponent } from '../edit-private-watchlist-dialog/edit-private-watchlist-dialog';

// "My Watchlists" section for the Movie Journeys page -- same shape as the "Guides"/"Personalities" panels on
// the Movie Guides page (movie-guides.ts/.html), just backed by the private, per-owner watchlists API instead of
// the public category tree. Every row here is already the caller's own (GET /api/watchlists/mine is
// owner-scoped server-side), so unlike Guide's list there's no separate "isOwnerOf" check for the delete button.
@Component({
  standalone: true,
  selector: 'app-my-watchlists-section',
  imports: [CommonModule, RouterLink, CreatePrivateWatchlistDialogComponent, EditPrivateWatchlistDialogComponent],
  templateUrl: './my-watchlists-section.html',
  styleUrl: './my-watchlists-section.css'
})
export class MyWatchlistsSectionComponent implements OnInit {
  private readonly api = inject(MoviesApiService);
  private readonly router = inject(Router);
  readonly auth = inject(AuthService);

  watchlists: WatchlistDto[] = [];
  loading = true;
  errorMessage = '';
  creating = false;
  editing: WatchlistDto | null = null;

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    if (!this.auth.token) {
      this.watchlists = [];
      this.loading = false;
      return;
    }
    this.loading = true;
    this.errorMessage = '';
    this.api.getMyWatchlists().subscribe({
      next: watchlists => {
        this.watchlists = watchlists;
        this.loading = false;
      },
      error: err => {
        this.errorMessage = err?.error?.message ?? err?.message ?? 'Could not load your watchlists';
        this.loading = false;
      }
    });
  }

  deleteWatchlist(watchlist: WatchlistDto, event: Event): void {
    event.preventDefault();
    event.stopPropagation();
    if (!confirm(`Delete "${watchlist.name}"? This removes the watchlist and everything in it.`)) return;
    this.errorMessage = '';
    this.api.deleteWatchlist(watchlist.id).subscribe({
      next: () => this.load(),
      error: err => {
        this.errorMessage = err?.error?.detail ?? err?.error?.message ?? err?.message ?? 'Could not delete this watchlist';
      }
    });
  }

  openCreate(): void {
    this.creating = true;
  }

  closeCreate(): void {
    this.creating = false;
  }

  onWatchlistCreated(watchlistId: number): void {
    this.creating = false;
    this.router.navigate(['/my-watchlists', watchlistId]);
  }

  openEdit(watchlist: WatchlistDto, event: Event): void {
    event.preventDefault();
    event.stopPropagation();
    this.editing = watchlist;
  }

  closeEdit(): void {
    this.editing = null;
  }

  onEditSaved(updated: WatchlistDto): void {
    this.editing = null;
    const index = this.watchlists.findIndex(w => w.id === updated.id);
    if (index >= 0) this.watchlists[index] = updated;
  }
}
