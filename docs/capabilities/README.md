# Software Capability Specification Index

This directory is the DDD-oriented specification root for Movie Stream. Top-level folders represent Software
Capabilities. A Software Capability owns the capability-level domain model and groups Software Activities that create
value inside that capability.

Each Software Capability owns:

- `entity_model.md` - the capability domain model slice.
- `glossary.md` - the bounded-context ubiquitous language.
- `activities/<activity>/use-cases/<use-case-id>/uc.feature` - executable Gherkin scenarios for that use case.
- `activities/<activity>/use-cases/<use-case-id>/uc.puml` - PlantUML use-case and aggregate interaction diagram.

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
| `movie-catalog` | `catalog-administration` | `administer-movie-catalog` | `movie`, `security` |
| `user-access` | `profile-access` | `view-own-user-profile` | `userextra` |
| `user-access` | `user-administration` | `view-registered-users` | `userextra`, `security` |

## Implementation Naming

Every use case has a matching application service. Existing REST paths remain resource-oriented for the workshop UI,
but controllers delegate use-case behavior to services named after the use case.

| Use-case id | REST endpoint | Application service | Primary model |
|-------------|---------------|---------------------|---------------|
| `view-movie-catalog` | `GET /api/movies` | `ViewMovieCatalogUseCase` | `MOVIE` read model |
| `view-movie-details` | `GET /api/movies/{imdbId}` | `ViewMovieDetailsUseCase` | `MOVIE` aggregate |
| `add-movie-to-catalog` | `POST /api/movies` | `AddMovieToCatalogUseCase` | `MOVIE` aggregate |
| `add-movie-comment` | `POST /api/movies/{imdbId}/comments` | `AddMovieCommentUseCase` | `MOVIE_COMMENT` child entity |
| `view-own-user-profile` | `GET /api/userextras/me` | `ViewOwnUserProfileUseCase` | `USER_EXTRA` |
| `view-registered-users` | `GET /api/users` | `ViewRegisteredUsersUseCase` | `USER_EXTRA` listing |
| `administer-movie-catalog` | `PUT/DELETE /api/movies/{imdbId}` | `AdministerMovieCatalogUseCase` | `MOVIE` aggregate |
