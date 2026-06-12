import { CommonModule } from '@angular/common';
import { Component, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { MoviesApiService, Movie } from '../../services/movies-api';

@Component({
  standalone: true,
  selector: 'app-movie-wizard',
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './movie-wizard.html',
  styleUrl: './movie-wizard.css'
})
export class MovieWizardComponent {
  private readonly fb = inject(FormBuilder);
  private readonly moviesApi = inject(MoviesApiService);
  private readonly router = inject(Router);

  step = 1;
  loading = false;
  saving = false;
  errorMessage = '';
  searchResults: Movie[] = [];
  selectedMovie: Movie | null = null;

  readonly searchForm = this.fb.group({
    searchText: ['', Validators.required]
  });

  readonly movieForm = this.fb.group({
    imdbId: ['', Validators.required],
    title: ['', Validators.required],
    director: ['', Validators.required],
    year: ['', Validators.required],
    poster: ['']
  });

  search(): void {
    if (this.searchForm.invalid) return;
    const title = this.searchForm.getRawValue().searchText ?? '';
    this.loading = true;
    this.errorMessage = '';
    this.searchResults = [];
    this.moviesApi.searchOmdb(title).subscribe({
      next: result => {
        if (result.Response === 'False') {
          this.errorMessage = result.Error ?? 'No movie found';
        } else {
          this.searchResults = [{
            imdbId: result.imdbID,
            title: result.Title,
            director: result.Director,
            year: result.Year,
            poster: result.Poster,
            comments: []
          }];
        }
        this.loading = false;
      },
      error: err => {
        this.errorMessage = err?.message ?? 'OMDb search failed';
        this.loading = false;
      }
    });
  }

  selectMovie(movie: Movie): void {
    this.selectedMovie = this.selectedMovie?.imdbId === movie.imdbId ? null : movie;
    if (this.selectedMovie) {
      this.movieForm.patchValue({
        imdbId: movie.imdbId,
        title: movie.title,
        director: movie.director,
        year: movie.year,
        poster: movie.poster
      });
    } else {
      this.movieForm.reset();
    }
  }

  next(): void {
    if (this.step === 2 && this.movieForm.invalid) {
      this.movieForm.markAllAsTouched();
      return;
    }
    this.step = Math.min(3, this.step + 1);
  }

  back(): void {
    this.step = Math.max(1, this.step - 1);
  }

  createMovie(): void {
    if (this.movieForm.invalid) {
      this.movieForm.markAllAsTouched();
      return;
    }
    this.saving = true;
    this.errorMessage = '';
    const value = this.movieForm.getRawValue();
    const movie = {
      imdbId: value.imdbId ?? '',
      title: value.title ?? '',
      director: value.director ?? '',
      year: value.year ?? '',
      poster: value.poster ?? ''
    };
    this.moviesApi.createMovie(movie).subscribe({
      next: () => this.router.navigateByUrl('/home'),
      error: err => {
        this.errorMessage = err?.error?.message ?? err?.message ?? 'Could not create movie';
        this.saving = false;
      }
    });
  }

  poster(): string {
    const poster = this.movieForm.getRawValue().poster;
    return poster && poster !== 'N/A' ? poster : '/images/movie-poster.jpg';
  }
}
