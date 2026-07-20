import { CommonModule } from '@angular/common';
import { Component, EventEmitter, Input, Output, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { forkJoin, Observable, of } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { CsvMovieImport, CsvMovieRef, MoviesApiService, OmdbMovieSearchResult } from '../../services/movies-api';

@Component({
  standalone: true,
  selector: 'app-import-csv-dialog',
  imports: [CommonModule, FormsModule],
  templateUrl: './import-csv-dialog.html',
  styleUrl: './import-csv-dialog.css'
})
export class ImportCsvDialogComponent {
  private readonly api = inject(MoviesApiService);
  private static readonly MAX_FILE_BYTES = 5_000_000;
  private static readonly TIMEOUT_MS = 180_000;

  // Exactly one of movieGuideId/watchlistId is set by the caller.
  @Input() movieGuideId: number | null = null;
  @Input() watchlistId: number | null = null;
  // The default view's currently-selected sub-category, if any -- movies import into it instead of the guide's
  // own top-level category, matching how "Add Movies" already targets it (see movie-guide-detail.ts).
  @Input() categoryId: number | null = null;
  @Output() closed = new EventEmitter<void>();
  @Output() imported = new EventEmitter<void>();
  @Output() importFailed = new EventEmitter<string>();

  pastedCsv = '';
  processing = false;
  statusMessage = '';
  errorMessage = '';
  private timeoutHandle?: ReturnType<typeof setTimeout>;
  // Once the 3-minute guard fires, any later-arriving response from the still-in-flight request(s) is ignored --
  // the outcome has already been reported as a timeout.
  private cancelled = false;

  onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    input.value = '';
    if (!file) return;

    this.errorMessage = '';
    if (file.size > ImportCsvDialogComponent.MAX_FILE_BYTES) {
      this.errorMessage = `That file is too large (max ${ImportCsvDialogComponent.MAX_FILE_BYTES / 1_000_000} MB).`;
      return;
    }
    const reader = new FileReader();
    reader.onload = () => this.handleCsvText(String(reader.result ?? ''));
    reader.onerror = () => { this.errorMessage = 'Could not read the file.'; };
    reader.readAsText(file);
  }

  usePastedCsv(): void {
    if (!this.pastedCsv.trim()) return;
    if (this.pastedCsv.length > ImportCsvDialogComponent.MAX_FILE_BYTES) {
      this.errorMessage = `That's too much text (max ${ImportCsvDialogComponent.MAX_FILE_BYTES / 1_000_000} MB).`;
      return;
    }
    this.errorMessage = '';
    this.handleCsvText(this.pastedCsv);
  }

  close(): void {
    if (this.processing) return;
    this.closed.emit();
  }

  private handleCsvText(text: string): void {
    const rows = this.parseCsv(text);
    if (rows.length === 0) {
      this.errorMessage = 'No usable rows found — every line needs a non-blank imdb_id as the first column.';
      return;
    }
    this.errorMessage = '';
    this.runPhase1(rows);
  }

  // Tolerant, best-effort parsing: a line whose first column isn't a non-blank imdb_id is dropped entirely,
  // without ever reaching the server or the failed-movies list.
  private parseCsv(text: string): CsvMovieRef[] {
    const rows: CsvMovieRef[] = [];
    for (const line of text.split(/\r?\n/)) {
      const row = this.parseCsvLine(line);
      if (row) rows.push(row);
    }
    return rows;
  }

  // Column 1 is the imdb_id (never quoted, never contains a comma). Every column after that is one quoted,
  // dot-separated suggested category path (splitCsvFields already strips the surrounding quotes and leaves
  // commas inside them intact, e.g. "Narrative, Art & Characters.German Expressionism" stays one field).
  private parseCsvLine(line: string): CsvMovieRef | null {
    if (!line.trim()) return null;
    const fields = this.splitCsvFields(line);
    const imdbId = (fields[0] ?? '').trim();
    if (!imdbId) return null;
    const categoryPaths = fields.slice(1).map(field => field.trim()).filter(field => field.length > 0);
    return { imdbId, categoryPaths };
  }

  // Minimal CSV field splitter: handles double-quoted fields (with "" as an escaped quote) so a category path
  // containing a comma can be safely quoted.
  private splitCsvFields(line: string): string[] {
    const fields: string[] = [];
    let current = '';
    let inQuotes = false;
    for (let i = 0; i < line.length; i++) {
      const ch = line[i];
      if (inQuotes) {
        if (ch === '"') {
          if (line[i + 1] === '"') { current += '"'; i++; } else { inQuotes = false; }
        } else {
          current += ch;
        }
      } else if (ch === '"') {
        inQuotes = true;
      } else if (ch === ',') {
        fields.push(current);
        current = '';
      } else {
        current += ch;
      }
    }
    fields.push(current);
    return fields;
  }

  private runPhase1(rows: CsvMovieRef[]): void {
    this.processing = true;
    this.cancelled = false;
    this.startTimeoutGuard();
    this.statusMessage = `Matching ${rows.length} row(s) against the catalog…`;
    this.importMovies(rows).subscribe({
      next: response => {
        if (response.failedMovies.length === 0) {
          this.finish();
          return;
        }
        this.runPhase2(response.failedMovies);
      },
      error: error => this.fail(error)
    });
  }

  private importMovies(rows: CsvMovieRef[]) {
    return this.watchlistId != null
      ? this.api.importCsvMoviesToWatchlist(this.watchlistId, rows, this.categoryId)
      : this.api.importCsvMovies(this.movieGuideId!, rows, this.categoryId);
  }

  private completeImport(movies: CsvMovieImport[]) {
    return this.watchlistId != null
      ? this.api.completeCsvImportToWatchlist(this.watchlistId, movies, this.categoryId)
      : this.api.completeCsvImport(this.movieGuideId!, movies, this.categoryId);
  }

  private runPhase2(failedMovies: CsvMovieRef[]): void {
    this.statusMessage = `Looking up ${failedMovies.length} movie(s) on OMDb…`;
    forkJoin(failedMovies.map(ref => this.lookupOmdb(ref))).subscribe({
      next: results => {
        const movies: CsvMovieImport[] = results
          .map((result, index) => (result ? { movie: this.api.movieFromOmdb(result), categoryPaths: failedMovies[index].categoryPaths } : null))
          .filter((item): item is CsvMovieImport => item !== null);
        if (movies.length === 0) {
          this.finish();
          return;
        }
        this.statusMessage = `Adding ${movies.length} movie(s) found on OMDb…`;
        this.completeImport(movies).subscribe({
          next: () => this.finish(),
          error: error => this.fail(error)
        });
      },
      error: error => this.fail(error)
    });
  }

  // Any OMDb error (not found, rate-limited, unavailable, ...) is treated as "skip, no retry" per spec, same as
  // the JSON-upload flow's getOmdbMoviesByIds.
  private lookupOmdb(ref: CsvMovieRef): Observable<OmdbMovieSearchResult | null> {
    return this.api.getOmdbMovieById(ref.imdbId).pipe(catchError(() => of(null)));
  }

  private startTimeoutGuard(): void {
    this.clearTimeoutGuard();
    this.timeoutHandle = setTimeout(() => this.onTimeout(), ImportCsvDialogComponent.TIMEOUT_MS);
  }

  private clearTimeoutGuard(): void {
    if (this.timeoutHandle) {
      clearTimeout(this.timeoutHandle);
      this.timeoutHandle = undefined;
    }
  }

  private onTimeout(): void {
    this.timeoutHandle = undefined;
    this.cancelled = true;
    this.processing = false;
    this.importFailed.emit('Import timed out after 3 minutes.');
  }

  private finish(): void {
    if (this.cancelled) return;
    this.clearTimeoutGuard();
    this.processing = false;
    this.imported.emit();
  }

  private fail(error: any): void {
    if (this.cancelled) return;
    this.clearTimeoutGuard();
    this.processing = false;
    const message = error?.error?.detail ?? error?.error?.message ?? error?.message ?? 'Could not import movies from CSV';
    this.importFailed.emit(message);
  }
}
