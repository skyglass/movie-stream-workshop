# Software Capability Specification Index

This directory is the DDD-oriented specification root for Movie Stream. Top-level folders represent Software
Capabilities. A Software Capability owns the capability-level domain model and groups Software Activities that create
value inside that capability.

Each Software Capability owns:

- `entity_model.md` - the capability domain model slice.
- `glossary.md` - the bounded-context ubiquitous language.
- `<activity>/<use-case-id>/uc.feature` - executable Gherkin scenarios for that use case.
- `<activity>/<use-case-id>/uc.puml` - PlantUML use-case and aggregate interaction diagram.

Use-case folder names are short dash-separated ids. The `Feature:` name in each `uc.feature` file matches the folder
name exactly.

## Goal Hierarchy

| Level | Meaning |
|-------|---------|
| Software Capability | Strategic business boundary and candidate bounded context. |
| Software Activity | Behavioral boundary that groups related workflows inside a capability. |
| Use Case / Gherkin Feature | Delivery boundary with one use-case application service and executable acceptance scenarios. |
| Gherkin Scenario | Business-relevant example for a supported flow or policy. |
| Cucumber Step Definition | Test adapter that translates scenario language into application-service calls and fixture setup. |
| Application Service | Transaction and orchestration boundary for the use case. |
| Domain Model | Aggregate roots, child entities, value objects, repository ports, and policies. |

## Capabilities And Activities

| Software Capability | Software Activity | Use cases | Current code package |
|---------------------|-------------------|-----------|----------------------|
| `movie-catalog` | `catalog-discovery` | `view-movie-catalog`, `view-movie-details` | `movie` |
| `movie-catalog` | `catalog-contribution` | `add-movie-to-catalog` | `movie` |
| `movie-catalog` | `movie-discussion` | `add-movie-comment` | `movie` |
| `movie-catalog` | `movie-recommendation` | `recommend-movie`, `movie-challenge`, `view-favorite-movies`, `share-my-favorite-movies`, `view-users-favorite-movies`, `view-users-recommended-movies`, `view-similar-to-favorite-movies`, `view-similar-movies` | `movie` |
| `movie-catalog` | `catalog-administration` | `administer-movie-catalog` | `movie`, `security` |
| `user-access` | `profile-access` | `view-own-user-profile`, `change-own-avatar` | `userextra` |
| `user-access` | `user-administration` | `view-registered-users` | `userextra`, `security` |
| `movie-guides` | `guide-curation` | `curate-movie-guide` | `movie` |

## Implementation Naming

Every use case has a matching application service. Existing REST paths remain resource-oriented for the workshop UI,
but controllers delegate use-case behavior to services named after the use case.

| Use-case id | REST endpoint | Application service | Primary model |
|-------------|---------------|---------------------|---------------|
| `view-movie-catalog` | `GET /api/movies` | `ViewMovieCatalogUseCase` | `MOVIE` read model |
| `view-movie-details` | `GET /api/movies/{imdbId}` | `ViewMovieDetailsUseCase` | `MOVIE`, `USER_MOVIE_CHALLENGE_VOTE`, `USER_MOVIE_RATING` read models |
| `add-movie-to-catalog` | `POST /api/movies` | `AddMovieToCatalogUseCase` | `MOVIE` aggregate |
| `add-movie-comment` | `POST /api/movies/{imdbId}/comments` | `AddMovieCommentUseCase` | `MOVIE_COMMENT` child entity |
| `recommend-movie` | `POST/DELETE /api/movies/{imdbId}/recommendation`, `POST /api/movies/{imdbId}/recommendation/replay`, `POST /api/movies/{imdbId}/recommendation/dislike`, `POST /api/movies/recommendation` | `RecommendMovieUseCase` | `MOVIE_RECOMMENDATION`, `USER_MOVIE_CHALLENGE_VOTE`, `USER_MOVIE_RANK` |
| `movie-challenge` | `GET /api/movie-challenges/next`, `GET /api/movie-challenges/suggested`, `POST /api/movie-challenges/votes`, `POST /api/movie-challenges/votes/batch` | `MovieChallengeUseCase` | `USER_MOVIE_CHALLENGE_VOTE`, `USER_MOVIE_RANK` |
| `view-favorite-movies` | `GET /api/favorite-movies` | `ViewFavoriteMoviesUseCase` | `USER_MOVIE_RATING` read model |
| `share-my-favorite-movies` | `GET/POST/DELETE /api/favorite-movies/share`, `GET /api/my-favorite-movies/{encodedUsername}` | `ShareMyFavoriteMoviesUseCase` | `USER_SETTINGS`, `USER_MOVIE_RATING` read model |
| `view-users-favorite-movies` | `GET /api/users-favorite-movies` | `ViewUsersFavoriteMoviesUseCase` | `USER_MOVIE_RATING` aggregate read model |
| `view-users-recommended-movies` | `GET /api/users-recommended-movies` | `ViewUsersRecommendedMoviesUseCase` | `USER_MOVIE_RATING` Pearson-similarity read model |
| `view-similar-to-favorite-movies` | `GET /api/favorite-movies/similar` | `ViewCategorySimilarMoviesUseCase` | `USER_MOVIE_RATING`, `MOVIE_CATEGORY` category-affinity read model |
| `view-similar-movies` | `GET /api/movies/{imdbId}/similar-movies` | `ViewCategorySimilarMoviesUseCase` | `USER_MOVIE_RATING`, `MOVIE_CATEGORY` category-affinity read model (single-movie seed) |
| `view-own-user-profile` | `GET /api/userextras/me` | `ViewOwnUserProfileUseCase` | `USER_EXTRA` |
| `change-own-avatar` | `POST /api/userextras/me` | `ChangeOwnAvatarUseCase` | `USER_EXTRA` |
| `view-registered-users` | `GET /api/users` | `ViewRegisteredUsersUseCase` | `USER_EXTRA` listing |
| `curate-movie-guide` | `POST /api/movie-guides/wizard`, `POST /api/movie-guides/{id}/subscribe`, `GET /api/movie-guides/by-category/{id}`, `GET /api/movie-guides/mine`, `POST /api/movie-guides/{id}/wizard-movies`, `GET /api/movie-guides/{id}/movies`, `POST /api/movie-guides/{id}/import-csv`, `POST /api/movie-guides/{id}/import-csv/complete`, `DELETE /api/categories/{id}` | `MovieGuideService` | `MOVIE_GUIDE`, `MOVIE_GUIDE_DEFAULT_CATEGORY` |
| `administer-movie-catalog` | `PUT/DELETE /api/movies/{imdbId}` | `AdministerMovieCatalogUseCase` | `MOVIE` aggregate |
