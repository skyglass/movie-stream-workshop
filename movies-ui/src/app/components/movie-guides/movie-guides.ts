import { CommonModule } from '@angular/common';
import { Component, OnInit, inject } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../services/auth';
import { MovieCategory, MoviesApiService } from '../../services/movies-api';
import { CreateGuideWizardComponent } from '../create-guide-wizard/create-guide-wizard';
import { CreateMovieGuideDialogComponent } from '../create-movie-guide-dialog/create-movie-guide-dialog';
import { EditGuideDialogComponent } from '../edit-guide-dialog/edit-guide-dialog';

@Component({
  standalone: true,
  selector: 'app-movie-guides',
  imports: [CommonModule, RouterLink, CreateGuideWizardComponent, CreateMovieGuideDialogComponent, EditGuideDialogComponent],
  templateUrl: './movie-guides.html',
  styleUrl: './movie-guides.css'
})
export class MovieGuidesComponent implements OnInit {
  private readonly api = inject(MoviesApiService);
  private readonly router = inject(Router);
  readonly auth = inject(AuthService);

  guides: MovieCategory[] = [];
  personalities: MovieCategory[] = [];
  private guidesRootId: number | null = null;
  private personalitiesRootId: number | null = null;
  private myGuideCategoryIds = new Set<number>();
  loading = true;
  errorMessage = '';
  creatingType: 'Guide' | 'Personality' | null = null;
  creatingViaJson: 'Guide' | 'Personality' | null = null;
  editing: { category: MovieCategory; label: 'Guide' | 'Personality' } | null = null;

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading = true;
    this.errorMessage = '';
    this.api.getCategoryTree().subscribe({
      next: categories => {
        const guidesRoot = categories.find(c => c.name === 'Guides');
        const personalitiesRoot = categories.find(c => c.name === 'Personalities');
        this.guides = guidesRoot?.children ?? [];
        this.personalities = personalitiesRoot?.children ?? [];
        this.guidesRootId = guidesRoot?.id ?? null;
        this.personalitiesRootId = personalitiesRoot?.id ?? null;
        this.loading = false;
      },
      error: err => {
        this.errorMessage = err?.error?.message ?? err?.message ?? 'Could not load Movie Guides';
        this.loading = false;
      }
    });
    if (this.auth.token) {
      this.api.getMyGuideCategoryIds().subscribe({
        next: ids => { this.myGuideCategoryIds = new Set(ids); },
        error: () => { this.myGuideCategoryIds = new Set(); }
      });
    } else {
      this.myGuideCategoryIds = new Set();
    }
  }

  isOwnerOf(categoryId: number): boolean {
    return this.myGuideCategoryIds.has(categoryId);
  }

  deleteGuide(category: MovieCategory, label: 'Guide' | 'Personality', event: Event): void {
    event.preventDefault();
    event.stopPropagation();
    const rootId = label === 'Guide' ? this.guidesRootId : this.personalitiesRootId;
    if (rootId == null) return;
    if (!confirm(`Delete "${category.name}"? This removes the ${label.toLowerCase()} and everything in it.`)) return;
    this.errorMessage = '';
    this.api.deleteCategory(category.id, rootId).subscribe({
      next: () => this.load(),
      error: err => {
        this.errorMessage = err?.error?.detail ?? err?.error?.message ?? err?.message ?? `Could not delete this ${label.toLowerCase()}`;
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

  openCreateJson(type: 'Guide' | 'Personality'): void {
    this.creatingViaJson = type;
  }

  closeCreateJson(): void {
    this.creatingViaJson = null;
  }

  onJsonGuideCreated(guideCategoryId: number): void {
    this.creatingViaJson = null;
    this.router.navigate(['/movie-guides', guideCategoryId]);
  }

  openEdit(category: MovieCategory, label: 'Guide' | 'Personality', event: Event): void {
    event.preventDefault();
    event.stopPropagation();
    this.editing = { category, label };
  }

  closeEdit(): void {
    this.editing = null;
  }

  onEditSaved(updated: MovieCategory): void {
    this.editing = null;
    const list = this.guides.some(g => g.id === updated.id) ? this.guides : this.personalities;
    const index = list.findIndex(c => c.id === updated.id);
    if (index >= 0) list[index] = updated;
  }
}
