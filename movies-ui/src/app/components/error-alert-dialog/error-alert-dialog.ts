import { CommonModule } from '@angular/common';
import { Component, EventEmitter, Input, Output } from '@angular/core';

// A small modal alert with a single "OK" button, stacked on top of whatever dialog is showing it (e.g. a
// create/edit form) -- dismissing it only clears the error, it never closes the dialog underneath, so the user
// can fix the offending field (e.g. a duplicate name) and resubmit without having lost anything they'd typed.
@Component({
  standalone: true,
  selector: 'app-error-alert-dialog',
  imports: [CommonModule],
  templateUrl: './error-alert-dialog.html',
  styleUrl: './error-alert-dialog.css'
})
export class ErrorAlertDialogComponent {
  @Input({ required: true }) message!: string;
  @Output() closed = new EventEmitter<void>();
}
