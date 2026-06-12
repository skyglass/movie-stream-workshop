import { CommonModule } from '@angular/common';
import { Component, OnInit, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { AuthService } from '../../services/auth';
import { Movie, MoviesApiService } from '../../services/movies-api';

@Component({
  standalone: true,
  selector: 'app-movie-detail',
  imports: [CommonModule, ReactiveFormsModule, RouterLink],
  templateUrl: './movie-detail.html',
  styleUrl: './movie-detail.css'
})
export class MovieDetailComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly fb = inject(FormBuilder);
  private readonly moviesApi = inject(MoviesApiService);
  readonly auth = inject(AuthService);

  movie: Movie | null = null;
  loading = false;
  saving = false;
  errorMessage = '';

  readonly commentForm = this.fb.group({
    text: ['', [Validators.required, Validators.maxLength(4000)]]
  });

  ngOnInit(): void {
    const imdbId = this.route.snapshot.paramMap.get('imdbId');
    if (imdbId) {
      this.loadMovie(imdbId);
    }
  }

  loadMovie(imdbId: string): void {
    this.loading = true;
    this.moviesApi.getMovie(imdbId).subscribe({
      next: movie => {
        this.movie = movie;
        this.loading = false;
      },
      error: err => {
        this.errorMessage = err?.error?.message ?? err?.message ?? 'Could not load movie';
        this.loading = false;
      }
    });
  }

  addComment(): void {
    if (!this.movie || this.commentForm.invalid) return;
    this.saving = true;
    const text = this.commentForm.getRawValue().text ?? '';
    this.moviesApi.addComment(this.movie.imdbId, text).subscribe({
      next: movie => {
        this.movie = movie;
        this.commentForm.reset();
        this.saving = false;
      },
      error: err => {
        this.errorMessage = err?.error?.message ?? err?.message ?? 'Could not save comment';
        this.saving = false;
      }
    });
  }

  poster(movie: Movie): string {
    return movie.poster && movie.poster !== 'N/A' ? movie.poster : '/images/movie-poster.jpg';
  }

  avatar(seed: string): string {
    return `https://api.dicebear.com/9.x/avataaars/svg?seed=${encodeURIComponent(seed || 'user')}`;
  }
}
