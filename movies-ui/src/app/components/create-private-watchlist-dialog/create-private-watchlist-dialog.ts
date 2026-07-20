import { CommonModule } from '@angular/common';
import { Component, EventEmitter, Output, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MoviesApiService } from '../../services/movies-api';
import { ErrorAlertDialogComponent } from '../error-alert-dialog/error-alert-dialog';

@Component({
  standalone: true,
  selector: 'app-create-private-watchlist-dialog',
  imports: [CommonModule, FormsModule, ErrorAlertDialogComponent],
  templateUrl: './create-private-watchlist-dialog.html',
  styleUrl: './create-private-watchlist-dialog.css'
})
export class CreatePrivateWatchlistDialogComponent {
  private readonly api = inject(MoviesApiService);

  @Output() closed = new EventEmitter<void>();
  @Output() watchlistCreated = new EventEmitter<number>();

  title = '';
  description = '';
  icon = '';
  saving = false;
  errorMessage = '';

  submit(): void {
    if (!this.title.trim() || this.saving) return;
    this.saving = true;
    this.errorMessage = '';
    this.api.createWatchlist(this.title.trim(), this.description.trim(), this.icon.trim(), []).subscribe({
      next: watchlist => {
        this.saving = false;
        this.watchlistCreated.emit(watchlist.id);
      },
      error: error => {
        this.saving = false;
        this.errorMessage = error?.error?.detail ?? error?.error?.message ?? error?.message ?? 'Could not create the watchlist';
      }
    });
  }

  cancel(): void {
    if (this.saving) return;
    this.closed.emit();
  }
}
