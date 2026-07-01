# Entity Model

This file is the full-system reference. The DDD source of truth is split by Software Capability under
[`docs/capabilities`](capabilities/), where each capability owns `entity_model.md` and `glossary.md`.

## Bounded Context Overview

```mermaid
flowchart LR
    subgraph movie_catalog["movie-catalog"]
        MOVIE["MOVIE\nAggregate Root"]
        MOVIE_COMMENT["MOVIE_COMMENT\nChild Entity"]
        MOVIE_RECOMMENDATION["MOVIE_RECOMMENDATION\nUser recommendation"]
        USER_MOVIE_PAIR_CHALLENGE["USER_MOVIE_PAIR_CHALLENGE\nCompleted pair"]
        MOVIE_USER_VOTE["MOVIE_USER_VOTE\nChallenge winner votes"]
    end

    subgraph user_access["user-access"]
        USER_EXTRA["USER_EXTRA\nUser Profile Projection"]
        ROLE["ROLE\nAccess Policy Concept"]
    end

    MOVIE ||--o{ MOVIE_COMMENT : receives
    MOVIE ||--o{ MOVIE_RECOMMENDATION : recommended_by
    MOVIE ||--o{ USER_MOVIE_PAIR_CHALLENGE : appears_in_pair
    MOVIE ||--o{ MOVIE_USER_VOTE : receives_vote
    MOVIE_COMMENT --> USER_EXTRA : "renders avatar for username"
    ROLE --> USER_EXTRA : "protects admin listing"
```

## Entity Relationship Diagram

```mermaid
erDiagram
    MOVIES ||--o{ MOVIE_COMMENTS : receives
    MOVIES ||--o{ MOVIE_RECOMMENDATIONS : recommended_by
    MOVIES ||--o{ USER_MOVIE_PAIR_CHALLENGE : appears_in_pair
    MOVIES ||--o{ MOVIE_USER_VOTES : receives_vote
    USERS ||--o{ MOVIE_COMMENTS : "referenced by username"
    USERS ||--o{ MOVIE_RECOMMENDATIONS : makes
    USERS ||--o{ USER_MOVIE_PAIR_CHALLENGE : completes
    USERS ||--o{ MOVIE_USER_VOTES : casts
```

### MOVIE

Represents a title in the Movie Stream catalog.

| Attribute | Description | Data Type | Validation Rules |
|-----------|-------------|-----------|------------------|
| imdb_id | External movie identifier | String | Primary Key, Not Blank |
| title | Display title | String | Not Null, Not Blank on create |
| director | Director or `N/A` | String | Not Null, Not Blank on create |
| release_year | Release year or `N/A` | String | Not Null, Not Blank on create |
| poster | Poster URL | String | Optional, max 2048 characters |

### MOVIE_COMMENT

Represents a user-authored comment attached to one movie.

| Attribute | Description | Data Type | Validation Rules |
|-----------|-------------|-----------|------------------|
| id | Unique comment identifier | Long | Primary Key, Identity |
| movie_imdb_id | Owning movie reference | String | Foreign Key to `movies.imdb_id`, Cascade Delete |
| username | Author username from JWT principal | String | Not Null |
| text | Comment text | String | Not Blank, max 4000 characters |
| timestamp | Creation instant | Instant | Not Null, ordered newest first |

### USER_EXTRA

Represents the application-local profile projection for an authenticated identity.

| Attribute | Description | Data Type | Validation Rules |
|-----------|-------------|-----------|------------------|
| username | Username from JWT claims | String | Primary Key, Not Blank |
| email | Email from JWT claims or fallback | String | Not Null |
| avatar | Avatar seed used by the UI | String | Not Null |

### MOVIE_RECOMMENDATION

Represents one authenticated user's recommendation of one movie.

| Attribute | Description | Data Type | Validation Rules |
|-----------|-------------|-----------|------------------|
| user_id | Recommending username | String | Foreign Key to `users.username`, Primary Key part |
| movie_id | Recommended movie | String | Foreign Key to `movies.imdb_id`, Primary Key part |

### USER_MOVIE_PAIR_CHALLENGE

Represents one completed Movie Challenge pair for one user.

| Attribute | Description | Data Type | Validation Rules |
|-----------|-------------|-----------|------------------|
| user_id | Challenged username | String | Foreign Key to `users.username`, Primary Key part |
| movie1_id | Alphabetically first movie id | String | Foreign Key to `movies.imdb_id`, Primary Key part |
| movie2_id | Alphabetically second movie id | String | Foreign Key to `movies.imdb_id`, Primary Key part, greater than movie1_id |
| movie1_wins | Whether movie1 won the pair | Boolean | Not Null |

### MOVIE_USER_VOTE

Represents Movie Challenge winner counts by user and movie.

| Attribute | Description | Data Type | Validation Rules |
|-----------|-------------|-----------|------------------|
| user_id | Voting username | String | Foreign Key to `users.username`, Primary Key part |
| movie_id | Selected movie winner | String | Foreign Key to `movies.imdb_id`, Primary Key part |
| vote_count | Number of times this user selected this movie | Integer | Not Null, non-negative |

## Cross-Context Policies

- `MOVIE` owns `MOVIE_COMMENT` as a child entity because comment lifecycle is scoped to the movie.
- `USER_EXTRA` does not own comments. Comments store usernames only and resolve avatar data through the user-access
  read model.
- `MOVIES_ADMIN` is required for `/api/users` and movie administration endpoints.
- Authenticated non-admin users can read only their own profile through `GET /api/userextras/me`.
- Users recommended movies exclude the current user's own recommendations and rank remaining movies with weighted
  Movie Challenge votes from users who chose the same winners in completed challenge pairs.
