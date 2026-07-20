import { CommonModule } from '@angular/common';
import { Component, EventEmitter, Input, OnInit, Output, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MovieCategory, MoviesApiService } from '../../services/movies-api';
import { ErrorAlertDialogComponent } from '../error-alert-dialog/error-alert-dialog';

@Component({
  standalone: true,
  selector: 'app-edit-guide-dialog',
  imports: [CommonModule, FormsModule, ErrorAlertDialogComponent],
  templateUrl: './edit-guide-dialog.html',
  styleUrl: './edit-guide-dialog.css'
})
export class EditGuideDialogComponent implements OnInit {
  private readonly api = inject(MoviesApiService);

  @Input({ required: true }) category!: MovieCategory;
  @Input() entryLabel: 'Guide' | 'Personality' = 'Guide';
  @Output() saved = new EventEmitter<MovieCategory>();
  @Output() closed = new EventEmitter<void>();

  name = '';
  description = '';
  icon = '';
  saving = false;
  errorMessage = '';

  ngOnInit(): void {
    this.name = this.category.name;
    this.description = this.category.description ?? '';
    this.icon = this.category.icon ?? '';
  }

  save(): void {
    if (!this.name.trim() || this.saving) return;
    this.saving = true;
    this.errorMessage = '';
    this.api.updateCategory(this.category.id, {
      name: this.name.trim(),
      description: this.description.trim(),
      icon: this.icon.trim(),
      parentId: this.category.parentId
    }).subscribe({
      next: updated => {
        this.saving = false;
        this.saved.emit(updated);
      },
      error: err => {
        this.saving = false;
        this.errorMessage = err?.error?.detail ?? err?.error?.message ?? err?.message ?? `Could not save this ${this.entryLabel}`;
      }
    });
  }

  close(): void {
    if (this.saving) return;
    this.closed.emit();
  }
}
