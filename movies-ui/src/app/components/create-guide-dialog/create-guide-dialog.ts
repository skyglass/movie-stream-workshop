import { CommonModule } from '@angular/common';
import { Component, EventEmitter, Input, Output, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MoviesApiService } from '../../services/movies-api';
import { ErrorAlertDialogComponent } from '../error-alert-dialog/error-alert-dialog';

@Component({
  standalone: true,
  selector: 'app-create-guide-dialog',
  imports: [CommonModule, FormsModule, ErrorAlertDialogComponent],
  templateUrl: './create-guide-dialog.html',
  styleUrl: './create-guide-dialog.css'
})
export class CreateGuideDialogComponent {
  private readonly api = inject(MoviesApiService);

  @Input() initialType: 'Guide' | 'Personality' = 'Guide';
  @Output() closed = new EventEmitter<void>();
  @Output() guideCreated = new EventEmitter<number>();

  name = '';
  description = '';
  icon = '';
  saving = false;
  errorMessage = '';

  submit(): void {
    if (!this.name.trim() || this.saving) return;
    this.saving = true;
    this.errorMessage = '';
    this.api.createGuide(this.initialType, this.name.trim(), this.description.trim(), this.icon.trim(), []).subscribe({
      next: guide => {
        this.saving = false;
        this.guideCreated.emit(guide.categoryId);
      },
      error: error => {
        this.saving = false;
        this.errorMessage = error?.error?.detail ?? error?.error?.message ?? error?.message ?? 'Could not create the guide';
      }
    });
  }

  cancel(): void {
    if (this.saving) return;
    this.closed.emit();
  }
}
