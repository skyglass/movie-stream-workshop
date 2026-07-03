# EP-020 Movie Type Classification

## Use Case

Movie catalog contribution and recommendation should preserve the OMDb result type for newly created catalog entries.

## Change

- Add a required movie type classification with allowed values Movie, Series, and Episode.
- Default existing and omitted values to Movie.
- Persist the type as an explicit numeric enum code in the movie catalog.
- Return the type and its display description in movie API responses.

## Acceptance

- Adding a catalog movie records its type.
- Recommending a movie that does not yet exist creates it with its type.
- Viewing movie details returns the type description for display.
