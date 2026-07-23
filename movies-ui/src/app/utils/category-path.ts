import { MovieCategory } from '../services/movies-api';

// Root-to-target ancestor chain (inclusive), or null if targetId isn't found anywhere in the tree.
export function findCategoryPath(categories: MovieCategory[], targetId: number): MovieCategory[] | null {
  for (const category of categories) {
    if (category.id === targetId) return [category];
    const found = findCategoryPath(category.children, targetId);
    if (found) return [category, ...found];
  }
  return null;
}

// Lowercases and collapses whitespace/punctuation to '-' for a readable URL segment. Cosmetic only -- category
// page navigation always resolves by numeric id, so this never needs to handle every possible character: anything
// left after this (accents, non-Latin scripts, emoji) round-trips safely via the Router's own URL encoding.
export function slugify(name: string): string {
  return name
    .toLowerCase()
    .replace(/[^\p{L}\p{N}]+/gu, '-')
    .replace(/^-+|-+$/g, '') || 'category';
}

// Router command array for a category's own friendly URL, e.g. ['/categories', 42, 'movies', 'action', 'neo-noir'].
export function categoryPageSegments(path: MovieCategory[]): (string | number)[] {
  if (!path.length) return ['/categories', 'root'];
  return ['/categories', path[path.length - 1].id, ...path.map(category => slugify(category.name))];
}

// Router command array for a sub-category's friendly URL underneath a fixed base path, e.g.
// subCategorySegments(['/movie-guides', 42], path) -> ['/movie-guides', 42, 108, 'genres', 'drama']. Mirrors
// categoryPageSegments' slugify logic -- resolution is always by the numeric id, trailing segments are cosmetic.
export function subCategorySegments(basePath: (string | number)[], path: MovieCategory[]): (string | number)[] {
  if (!path.length) return basePath;
  return [...basePath, path[path.length - 1].id, ...path.map(category => slugify(category.name))];
}
