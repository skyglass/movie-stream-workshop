import { Location } from '@angular/common';
import { Component, inject } from '@angular/core';

@Component({
  standalone: true,
  selector: 'app-back-button',
  template: `
    <button type="button" class="back-button" (click)="goBack()">
      <span class="material-icons" aria-hidden="true">arrow_back</span>
      Back
    </button>
  `,
  styles: [`
    .back-button { display: inline-flex; align-items: center; gap: .35rem; border: 1px solid #cbd5e1; border-radius: .45rem; background: #fff; color: #334155; padding: .5rem .8rem; font-weight: 700; cursor: pointer; }
    .back-button:hover { background: #f1f5f9; }
    .material-icons { font-size: 1.1rem; }
  `]
})
export class BackButtonComponent {
  private readonly location = inject(Location);

  goBack(): void {
    this.location.back();
  }
}
