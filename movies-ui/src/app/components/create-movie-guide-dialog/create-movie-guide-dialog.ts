import { CommonModule } from '@angular/common';
import { Component, EventEmitter, Input, OnInit, Output, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Observable } from 'rxjs';
import { AuthService } from '../../services/auth';
import {
  CreateMovieGuideResponse,
  GuideMovieDetails,
  GuideMovieRef,
  MoviesApiService,
  OmdbMovieSearchResult
} from '../../services/movies-api';

type GuideJsonFile = {
  type?: string;
  name?: string;
  description?: string;
  icon?: string;
  movies?: { imdbId?: string; title?: string; year?: string; director?: string; categories?: string[] }[];
};

type PreviewMovie = {
  imdbId: string;
  title: string;
  year: string;
  director: string;
  categoriesText: string;
};

const LIMITS = {
  privileged: { maxMovies: 1000, maxCategories: 20_000 },
  regular: { maxMovies: 100, maxCategories: 700 }
};

@Component({
  standalone: true,
  selector: 'app-create-movie-guide-dialog',
  imports: [CommonModule, FormsModule],
  templateUrl: './create-movie-guide-dialog.html',
  styleUrl: './create-movie-guide-dialog.css'
})
export class CreateMovieGuideDialogComponent implements OnInit {
  private readonly api = inject(MoviesApiService);
  private readonly auth = inject(AuthService);

  @Input() initialType: 'Guide' | 'Personality' = 'Guide';
  @Output() guideCreated = new EventEmitter<number>();
  @Output() closed = new EventEmitter<void>();

  processing = false;
  statusMessage = '';
  errorMessage = '';
  promptType: 'Guide' | 'Personality' = 'Guide';
  promptCopied = false;
  isPrivileged = false;

  formName = '';
  formDescription = '';
  formIcon = '';
  formMovieCount = 50;
  formCategoriesPerMovie = 10;

  previewing = false;
  previewGuideMeta: { type: 'Guide' | 'Personality'; name: string; description: string; icon: string } | null = null;
  previewMovies: PreviewMovie[] = [];

  ngOnInit(): void {
    this.promptType = this.initialType;
    this.isPrivileged = this.auth.hasRole('MOVIES_GUIDE') || this.auth.isAdmin;
  }

  get promptExample(): string {
    if (this.isPrivileged) {
      return this.promptType === 'Guide' ? this.privilegedGuidePrompt() : this.privilegedPersonalityPrompt();
    }
    return this.regularUserPrompt(this.promptType);
  }

  private get name(): string {
    return this.formName.trim() || `<your ${this.promptType.toLowerCase()} name>`;
  }

  private get description(): string {
    return this.formDescription.trim() || '<one sentence description>';
  }

  private get icon(): string {
    return this.formIcon.trim() || `<a single emoji representing this ${this.promptType.toLowerCase()}>`;
  }

  private get movieCount(): number {
    return this.formMovieCount > 0 ? Math.floor(this.formMovieCount) : 50;
  }

  private get categoriesPerMovie(): number {
    return this.formCategoriesPerMovie > 0 ? Math.floor(this.formCategoriesPerMovie) : 10;
  }

  private privilegedGuidePrompt(): string {
    return `You are a domain expert curating a themed movie guide called "${this.name}".

Hand-pick ${this.movieCount} real movies that best represent this topic: ${this.description}. For each movie:
1. Find its real IMDb id (tt...), title, release year, and director — double-check these are all real and accurate, not invented.
2. Write yourself a one-sentence note on why this movie earned its place in the guide.
3. Turn that note into up to ${this.categoriesPerMovie} dot-separated category paths (root -> ... -> leaf, any depth) that capture why it fits — e.g. "Genres.Thriller" or a new, specific path under a topic-relevant root.

Reply with only this JSON (no commentary), keeping "type", "name", "description" and "icon" exactly as given below — you only need to fill in "movies". Include "title", "year" and "director" for every movie so they can be previewed before upload — imdbId is still what's actually used to match/create the movie:
{
  "type": "Guide",
  "name": "${this.name}",
  "description": "${this.description}",
  "icon": "${this.icon}",
  "movies": [
    { "imdbId": "tt...", "title": "...", "year": "...", "director": "...", "categories": ["...", "..."] }
  ]
}`;
  }

  private privilegedPersonalityPrompt(): string {
    return `You are simulating <a real film expert, critic, or movie fan whose taste is publicly known, e.g. "Robert De Niro" or "a Cahiers du Cinéma critic">, recommending movies the way this person genuinely would, for a list called "${this.name}".

Note: the personality is the expert doing the recommending, not necessarily the subject of the movies — an actor or director can absolutely be the persona here, but only because of their well-known taste and opinions, not their own filmography (their own films belong under Directors/Writers instead).

Hand-pick ${this.movieCount} real movies this persona is genuinely known to admire, champion, or be an authority on: ${this.description}. For each movie:
1. Find its real IMDb id (tt...), title, release year, and director — double-check these are all real and accurate, not invented.
2. Write yourself a one-sentence note on why this persona would recommend it.
3. Turn that note into up to ${this.categoriesPerMovie} dot-separated category paths (root -> ... -> leaf, any depth) that capture why this persona recommends it — e.g. "Recommended By.<persona name>.<the specific facet>".

Reply with only this JSON (no commentary), keeping "type", "name", "description" and "icon" exactly as given below — you only need to fill in "movies". Include "title", "year" and "director" for every movie so they can be previewed before upload — imdbId is still what's actually used to match/create the movie:
{
  "type": "Personality",
  "name": "${this.name}",
  "description": "${this.description}",
  "icon": "${this.icon}",
  "movies": [
    { "imdbId": "tt...", "title": "...", "year": "...", "director": "...", "categories": ["...", "..."] }
  ]
}`;
  }

  private regularUserPrompt(type: 'Guide' | 'Personality'): string {
    const intro = type === 'Guide'
      ? `You are a domain expert curating a themed movie guide called "${this.name}".`
      : `You are simulating <a real film expert, critic, or movie fan whose taste is publicly known, e.g. "Robert De Niro" or "a Cahiers du Cinéma critic">, recommending movies the way this person genuinely would, for a list called "${this.name}".

Note: the personality is the expert doing the recommending, not necessarily the subject of the movies — their own films belong under Directors/Writers instead.`;
    const goal = type === 'Guide'
      ? `Hand-pick ${this.movieCount} real movies that best represent this topic: ${this.description}.`
      : `Hand-pick ${this.movieCount} real movies this persona is genuinely known to admire, champion, or be an authority on: ${this.description}.`;
    return `${intro}

${goal} For each movie:
1. Find its real IMDb id (tt...), title, release year, and director — double-check these are all real and accurate, not invented.
2. Write yourself a one-sentence note on why this movie earned its place.
3. Turn that note into up to ${this.categoriesPerMovie} dot-separated category paths (root -> ... -> leaf, any depth) that capture why it fits — e.g. "Genres.Thriller" or a new, specific path.

Reply with only this JSON (no commentary), keeping "type", "name", "description" and "icon" exactly as given below — you only need to fill in "movies". Include "title", "year" and "director" for every movie so they can be previewed before upload — imdbId is still what's actually used to match/create the movie:
{
  "type": "${type}",
  "name": "${this.name}",
  "description": "${this.description}",
  "icon": "${this.icon}",
  "movies": [
    { "imdbId": "tt...", "title": "...", "year": "...", "director": "...", "categories": ["...", "..."] }
  ]
}`;
  }

  selectPromptType(type: 'Guide' | 'Personality'): void {
    this.promptType = type;
    this.promptCopied = false;
  }

  copyPrompt(): void {
    navigator.clipboard.writeText(this.promptExample).then(() => {
      this.promptCopied = true;
      setTimeout(() => { this.promptCopied = false; }, 2000);
    });
  }

  private static readonly MAX_FILE_BYTES = 5_000_000;

  pastedJson = '';

  onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    input.value = '';
    if (!file) return;

    this.errorMessage = '';
    if (file.size > CreateMovieGuideDialogComponent.MAX_FILE_BYTES) {
      this.errorMessage = `That file is too large (max ${CreateMovieGuideDialogComponent.MAX_FILE_BYTES / 1_000_000} MB).`;
      return;
    }
    const reader = new FileReader();
    reader.onload = () => this.handleFileContents(String(reader.result ?? ''));
    reader.onerror = () => { this.errorMessage = 'Could not read the file.'; };
    reader.readAsText(file);
  }

  usePastedJson(): void {
    if (!this.pastedJson.trim()) return;
    if (this.pastedJson.length > CreateMovieGuideDialogComponent.MAX_FILE_BYTES) {
      this.errorMessage = `That's too much text (max ${CreateMovieGuideDialogComponent.MAX_FILE_BYTES / 1_000_000} MB).`;
      return;
    }
    this.errorMessage = '';
    this.handleFileContents(this.pastedJson);
  }

  close(): void {
    if (this.processing) return;
    this.closed.emit();
  }

  private handleFileContents(text: string): void {
    let parsed: GuideJsonFile;
    try {
      parsed = JSON.parse(text);
    } catch {
      this.errorMessage = 'That file is not valid JSON.';
      return;
    }

    const validationError = this.validate(parsed);
    if (validationError) {
      this.errorMessage = validationError;
      return;
    }

    this.errorMessage = '';
    this.previewGuideMeta = {
      type: parsed.type as 'Guide' | 'Personality',
      name: parsed.name!.trim(),
      description: (parsed.description ?? '').trim(),
      icon: (parsed.icon ?? '').trim()
    };
    this.previewMovies = parsed.movies!.map(m => ({
      imdbId: m.imdbId!.trim(),
      title: (m.title ?? '').trim(),
      year: (m.year ?? '').trim(),
      director: (m.director ?? '').trim(),
      categoriesText: m.categories!.join(', ')
    }));
    this.previewing = true;
  }

  removePreviewMovie(index: number): void {
    this.previewMovies.splice(index, 1);
  }

  previewTotalCategories(): number {
    return this.previewMovies.reduce((total, movie) => total + this.splitCategories(movie.categoriesText).length, 0);
  }

  cancelPreview(): void {
    this.previewing = false;
    this.previewGuideMeta = null;
    this.previewMovies = [];
  }

  submitPreview(): void {
    if (!this.previewGuideMeta) return;
    const movies: GuideMovieRef[] = this.previewMovies
      .map(m => ({ imdbId: m.imdbId, categories: this.splitCategories(m.categoriesText) }))
      .filter(m => m.categories.length > 0);
    if (movies.length === 0) {
      this.errorMessage = 'At least one movie with a category is required.';
      return;
    }
    const meta = this.previewGuideMeta;
    this.previewing = false;
    this.previewGuideMeta = null;
    this.previewMovies = [];
    this.runPhase1(meta.type, meta.name, meta.description, meta.icon, movies);
  }

  private splitCategories(categoriesText: string): string[] {
    return categoriesText.split(',').map(c => c.trim()).filter(c => c.length > 0);
  }

  private validate(parsed: GuideJsonFile): string | null {
    if (parsed.type !== 'Guide' && parsed.type !== 'Personality') {
      return '"type" must be exactly "Guide" or "Personality".';
    }
    if (!parsed.name || !parsed.name.trim()) {
      return '"name" is required.';
    }
    if (!Array.isArray(parsed.movies) || parsed.movies.length === 0) {
      return '"movies" must be a non-empty array.';
    }
    const limits = this.isPrivileged ? LIMITS.privileged : LIMITS.regular;
    if (parsed.movies.length > limits.maxMovies) {
      return `Too many movies: ${parsed.movies.length} (maximum ${limits.maxMovies}).`;
    }

    let totalCategories = 0;
    for (const movie of parsed.movies) {
      if (!movie.imdbId || !movie.imdbId.trim()) {
        return 'Every movie needs a non-blank "imdbId".';
      }
      if (!movie.title || !movie.title.trim()) {
        return `Movie "${movie.imdbId}" needs a non-blank "title" (used for the preview).`;
      }
      if (!movie.year || !movie.year.trim()) {
        return `Movie "${movie.imdbId}" needs a non-blank "year" (used for the preview).`;
      }
      if (!movie.director || !movie.director.trim()) {
        return `Movie "${movie.imdbId}" needs a non-blank "director" (used for the preview).`;
      }
      if (!Array.isArray(movie.categories) || movie.categories.length === 0) {
        return `Movie "${movie.imdbId}" needs a non-empty "categories" array.`;
      }
      totalCategories += movie.categories.length;
    }
    if (totalCategories > limits.maxCategories) {
      return `Too many categories across all movies: ${totalCategories} (maximum ${limits.maxCategories}).`;
    }
    return null;
  }

  private createGuide(type: string, name: string, description: string, icon: string, movies: GuideMovieRef[]): Observable<CreateMovieGuideResponse> {
    return this.isPrivileged
      ? this.api.createMovieGuide(type, name, description, icon, movies)
      : this.api.createMovieGuideExistingOnly(type, name, description, icon, movies);
  }

  private completeGuide(guideCategoryId: number, details: GuideMovieDetails[]): Observable<void> {
    return this.isPrivileged
      ? this.api.completeMovieGuide(guideCategoryId, details)
      : this.api.completeMovieGuideExistingOnly(guideCategoryId, details);
  }

  private runPhase1(type: string, name: string, description: string, icon: string, movies: GuideMovieRef[]): void {
    this.processing = true;
    this.statusMessage = 'Resolving categories and matching movies already in the catalog…';
    this.errorMessage = '';
    this.createGuide(type, name, description, icon, movies).subscribe({
      next: response => {
        if (response.failedImdbIds.length === 0) {
          this.finish(response.guideCategoryId);
          return;
        }
        this.runPhase2(response.guideCategoryId, response.failedImdbIds, movies);
      },
      error: error => this.fail(error)
    });
  }

  private runPhase2(guideCategoryId: number, failedImdbIds: string[], movies: GuideMovieRef[]): void {
    this.statusMessage = `Fetching details from OMDb for ${failedImdbIds.length} movie(s) not yet in the catalog…`;
    this.api.getOmdbMoviesByIds(failedImdbIds).subscribe({
      next: results => {
        const details: GuideMovieDetails[] = [];
        results.forEach((result, index) => {
          if (!result) return; // OMDb couldn't resolve this imdbId — skip it, per spec.
          const categories = movies.find(m => m.imdbId === failedImdbIds[index])?.categories ?? [];
          details.push({ movie: this.api.movieFromOmdb(result as OmdbMovieSearchResult), categories });
        });
        if (details.length === 0) {
          this.finish(guideCategoryId);
          return;
        }
        this.statusMessage = 'Creating the remaining movies and assigning categories…';
        this.completeGuide(guideCategoryId, details).subscribe({
          next: () => this.finish(guideCategoryId),
          error: error => this.fail(error)
        });
      },
      error: error => this.fail(error)
    });
  }

  private finish(guideCategoryId: number): void {
    this.processing = false;
    this.guideCreated.emit(guideCategoryId);
  }

  private fail(error: any): void {
    this.processing = false;
    this.errorMessage = error?.error?.detail ?? error?.error?.message ?? error?.message ?? 'Could not create the movie guide';
  }
}
