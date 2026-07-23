import { CommonModule } from '@angular/common';
import { Component } from '@angular/core';
import { MyWatchlistsSectionComponent } from '../my-watchlists-section/my-watchlists-section';

// Dedicated landing page for the "Movie Watchlists" nav link -- previously that link pointed into the (now
// removed) Movie Journeys page, which happened to embed <app-my-watchlists-section> at its top. This page is
// just that section, standalone, so private watchlists keep a real entry point.
@Component({
  standalone: true,
  selector: 'app-my-watchlists-page',
  imports: [CommonModule, MyWatchlistsSectionComponent],
  templateUrl: './my-watchlists-page.html',
  styleUrl: './my-watchlists-page.css'
})
export class MyWatchlistsPageComponent {
}
