# User Guide

## Recommended Movies Algorithm

The Recommended Movies list suggests movies rated by users whose Movie Challenge preferences are similar to yours. 
It gives greater ranking influence to users
whose calculated ratings correlate strongly with yours and moves weakly supported movies toward the catalog average.

### 1. Calculated movie ratings

Movie Challenge choices are processed by the Bradley-Terry ranking model and converted into a calculated rating from
`1` to `10` for each ranked movie. These calculated ratings are the input to recommendation similarity.

### 2. Pearson user similarity

For the current user and each other user, the algorithm finds movies rated by both users and calculates Pearson
correlation over those shared ratings:

```text
raw_similarity = Pearson(current_user_ratings, other_user_ratings)
```

Pearson correlation measures whether two users rate movies in a similar relative pattern:

| Pearson value | Meaning |
|---:|---|
| `+1.0` | Perfectly aligned rating pattern |
| `+0.7` | Strongly similar taste |
| `+0.3` | Weakly similar taste |
| `0` | No useful relationship |
| `-0.7` | Strongly opposing taste |
| `-1.0` | Completely opposite rating pattern |

The calculation requires at least two shared rated movies. Correlation is unavailable when the shared ratings have no
variation. Users with unavailable, zero, or negative correlation do not contribute recommendations.

### 3. Shared-rating confidence

A high correlation based on only a few shared movies is less reliable than the same correlation based on many shared
movies. The algorithm therefore reduces similarity using a smooth confidence factor:

```text
confidence = shared_rating_count / (shared_rating_count + 8)
similarity = raw_similarity * confidence
```

Examples:

| Shared ratings | Confidence | Raw Pearson | Final similarity |
|---:|---:|---:|---:|
| 2 | `0.20` | `1.00` | `0.20` |
| 4 | `0.33` | `0.90` | `0.30` |
| 8 | `0.50` | `0.80` | `0.40` |
| 24 | `0.75` | `0.80` | `0.60` |
| 72 | `0.90` | `0.80` | `0.72` |

Eight shared ratings are the half-confidence point, not a hard cap. Confidence continues to increase toward `1` as
more shared ratings become available.

### 4. Regularized candidate movie score

A normal weighted average has an undesirable edge case: when only one user rated a candidate movie, that user's
similarity cancels between the numerator and denominator. The candidate receives the user's complete rating even when
their similarity is very weak.

The algorithm prevents this by treating the catalog-wide average rating as a prior with strength `1`:

```text
recommended_score =
    catalog_average + sum(movie_rating * positive_similarity)
    ---------------------------------------------------------
                  1 + sum(positive_similarity)
```

More generally, with configurable prior strength `lambda`:

```text
recommended_score =
    lambda * catalog_average + sum(movie_rating * positive_similarity)
    ------------------------------------------------------------------
                 lambda + sum(positive_similarity)
```

The current value of `lambda` is `1`. This is equivalent to adding one artificial contributor whose rating is the
catalog average and whose weight is `1`. Each real contributor's weight is their confidence-adjusted Pearson similarity.

The formula can also be written as:

```text
recommended_score = catalog_average
    + sum(similarity * (movie_rating - catalog_average))
      --------------------------------------------------
                    1 + sum(similarity)
```

This form shows that every candidate starts at the catalog average. Similar users move it away from that average:

- A weakly similar user moves the score only slightly.
- A strongly similar user moves it farther.
- Several strongly similar users can collectively overcome the prior.
- A rating below the catalog average moves the candidate downward.

For example, with catalog average `6`, one user rating a movie `10`, and similarity `0.1`:

```text
score = (6 + 10 * 0.1) / (1 + 0.1)
      = 7 / 1.1
      = 6.36
```

The catalog prior supplies `90.9%` of the effective weight and the weakly similar user supplies `9.1%`. Without
regularization, the movie would incorrectly receive the full score `10`.

With the same rating and similarity `0.5`:

```text
score = (6 + 10 * 0.5) / (1 + 0.5)
      = 7.33
```

With two users who both rate the movie `10` and each have similarity `0.8`:

```text
score = (6 + 10 * 0.8 + 10 * 0.8) / (1 + 0.8 + 0.8)
      = 8.46
```

The result therefore rewards agreement from multiple strongly similar users without allowing a single weakly similar
user to dominate candidate scores.

### 5. Candidate selection and ordering

Candidate movies come from positively correlated users and exclude every movie the current user has already recommended
or disliked. Movies are ordered by:

1. Regularized recommendation score.
2. Total positive similarity supporting the movie.
3. Number of contributing similar users.
4. Movie title and IMDb ID as deterministic tie-breakers.

A prolific user may still contribute many movies. When that user has only weak positive similarity, their uniquely
supported movies remain eligible but receive scores close to the catalog average and generally appear below movies with
stronger or broader support. If the user's Pearson correlation is zero or negative, their uniquely contributed movies
are not eligible.

## Movie Guide JSON Import Format

"Create Movie Guide" (Movie Guides page, any signed-in user) bulk-imports a hand-curated movie list from a JSON file.
Uploading it creates a `Guides > <name>` or `Personalities > <name>` category, links every listed movie to it, and
lands you on the catalog filtered to that category. A name that's already taken by an existing Guide/Personality is
rejected — bulk import only ever creates a brand-new one; to add or remove movies/categories on an existing one, edit
it directly on its own page instead of re-importing.

A **Guide** is a themed list (e.g. "Heist Movies"). A **Personality** is a real film expert, critic, or movie fan
whose taste is publicly known — the "personality" is the one *doing the recommending*, not necessarily the subject
of the movies. An actor or director can absolutely be the persona (e.g. "Robert De Niro — Italian Neorealism Picks"),
but only because of their well-known taste and opinions; their own filmography belongs under `Directors`/`Writers`
instead, not under a Personality category named after them.

### Two account tiers — same JSON format, different category resolution

The JSON shape is identical either way. Creating the `Guides > <name>` / `Personalities > <name>` container itself is
open to everyone. What differs is how a movie's `categories` dot-paths get resolved:

- **`MOVIES_GUIDE` role or admin** — each path segment is created if it doesn't already exist (walking root to leaf).
  This role is assigned per-user in Keycloak by an admin, precisely because unrestricted category creation at
  bulk-import scale is a real abuse surface (spam/obscene category names, resource exhaustion) — see the request-size
  and count limits below.
- **Everyone else (any signed-in user)** — nothing is ever created. Each path is walked root to leaf against
  *existing* categories only; the moment any segment (including the root) doesn't match, that whole path is silently
  dropped for that movie rather than erroring. Browse "Select Categories" in the filter bar to see the current tree
  before writing (or generating) your file, and copy a path from there exactly.

Either way, any `imdbId` your account can't resolve (nonexistent, or OMDb can't find it either) is silently skipped
rather than failing the whole upload — but the request as a whole is still capped: **1000 movies / 20,000 total
category references** for `MOVIES_GUIDE`/admin accounts, **100 movies / 700 total category references** for everyone
else. A file over 5 MB is rejected before it's even read.

### Top-level fields

| Field | Type | Required | Meaning |
|---|---|---|---|
| `type` | `"Guide"` \| `"Personality"` | yes | Exact casing. Chooses the `Guides` or `Personalities` root. |
| `name` | string | yes | The guide/personality's own category name, e.g. `"Heist Movies"` or `"Robert De Niro — Italian Neorealism Picks"`. |
| `description` | string | no | Stored on that category the first time it's created; left alone on later uploads. |
| `icon` | string | no | A single emoji for the guide/personality's category (e.g. `"🔫"` for a heist guide). Falls back to a generic 🗺️/🌟 if omitted. |
| `movies` | array | yes | See below. |

### Each `movies[]` entry

| Field | Type | Required | Meaning |
|---|---|---|---|
| `imdbId` | string | yes | A real IMDb id (`tt...`). Every movie must have one — this is what's actually used to match or create the movie. |
| `title` | string | yes | The movie's title. Used only to render the pre-upload preview (see below) — never sent for reconciliation. |
| `year` | string | yes | The movie's release year. Preview-only, same as `title`. |
| `director` | string | yes | The movie's director. Preview-only, same as `title`. |
| `categories` | array of strings | yes | One or more **dot-separated full paths**, root to leaf, e.g. `"Directors.Vittorio De Sica"` or `"Recommended By.Robert De Niro's Italian Neorealism Picks.The Human Cost of Poverty"`. Any number of levels is allowed. `MOVIES_GUIDE`/admin accounts auto-create any segment that doesn't exist; other accounts must match an existing path exactly, or it's dropped (see above). |

`title`/`year`/`director` exist purely so the app can show you a **preview** of every movie before anything is created —
a chance to catch an AI hallucinating a movie into the wrong category, or picking a movie that doesn't actually fit.
On that preview screen you can edit a movie's categories or remove it from the list entirely before submitting.

Every movie is also linked to the guide/personality category itself — you don't need to (and shouldn't) repeat the
`type`/`name` as one of its own `categories` entries.

### What "categories" should actually contain

A category path can be:
- **A reuse of something that already exists** — `"Genres.Crime"`, `"Directors.Vittorio De Sica"`, or one of the
  curated narrative paths (`"Narratives.Greek & Roman Mythology.Prometheus"`) all resolve to the existing category
  instead of creating a duplicate.
- **A brand-new, invented path** that captures *why this specific movie belongs in this specific guide/personality* —
  this is the interesting part. When you (or an AI assistant) curate the list, write a short private note for each
  movie explaining the judgment call — what makes it fit the theme, or why this persona would recommend it — and turn
  that note into a category path instead of leaving it as prose. A deeper path is fine and often better:
  `"Recommended By.Robert De Niro's Italian Neorealism Picks.The Human Cost of Poverty"` says far more than a flat
  `"Neorealism"` tag would. For a Personality specifically, remember the path should describe *why the persona
  recommends it*, not a fact about the persona's own career — their own filmography belongs under `Directors`/`Writers`.

Re-uploading the same file is always safe — every step is idempotent (existing categories are reused, existing
`movie_category` links are no-ops).

### Minimal 2-movie example

```json
{
  "type": "Personality",
  "name": "Robert De Niro — Italian Neorealism Picks",
  "description": "The postwar Italian films De Niro has cited as formative to his own sense of screen truth.",
  "icon": "🤌",
  "movies": [
    {
      "imdbId": "tt0040959",
      "title": "Bicycle Thieves",
      "year": "1948",
      "director": "Vittorio De Sica",
      "categories": ["Directors.Vittorio De Sica", "Recommended By.Robert De Niro's Italian Neorealism Picks.The Human Cost of Poverty"]
    },
    {
      "imdbId": "tt0038650",
      "title": "Rome, Open City",
      "year": "1945",
      "director": "Roberto Rossellini",
      "categories": ["Directors.Roberto Rossellini", "Recommended By.Robert De Niro's Italian Neorealism Picks.Wartime Resistance Under Occupation"]
    }
  ]
}
```

### Generating a file with an AI assistant

This format is deliberately easy for an AI assistant (ChatGPT, Claude, Codex, etc.) to produce: ask it to role-play a
domain expert curating a Guide, or a real, publicly-known film expert/critic/movie fan *recommending* movies for a
Personality — hand-pick a real, specific movie list for that topic/persona, and translate its own judgment notes
directly into category paths. A reusable prompt template:

```text
For a Guide: you are <a real or invented domain expert>, curating a movie guide called "<Guide name>".
For a Personality: you are simulating <a real film expert, critic, or movie fan whose taste is publicly known, e.g.
"Robert De Niro" or "a Cahiers du Cinéma critic">, recommending movies the way this person genuinely would, for a
list called "<Personality name>". The personality is the expert doing the recommending, not necessarily the subject
of the movies — an actor/director can be the persona, but only for their known taste, not their own filmography
(which belongs under Directors/Writers instead).

Hand-pick <12–25, or fewer for a tightly-scoped theme> real movies that best represent this topic/persona. For each
movie:
1. Look up (or recall) its real IMDb id (tt...), title, release year, and director — double-check these are real
   and accurate, not invented.
2. Write yourself a one-sentence note on *why* this specific movie earned its place on the list.
3. Turn that note into one or more dot-separated category paths (root → ... → leaf, any depth) that capture why it
   fits — e.g. "Genres.Thriller" or a new, specific path under a topic-relevant root.

Output only this JSON shape (no commentary). Include "title", "year" and "director" for every movie so they can be
previewed before upload — "imdbId" is still what's actually used to match/create the movie:
{
  "type": "Guide" or "Personality",
  "name": "<title>",
  "description": "<one sentence>",
  "icon": "<a single emoji>",
  "movies": [ { "imdbId": "tt...", "title": "...", "year": "...", "director": "...", "categories": ["...", "..."] }, ... ]
}
```

### Worked examples

Three different framings of the same Personality persona, and three unrelated Guide topics — each trimmed to 2
movies here for brevity (a real upload would typically include far more). Note that none of the movies in the
Personality examples star or were directed by Robert De Niro — he's the expert doing the recommending, not the
subject.

**Personality 1 — Robert De Niro's Italian Neorealism picks** (postwar films he's cited as formative to his own sense of screen truth):

```json
{
  "type": "Personality",
  "name": "Robert De Niro — Italian Neorealism Picks",
  "description": "The postwar Italian films De Niro has cited as formative to his own sense of screen truth.",
  "icon": "🤌",
  "movies": [
    { "imdbId": "tt0040959", "title": "Bicycle Thieves", "year": "1948", "director": "Vittorio De Sica", "categories": ["Directors.Vittorio De Sica", "Recommended By.Robert De Niro's Italian Neorealism Picks.The Human Cost of Poverty"] },
    { "imdbId": "tt0038650", "title": "Rome, Open City", "year": "1945", "director": "Roberto Rossellini", "categories": ["Directors.Roberto Rossellini", "Recommended By.Robert De Niro's Italian Neorealism Picks.Wartime Resistance Under Occupation"] }
  ]
}
```

**Personality 2 — Robert De Niro's classic Hollywood noir favorites** (the studio-era films he's pointed to as touchstones):

```json
{
  "type": "Personality",
  "name": "Robert De Niro — Classic Hollywood Noir Favorites",
  "description": "The studio-era noirs whose moral shading De Niro has named as touchstones.",
  "icon": "🕵️",
  "movies": [
    { "imdbId": "tt0043014", "title": "Sunset Boulevard", "year": "1950", "director": "Billy Wilder", "categories": ["Directors.Billy Wilder", "Styles.Influences.Classical Hollywood Style", "Recommended By.Robert De Niro's Classic Hollywood Noir Favorites.Faded Stardom's Dark Side"] },
    { "imdbId": "tt0036775", "title": "Double Indemnity", "year": "1944", "director": "Billy Wilder", "categories": ["Directors.Billy Wilder", "Styles.Influences.Film Noir", "Recommended By.Robert De Niro's Classic Hollywood Noir Favorites.Greed Disguised as Romance"] }
  ]
}
```

**Personality 3 — Robert De Niro's method acting masters** (the performances he's credited with shaping his own approach):

```json
{
  "type": "Personality",
  "name": "Robert De Niro — Method Acting Masters",
  "description": "The performances De Niro has credited as shaping his own approach to the craft.",
  "icon": "🎭",
  "movies": [
    { "imdbId": "tt0047296", "title": "On the Waterfront", "year": "1954", "director": "Elia Kazan", "categories": ["Directors.Elia Kazan", "Recommended By.Robert De Niro's Method Acting Masters.The Conflicted Conscience"] },
    { "imdbId": "tt0044081", "title": "A Streetcar Named Desire", "year": "1951", "director": "Elia Kazan", "categories": ["Directors.Elia Kazan", "Writers.Tennessee Williams", "Narratives.Classic Theatre & Tragedy.Blanche DuBois", "Recommended By.Robert De Niro's Method Acting Masters.Raw Emotional Volatility"] }
  ]
}
```

**Guide 1 — Heist movies: masterworks of the perfect plan** (judged by how deliberately the plan/execution/twist structure is built, not just genre):

```json
{
  "type": "Guide",
  "name": "Heist Movies: Masterworks of the Perfect Plan",
  "description": "Films built entirely around the construction, execution, and unraveling of a meticulous plan.",
  "icon": "🔫",
  "movies": [
    { "imdbId": "tt0070735", "title": "The Sting", "year": "1973", "director": "George Roy Hill", "categories": ["Genres.Comedy", "Heist Mechanics.The Perfect Plan.The Long Con"] },
    { "imdbId": "tt0105236", "title": "Reservoir Dogs", "year": "1992", "director": "Quentin Tarantino", "categories": ["Directors.Quentin Tarantino", "Heist Mechanics.The Perfect Plan.The Job Gone Wrong"] }
  ]
}
```

**Guide 2 — Existential sci-fi: questioning what it means to be human** (judged by philosophical weight, not spectacle):

```json
{
  "type": "Guide",
  "name": "Existential Sci-Fi: Questioning What It Means to Be Human",
  "description": "Science fiction that uses its premise to interrogate identity, memory, and consciousness.",
  "icon": "🤖",
  "movies": [
    { "imdbId": "tt0083658", "title": "Blade Runner", "year": "1982", "director": "Ridley Scott", "categories": ["Genres.Sci-Fi", "Philosophical Questions.What Makes Us Human.Manufactured Memory"] },
    { "imdbId": "tt2543164", "title": "Arrival", "year": "2016", "director": "Denis Villeneuve", "categories": ["Genres.Sci-Fi", "Philosophical Questions.What Makes Us Human.Language Reshapes Time"] }
  ]
}
```

**Guide 3 — Underdog sports dramas: triumph against the odds** (judged by the shape of the underdog arc, across different sports):

```json
{
  "type": "Guide",
  "name": "Underdog Sports Dramas: Triumph Against the Odds",
  "description": "Sports films where the emotional core is the unlikely rise, not the sport itself.",
  "icon": "🏆",
  "movies": [
    { "imdbId": "tt0075148", "title": "Rocky", "year": "1976", "director": "John G. Avildsen", "categories": ["Genres.Sport", "Narrative Arcs.Triumph Against the Odds.The Unlikely Contender"] },
    { "imdbId": "tt0405159", "title": "Cinderella Man", "year": "2005", "director": "Ron Howard", "categories": ["Genres.Sport", "Narrative Arcs.Triumph Against the Odds.The Late-Blooming Fighter"] }
  ]
}
```
