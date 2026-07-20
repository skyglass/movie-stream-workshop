import { CommonModule } from '@angular/common';
import { Component, EventEmitter, Input, OnDestroy, OnInit, Output, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Observable, map, switchMap } from 'rxjs';
import { MoviesApiService, ShareableMovie } from '../../services/movies-api';

// "Download Poster Collage": previews and downloads a poster-only collage (server-rendered PNG, see
// MovieCardsCollageService on the backend) built from up to `maxMovies` movies matching the host page's
// currently-active filter, in the same order they're shown on-screen. Opened from ShareDialogComponent.
@Component({
  standalone: true,
  selector: 'app-movie-cards-collage-dialog',
  imports: [CommonModule, FormsModule],
  templateUrl: './movie-cards-collage-dialog.html',
  styleUrl: './movie-cards-collage-dialog.css'
})
export class MovieCardsCollageDialogComponent implements OnInit, OnDestroy {
  private readonly api = inject(MoviesApiService);

  // Fetches up to maxMovies movies in on-screen order for the current filter -- supplied by the host page
  // (movie-guide-detail.ts / favorite-movies.ts) so this dialog never has to know which API/filter state to use,
  // and never touches the host's own list/pagination state. Shared with ShareDialogComponent's CSV download,
  // which is why this returns full movie records rather than just imdb ids.
  @Input({ required: true }) fetchOrderedMovies!: (maxMovies: number) => Observable<ShareableMovie[]>;
  @Output() closed = new EventEmitter<void>();

  readonly minMovies = 1;
  readonly maxMoviesLimit = 50;
  maxMovies = 50;
  loading = false;
  errorMessage = '';
  previewUrl: string | null = null;

  ngOnInit(): void {
    this.generate();
  }

  ngOnDestroy(): void {
    this.revokePreview();
  }

  onMaxMoviesChange(): void {
    const rounded = Math.round(this.maxMovies) || this.minMovies;
    this.maxMovies = Math.min(this.maxMoviesLimit, Math.max(this.minMovies, rounded));
    this.generate();
  }

  generate(): void {
    this.loading = true;
    this.errorMessage = '';
    this.revokePreview();
    this.fetchOrderedMovies(this.maxMovies).pipe(
      map(movies => movies.map(movie => movie.imdbId)),
      switchMap(imdbIds => {
        if (imdbIds.length === 0) throw { clientMessage: 'No movies match the current filter' };
        return this.api.generateMovieCardsCollage(imdbIds);
      })
    ).subscribe({
      next: blob => {
        this.previewUrl = URL.createObjectURL(blob);
        this.loading = false;
      },
      error: err => this.fail(err)
    });
  }

  download(): void {
    if (!this.previewUrl) return;
    const link = document.createElement('a');
    link.href = this.previewUrl;
    link.download = 'movie-cards-collage.png';
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
  }

  close(): void {
    this.closed.emit();
  }

  private fail(err: any): void {
    // With responseType 'blob', a failed HTTP response's body arrives as a Blob too (Angular never parses it as
    // JSON), so there's no server-provided message to surface here -- a generic fallback is the best available.
    this.errorMessage = err?.clientMessage ?? (err?.error instanceof Blob ? null : err?.error?.message)
      ?? err?.message ?? 'Could not generate the collage';
    this.loading = false;
  }

  private revokePreview(): void {
    if (this.previewUrl) {
      URL.revokeObjectURL(this.previewUrl);
      this.previewUrl = null;
    }
  }
}
