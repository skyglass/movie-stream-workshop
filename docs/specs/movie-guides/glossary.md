# Movie Guides Glossary

| Term | Meaning |
|------|---------|
| Movie Guide | A themed, hand-curated collection of movies built around a topic, e.g. "Essential Heist Films." |
| Movie Personality | A collection built around a real or fictitious taste, e.g. "What Kubrick Would Watch." |
| Anchor category | The one `CATEGORY` a Movie Guide is created against; native content and subscriptions both live under it. |
| Owner | The username that created a Movie Guide; the only non-privileged actor allowed to manage it. |
| Root category | `Guides` for a Movie Guide, `Personalities` for a Movie Personality; auto-created on first use. |
| Subscribe | Link an existing category into a guide's anchor as a second, non-exclusive parent edge — never a copy, never exclusive to one guide. |
| Subscribed / default category | A category a guide has subscribed to; read-only from the guide's side, tracked in `MOVIE_GUIDE_DEFAULT_CATEGORY`. |
| Native category | A category created directly under a guide's anchor (not subscribed); fully manageable by the owner. |
| Reference edge | A `category_parent_child` edge created by subscribing; distinguishing feature is a matching `MOVIE_GUIDE_DEFAULT_CATEGORY` row. |
| Guide-scoped picker | The category tree dialog restricted to one guide's own anchor subtree, used to pick where to add movies. |
| Suggested category path | Dot-separated path (e.g. `Genres.Drama`) from a CSV row, resolved/created relative to the guide's target category. |
| CSV import Phase 1 | Matches CSV rows against the catalog by imdb_id and links the ones that already exist. |
| CSV import Phase 2 | Client-side OMDb lookup (2a) followed by submitting full movie payloads for rows Phase 1 couldn't resolve (2b). |
