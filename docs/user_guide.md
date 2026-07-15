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
