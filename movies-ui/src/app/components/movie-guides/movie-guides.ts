import { CommonModule } from '@angular/common';
import { Component, OnInit, inject } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../services/auth';
import { MovieCategory, MoviesApiService } from '../../services/movies-api';
import { CreateMovieGuideDialogComponent } from '../create-movie-guide-dialog/create-movie-guide-dialog';

@Component({
  standalone: true,
  selector: 'app-movie-guides',
  imports: [CommonModule, RouterLink, CreateMovieGuideDialogComponent],
  templateUrl: './movie-guides.html',
  styleUrl: './movie-guides.css'
})
export class MovieGuidesComponent implements OnInit {
  private readonly api = inject(MoviesApiService);
  private readonly router = inject(Router);
  readonly auth = inject(AuthService);

  guides: MovieCategory[] = [];
  personalities: MovieCategory[] = [];
  loading = true;
  errorMessage = '';
  creatingType: 'Guide' | 'Personality' | null = null;

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading = true;
    this.errorMessage = '';
    this.api.getCategoryTree().subscribe({
      next: categories => {
        this.guides = categories.find(c => c.name === 'Guides')?.children ?? [];
        this.personalities = categories.find(c => c.name === 'Personalities')?.children ?? [];
        this.loading = false;
      },
      error: err => {
        this.errorMessage = err?.error?.message ?? err?.message ?? 'Could not load Movie Guides';
        this.loading = false;
      }
    });
  }

  openCreate(type: 'Guide' | 'Personality'): void {
    this.creatingType = type;
  }

  closeCreate(): void {
    this.creatingType = null;
  }

  onGuideCreated(guideCategoryId: number): void {
    this.creatingType = null;
    this.router.navigate(['/movie-guides', guideCategoryId]);
  }
}
