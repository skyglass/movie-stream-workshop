import { CommonModule } from '@angular/common';
import { Component, EventEmitter, Input, Output } from '@angular/core';
import { Observable } from 'rxjs';
import { ShareableMovie } from '../../services/movies-api';
import { MovieCardsCollageDialogComponent } from '../movie-cards-collage-dialog/movie-cards-collage-dialog';

// Shared "Share" dialog reused by the Movie Guide detail page and My Favorite Movies -- a link + Copy button,
// "Download Poster Collage" (opens MovieCardsCollageDialogComponent), and "Download CSV file" (generated and
// downloaded entirely client-side -- plain text has none of the cross-origin poster-fetch concerns the collage
// has). Opening/closing this dialog never touches the host page's own filter/pagination state; it only ever
// reads a link string and a callback the host supplies for fetching the current filter's movies.
@Component({
  standalone: true,
  selector: 'app-share-dialog',
  imports: [CommonModule, MovieCardsCollageDialogComponent],
  templateUrl: './share-dialog.html',
  styleUrl: './share-dialog.css'
})
export class ShareDialogComponent {
  // Effectively "no limit" for the CSV download (see downloadCsv below) -- the backend page size has no upper
  // bound, and a real catalog is nowhere near this size, so this is just a large sentinel rather than a literal
  // cap.
  private static readonly CSV_ROW_LIMIT = 1_000_000;

  // Empty shareUrl means there's no public link to show (e.g. a private watchlist's restricted "Export" dialog) --
  // the link row hides itself in that case rather than every host page having to opt out explicitly.
  @Input() shareUrl = '';
  @Input() shareLabel = 'Shareable link';
  @Input() dialogTitle = 'Share';
  @Input({ required: true }) fetchOrderedMovies!: (maxMovies: number) => Observable<ShareableMovie[]>;
  @Output() closed = new EventEmitter<void>();

  copied = false;
  collageDialogVisible = false;
  downloadingCsv = false;
  csvErrorMessage = '';

  async copyShareUrl(): Promise<void> {
    if (!this.shareUrl) return;
    try {
      await navigator.clipboard.writeText(this.shareUrl);
    } catch {
      this.copyWithFallback();
    }
    this.copied = true;
  }

  openCollageDialog(): void {
    this.collageDialogVisible = true;
  }

  closeCollageDialog(): void {
    this.collageDialogVisible = false;
  }

  // Same movies "Download Poster Collage" would include (current filter, on-screen order) but ignoring the
  // "Movies to include" cap -- every matching movie goes into the file, not just the first maxMovies.
  downloadCsv(): void {
    if (this.downloadingCsv) return;
    this.downloadingCsv = true;
    this.csvErrorMessage = '';
    this.fetchOrderedMovies(ShareDialogComponent.CSV_ROW_LIMIT).subscribe({
      next: movies => {
        this.triggerCsvDownload(movies);
        this.downloadingCsv = false;
      },
      error: err => {
        this.csvErrorMessage = err?.error?.message ?? err?.message ?? 'Could not download the CSV file';
        this.downloadingCsv = false;
      }
    });
  }

  private triggerCsvDownload(movies: ShareableMovie[]): void {
    const csv = this.buildCsv(movies);
    const blob = new Blob([csv], { type: 'text/csv;charset=utf-8;' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = 'movies.csv';
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    URL.revokeObjectURL(url);
  }

  // title/directors are quoted (commas are common in both, e.g. "Butch Cassidy and the Sundance Kid" director
  // lists); imdb_id/year never contain commas or quotes, so they're left bare.
  private buildCsv(movies: ShareableMovie[]): string {
    const header = 'imdb_id,title,year,directors';
    const rows = movies.map(movie => [movie.imdbId, this.csvQuote(movie.title), movie.year, this.csvQuote(movie.director)].join(','));
    return [header, ...rows].join('\r\n');
  }

  private csvQuote(value: string | null | undefined): string {
    return `"${(value ?? '').replace(/"/g, '""')}"`;
  }

  private copyWithFallback(): void {
    const textarea = document.createElement('textarea');
    textarea.value = this.shareUrl;
    textarea.setAttribute('readonly', '');
    textarea.style.position = 'fixed';
    textarea.style.left = '-9999px';
    document.body.appendChild(textarea);
    textarea.select();
    document.execCommand('copy');
    document.body.removeChild(textarea);
  }
}
