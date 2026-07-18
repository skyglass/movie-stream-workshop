# Movie Guide / Movie List Design Notes

Working notes for the Movie Page family (`MovieList`, `MovieDetails`, `MovieGuide`) and the category system that
backs `SelectCategories` and the Movie Guides/Personalities feature. `MovieChallenge` is out of scope here and stays
unchanged unless a `MoviePage` change forces it to.

This file reconciles the target design against the current implementation. Each section states the target behavior,
then notes whether it already matches the code or needs a change.

## Movie Pages

A Movie Page is a route (e.g. `/my-favorite-movies`, `/movie-guides/:id`) rendered with some combination of:

- `MovieList` — a filterable, paginated list of movie cards.
- `MovieDetails` — a single-movie detail view.
- `MovieGuide` — a curated guide/personality view built on categories (notes to follow in a later pass).

## MovieList Component

Composed of `movie-grid` (cards + pagination) and `movie-filter-search` (filter + category selection + OMDb
external search). Current code:

- `movies-ui/src/app/components/movie-grid/` — cards, poster fallback, Like/Dislike/Clear actions, delegates
  pagination to `movie-page-navigator`.
- `movies-ui/src/app/components/movie-page-navigator/` — generic pager, reused by the grid and by External Results.
- `movies-ui/src/app/components/movie-filter-search/` — filter text, checkboxes, category selector trigger, and the
  OMDb External Results flow, all in one component.

**Status: matches target design**, no changes needed for the list/pagination shell itself.

## Filter Component

State: `FilterText`, a list of `FilterCheckboxes` (currently "Search OMDb" and "Not Voted Yet"), and
`SelectCategories` (selected category ids).

Behavior:

- Typing triggers the filter once at least 4 characters are entered (debounced), or immediately via the `Search`
  button.
- `Search` and `Clear` buttons and the `Search OMDb` checkbox are shown once the filter textbox is non-blank.
- `Clear` empties the filter textbox and reloads the page's default list.

Current code: `movie-filter-search.ts`/`.html` (300ms debounce, min 4 chars, `searchOmdb`/`showNotRecommendedFilter`
checkboxes, `Clear()` resets to the page's default category scope).

**Status: matches target design.**

## SelectCategories / Category Tree Dialog

`SelectCategories` is an icon button that opens a dialog containing a `CatalogTree` (category tree) and tracks
`selectedCategoryIds`. Confirming the dialog re-runs the movie list query scoped to those categories.

Current code: `movies-ui/src/app/components/category-tree-dialog/` (`mode="filter"`) +
`category-tree-node.ts` for the recursive tree rendering.

Filtering semantics (already implemented server-side, `MovieRepository` native queries):

- Only the selected top-level category ids are sent (`selectedCategoryIds`); the SQL joins through
  `category_parent_child_all` (the closure table) to resolve every descendant, so a movie tagged only on a
  sub-category still matches its selected ancestor.
- A movie matches if it belongs to *any* selected category (OR across `selectedCategoryIds`).
- An empty `selectedCategoryIds` means no category filter is applied (all movies returned).

**Status: matches target design**, including the transitive-closure join and OR/empty-means-all semantics.

## Category Management (MOVIES_GUIDE / MOVIES_ADMIN only)

The same dialog also hosts category CRUD, currently gated only by dialog `mode` (`assign`/`journey` show management
controls, `filter` does not) with **no role check** in the UI, and only `MOVIES_ADMIN`/`MOVIES_USER` gating on the
API side (`SecurityConfig`, `/api/categories/**` mutations). Target design:

- `AddRootCategory`, `AddCategoryToParent`, `EditCategory`, `MoveCategory`, `DeleteCategory` are visible only to
  users with `MOVIES_GUIDE` or `MOVIES_ADMIN`, both in the UI (hide the icon buttons) and on the corresponding API
  endpoints (reject otherwise).
- Movie-to-category assignment (`saveMovieCategories`, `addMovieFromSearchToCategory` — used from `movie-detail`
  and the OMDb Add flow) is a separate concern and keeps its current broader authorization (any authenticated
  `MOVIES_USER`); it is not restricted to `MOVIES_GUIDE`/`MOVIES_ADMIN`.

**Status: needs change.** Requires splitting `/api/categories/**` authorization into "category CRUD" vs.
"movie-category assignment" URL groups, and adding a role check in the UI around the management icon buttons.

### EditCategory

Dialog with a name/icon/description form, `Save` and `Cancel` actions. `Save` submits the edit, closes the dialog
on success, and stays open showing a validation error (e.g. "name is required") on failure. `Cancel` closes without
submitting.

Current code: the inline create/edit form in `category-tree-dialog.ts`/`.html` (`beginEdit`, `saveEditor`).

**Status: matches target behavior** for name/icon/description edits. The editor currently never changes
`parentId` on submit — reparenting is intentionally carved out into `MoveCategory` below, not `EditCategory`.

### MoveCategory (new)

An icon button opens a `MoveCategoryDialogWindow` containing a `CategorySelector`: a `CategoryTree` where the user
picks **exactly one** target category (single-select — checking one node unchecks any previously selected node).
A `Copy` checkbox lets the user acknowledge "copy instead of move." A `MoveCategoryAction` performs the operation:

- **Move** (`Copy` unchecked): changes the source category's parent to the selected target and rebuilds
  `category_parent_child_all`.
- **Copy** (`Copy` checked): creates new category row(s) under the selected target with the source subtree's
  shape, and rebuilds `category_parent_child_all`. The originals are untouched.

Safety requirements — the action must reject and take no effect for:

- Moving/copying a category to itself.
- Moving/copying a category into one of its own descendants (would create a cycle).

Current code: `CategoryService.setParent()` already exists as a reparent primitive with cycle checks for
`create`/`update`, but there is no dedicated move endpoint, no copy/subtree-duplication logic, and no UI for any
of this (`grep` for move/drag in `movies-ui` returns nothing).

**Status: net-new feature.** Needs a backend endpoint (move + copy, both subtree-aware, with cycle/self checks) and
a new UI component (`MoveCategory` icon button, `MoveCategoryDialogWindow`, single-select `CategorySelector`,
`Copy` checkbox).

### DeleteCategory (changed)

Only available to `MOVIES_GUIDE`/`MOVIES_ADMIN`. Clicking the icon button shows an "Are you sure?" confirmation;
the delete action only runs after the user confirms.

Deletion no longer requires the category to be empty of movies — a category with movies assigned can be deleted.
**Decision:** a category that still has sub-categories cannot be deleted (this rule is unchanged from today); the
user must move or delete the children first. Only the "must have no movies" half of the old restriction is lifted.

Delete sequence:

1. Unassign all movies from the category (`delete from movie_category where category_id = :id`).
2. Remove the category's edges from `category_parent_child` (as parent or child) — in practice this is already
   handled by the `on delete cascade` FK from `category_parent_child(parent_id|child_id)` to `category(id)`, so
   removing the `category` row is sufficient; `category_parent_child_all` cascades the same way and is then
   rebuilt via `rebuildClosure()` to clean up any transitive rows that routed through the deleted node.
3. Delete the `category` row itself.

FK audit: only three tables reference `category(id)` — `movie_category`, `category_parent_child`, and
`category_parent_child_all` — all three already `on delete cascade` (`V34__movie_categories.sql`). No other
foreign keys exist against `category`.

Current code: `CategoryService.delete()` (`movies-api/.../movie/CategoryService.java`) currently blocks with
`409 CONFLICT` whenever the category has movies *or* children. `CategoryController.delete()` has no per-endpoint
role check; authorization comes from `SecurityConfig`'s coarse `/api/categories/**` → `MOVIES_ADMIN, MOVIES_USER`
rule.

**Status: needs change.** Relax the "has movies" half of the guard, keep the "has children" half, add
`MOVIES_GUIDE`/`MOVIES_ADMIN`-only authorization, and add a confirmation step in the UI.

## OMDb External Results Flow

When "Search OMDb" is checked, an "External Results" section appears above the default `MovieList`, backed by the
existing external search algorithm (unchanged). Clicking a result opens a dialog with `Add`, `Like`, and `Close`.
`Add` and `Like` are already correct and unchanged. Closing the dialog (via `Close`, or automatically after a
successful `Add`/`Like`) returns to the External Results section plus the default list, each with its own
paginator.

Current code: this entire flow already lives in `movie-filter-search.ts`/`.html` (`searchOmdb`, `selectMovie`,
`act()`, `closeMovie()`, `externalActions` input).

**Status: matches target design**, no changes needed.

## Roles

`MOVIES_ADMIN`, `MOVIES_USER`, and `MOVIES_GUIDE` all already exist end-to-end (Keycloak realm
`config/keycloak/realm-movies.json`, `SecurityConfig` constants, `AuthService.hasRole`/`canEditMovies` in the UI).
`MOVIES_GUIDE` is not a new role — the work here is *using* it to gate category-management endpoints/UI that are
currently open to any `MOVIES_USER`.

## Movie Guide Wizard

Replaces the old "paste a JSON file produced by an LLM prompt" creation flow entirely
(`CreateMovieGuideDialogComponent`, `MovieGuideService.createGuide`/`createGuideExistingOnly`/`completeGuide`/
`completeGuideExistingOnly`, and their DTOs/endpoints are removed). "Guide" and "Personality" are the same flow;
only `movie_guide.type` differs.

### Entry point

The Movie Guides page keeps its two sections, "Guides" and "Personalities", each with a "Create Guide"/
"Create Personality" button that opens `CreateGuideWizard`/`CreatePersonalityWizard` (one component, `type` input).

Every wizard step has "Previous" (disabled on step 1), "Next", and "Cancel" (clears any in-progress state and
closes). Visiting an existing Guide/Personality page also needs to decide whether to show the wizard at all:

- Not the owner → never show the wizard; just the normal read-only guide page.
- Owner and `movie_guide.status == STARTED` → resume at Step 2 (Step 1's action already ran when the guide row was
  created — see below).
- Owner and `status == COMPLETED` → no wizard, normal page.

### `movie_guide.status`

New column `status smallint not null default 0`, backed by a Java enum `MovieGuideStatus { STARTED(0),
COMPLETED(1) }`. Comment on the enum: **never reorder or remove existing values, only append new ones** — the
ordinal is persisted directly.

### Step 1 — Select Categories to Include ("Subscribe")

Shows the same `CategoryTreeDialogComponent` in `mode="filter"` already built for the MovieList filter (parent
selection visually locks/checks descendants; only the top-level selected ids are ever sent — the backend already
resolves descendants transitively). Buttons: "Skip", "Next" — no "Previous" (first step).

Marketing framing for this step: *"Subscribe to other categories and receive all updates in your guide — e.g.
subscribe to 'New 2026' and your guide always shows fresh movies from it."*

Both "Skip" and "Next" run the same transaction (Skip just passes an empty category selection):

1. Create the category `Guide_Name` under the `Guides`/`Personalities` root (existing `resolveGuideCategory` logic).
2. Create the `movie_guide` row (`name`, `description`, `icon`, `owner`, `category_id`, `status=STARTED`).
3. For each selected category id, add a `Copy`-style link (reuse `CategoryService`'s Copy edge-insert) into the
   guide's anchor category, and record it in `movie_guide_default_category(movie_guide_id, category_id,
   referenced_category_id=category_id)`.

Returns `movie_guide_id`. This is why a returning owner can resume: the guide already exists the moment Step 1's
action runs, regardless of Skip vs. Next.

### Step 2 — Select Movies (MovieSelector step)

The Step 2 page itself shows the normal MovieList pattern, scoped with `parentCategory = <guide's anchor category>`
and `defaultCategoryIds = <the subscribed/referenced category ids from movie_guide_default_category>` **excluded**
from the view. Since nothing has been assigned to the guide's own category yet and the subscribed categories are
excluded, this list is empty by default. Header: "Add Movies" icon button (top right) + the standard Filter section
below it.

"Add Movies" opens the `MovieSelector` dialog:

- Default list = the normal home-page movie list/order, paginated.
- Every movie row gets a select/deselect checkbox in addition to its normal display, backed by a `selectedMovieIds`
  state that survives paging: the user can check items on page 1, jump to page 3, come back to page 2, etc., and
  every page reflects the current `selectedMovieIds` membership correctly.
- A "Select Categories" icon button below the filter opens `CategoryTreeDialogComponent` in `mode="filter"` again
  (still no management buttons — that mode already never shows them, for any role) for multi-select category
  filtering of the movie list. Closing this (OK and Close are the same action here) applies the selection, clears
  the rest of the filter (search text, "Search OMDb" checkbox, External Results) but **keeps** the new
  `selectedCategoryIds`, and resets pagination to page 1.
- Layout, top to bottom: External Results (only while "Search OMDb" is checked and search text is non-blank) →
  Movie Results (paginated, current filter + `selectedCategoryIds`; empty `selectedCategoryIds` means no category
  filter) → Selected Movies (picks in the order added, each with a "Remove" icon button).
- Movie Results checkboxes reflect `selectedMovieIds`; toggling one updates `selectedMovieIds` (and so the Selected
  Movies list) without touching filter state, category selection, or the current page.
- Page navigator resets to page 1 on any *filter* change (search keystroke, "Search OMDb" toggle, category
  selection change) but never because of a checkbox toggle. `selectedMovieIds` is untouched by paging or by filter
  changes — the only way to remove a pick is the explicit "Remove" button in the Selected Movies section.
- External Results element dialog keeps its existing Add/Like/Close behavior unchanged. `Close` just closes it, no
  state change. `Add`/`Like` run the existing action, close the dialog, and additionally append the movie straight
  into Selected Movies (no need to reload Movie Results).
- On closing `MovieSelector` (there is no separate "cancel without saving" — closing always applies the current
  Selected Movies), the full selected-movie list (with enough detail — title/year/director/etc.) is emitted back to
  Step 2, which assigns all of them to the `Guide_Name` category and advances the wizard to Step 3.

### Open items

Step 3 onward not described yet — to follow in a later pass.
