import { CommonModule } from '@angular/common';
import { Component, EventEmitter, Input, OnInit, Output, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MoviesApiService, WatchlistDto } from '../../services/movies-api';
import { ErrorAlertDialogComponent } from '../error-alert-dialog/error-alert-dialog';

@Component({
  standalone: true,
  selector: 'app-edit-private-watchlist-dialog',
  imports: [CommonModule, FormsModule, ErrorAlertDialogComponent],
  templateUrl: './edit-private-watchlist-dialog.html',
  styleUrl: './edit-private-watchlist-dialog.css'
})
export class EditPrivateWatchlistDialogComponent implements OnInit {
  private readonly api = inject(MoviesApiService);

  @Input({ required: true }) watchlist!: WatchlistDto;
  @Output() saved = new EventEmitter<WatchlistDto>();
  @Output() closed = new EventEmitter<void>();

  title = '';
  description = '';
  icon = '';
  saving = false;
  errorMessage = '';

  ngOnInit(): void {
    this.title = this.watchlist.name;
    this.description = this.watchlist.description ?? '';
    this.icon = this.watchlist.icon ?? '';
  }

  save(): void {
    if (!this.title.trim() || this.saving) return;
    this.saving = true;
    this.errorMessage = '';
    this.api.updateWatchlist(this.watchlist.id, this.title.trim(), this.description.trim(), this.icon.trim()).subscribe({
      next: updated => {
        this.saving = false;
        this.saved.emit(updated);
      },
      error: err => {
        this.saving = false;
        this.errorMessage = err?.error?.detail ?? err?.error?.message ?? err?.message ?? 'Could not save this watchlist';
      }
    });
  }

  close(): void {
    if (this.saving) return;
    this.closed.emit();
  }
}
