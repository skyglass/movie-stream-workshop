# EP-017 Paginated Movie Lists

## Goal

All movie list views return a page of movies and the full result count so the UI can render a page navigator.

## Acceptance Criteria

- Movie catalog, favorite movies, users favorite movies, and users recommended movies return a paged result with:
  - the current page content
  - `totalCount` for the full matching list
- Page requests are one-based at the API boundary.
- `MOVIES_PER_PAGE` controls the default page size for deployment and UI navigation.
- The UI shows bottom navigation with previous/next links when available and numbered page links.
- Existing list behavior and ordering are preserved within each page.
- Each movie list use case has one focused pagination scenario.
