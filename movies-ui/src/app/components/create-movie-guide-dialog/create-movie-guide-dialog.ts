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
  movies?: { imdbId?: string; categories?: string[] }[];
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

  ngOnInit(): void {
    this.promptType = this.initialType;
    this.isPrivileged = this.auth.hasRole('MOVIES_GUIDE') || this.auth.isAdmin;
  }

  private readonly guidePromptExample = `You are a domain expert curating a themed movie guide called "<your guide name, e.g. "Heist Movies: Masterworks of the Perfect Plan">".

Hand-pick 12-25 real movies that best represent this topic, based on <the specific criteria for this topic, e.g. "how deliberately the plot builds toward its central twist">. For each movie:
1. Find its real IMDb id (tt...).
2. Write yourself a one-sentence note on why this movie earned its place in the guide.
3. Turn that note into one or more dot-separated category paths (root -> ... -> leaf, any depth), reusing an existing category like Genres/Directors/Writers when it genuinely fits, and inventing a new, specific path under a topic-relevant root otherwise.

Reply with only this JSON (no commentary):
{
  "type": "Guide",
  "name": "<your guide name>",
  "description": "<one sentence describing the guide>",
  "movies": [
    { "imdbId": "tt...", "categories": ["...", "..."] }
  ]
}`;

  private readonly personalityPromptExample = `You are simulating <a real film expert, critic, or movie fan whose taste is publicly known, e.g. "Robert De Niro" or "a Cahiers du Cinéma critic">, recommending movies the way this person genuinely would, for a list called "<your personality name, e.g. "Robert De Niro's Italian Neorealism Picks">".

Note: the personality is the expert doing the recommending, not necessarily the subject of the movies — an actor or director can absolutely be the persona here, but only because of their well-known taste and opinions, not their own filmography (their own films belong under Directors/Writers instead).

Hand-pick 12-25 real movies this persona is genuinely known to admire, champion, or be an authority on. For each movie:
1. Find its real IMDb id (tt...).
2. Write yourself a one-sentence note on why this persona would recommend it.
3. Turn that note into one or more dot-separated category paths (root -> ... -> leaf, any depth), reusing an existing category like Genres/Directors/Writers when it genuinely fits, and inventing a new, specific path (e.g. "Recommended By.<persona name>.<the specific facet>") otherwise.

Reply with only this JSON (no commentary):
{
  "type": "Personality",
  "name": "<your personality name>",
  "description": "<one sentence describing the personality>",
  "movies": [
    { "imdbId": "tt...", "categories": ["...", "..."] }
  ]
}`;

  get promptExample(): string {
    if (this.isPrivileged) {
      return this.promptType === 'Guide' ? this.guidePromptExample : this.personalityPromptExample;
    }
    return this.regularUserPrompt(this.promptType);
  }

  private regularUserPrompt(type: 'Guide' | 'Personality'): string {
    const intro = type === 'Guide'
      ? `You are a domain expert curating a themed movie guide called "<your guide name>".`
      : `You are simulating <a real film expert, critic, or movie fan whose taste is publicly known, e.g. "Robert De Niro" or "a Cahiers du Cinéma critic">, recommending movies the way this person genuinely would, for a list called "<your personality name, e.g. "Robert De Niro's Italian Neorealism Picks">".

Note: the personality is the expert doing the recommending, not necessarily the subject of the movies — their own films belong under Directors/Writers instead.`;
    const goal = type === 'Guide'
      ? 'Hand-pick 12-25 real movies that best represent this topic.'
      : 'Hand-pick 12-25 real movies this persona is genuinely known to admire, champion, or be an authority on.';
    return `${intro}

Your account can only use EXISTING categories — it cannot invent new ones. ${goal} For each movie:
1. Find its real IMDb id (tt...).
2. Pick one or more dot-separated category paths (root -> ... -> leaf). Any path that doesn't already exist
   character-for-character is silently dropped rather than created, so when in doubt, give a movie more than one path.

Reply with only this JSON (no commentary):
{
  "type": "${type}",
  "name": "<your ${type.toLowerCase()} name>",
  "description": "<one sentence>",
  "movies": [
    { "imdbId": "tt...", "categories": ["...", "..."] }
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

    const movies: GuideMovieRef[] = parsed.movies!.map(m => ({ imdbId: m.imdbId!, categories: m.categories! }));
    this.runPhase1(parsed.type!, parsed.name!, parsed.description ?? '', movies);
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

  private createGuide(type: string, name: string, description: string, movies: GuideMovieRef[]): Observable<CreateMovieGuideResponse> {
    return this.isPrivileged
      ? this.api.createMovieGuide(type, name, description, movies)
      : this.api.createMovieGuideExistingOnly(type, name, description, movies);
  }

  private completeGuide(guideCategoryId: number, details: GuideMovieDetails[]): Observable<void> {
    return this.isPrivileged
      ? this.api.completeMovieGuide(guideCategoryId, details)
      : this.api.completeMovieGuideExistingOnly(guideCategoryId, details);
  }

  private runPhase1(type: string, name: string, description: string, movies: GuideMovieRef[]): void {
    this.processing = true;
    this.statusMessage = 'Resolving categories and matching movies already in the catalog…';
    this.errorMessage = '';
    this.createGuide(type, name, description, movies).subscribe({
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
