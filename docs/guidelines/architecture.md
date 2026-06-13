# Architecture

Read this before adding production code.

## System Shape

Movie Stream is split into three deployable parts:

- `movies-ui` - Angular UI served by Nginx.
- `movie-gateway` - Spring Cloud Gateway, public REST edge, Keycloak token adapter, and token exchange boundary.
- `movies-api` - Spring Boot resource server that owns movie and user-profile data.

The documentation hierarchy is capability-first:

```text
docs/capabilities/<capability>/entity_model.md
docs/capabilities/<capability>/glossary.md
docs/capabilities/<capability>/activities/<activity>/use-cases/<use-case-id>/
```

## Traceability Chain

```text
Software Capability
  -> Software Activity
    -> Use Case / Gherkin Feature
      -> Gherkin Scenario
        -> Cucumber Step Definition
          -> UseCaseApplicationService
            -> Domain Model / Repository
```

Every `uc.feature` file has a matching use-case application service. Controllers delegate behavior to these services
instead of duplicating orchestration logic.

## Use-case Naming

| Layer | Example |
|-------|---------|
| Software Capability folder | `movie-catalog` |
| Software Activity folder | `catalog-discovery` |
| Use-case folder and Gherkin `Feature:` | `view-movie-catalog` |
| Java application service | `ViewMovieCatalogUseCase` |
| REST endpoint | `GET /api/movies` |
| Primary aggregate/read model | `MOVIE_CATALOG` |

## Bounded Contexts

### movie-catalog

Owns `MOVIE` and `MOVIE_COMMENT`. Movie comments are child entities because their lifecycle is scoped to the movie.
Comment avatar rendering may read from `user-access`, but comments do not own user profiles.

### user-access

Owns `USER_EXTRA` and access-policy documentation. Keycloak owns identity and token issuance. The API consumes roles
from JWT claims and enforces access with Spring Security.

## Security Rules

- UI menu hiding is convenience only. REST authorization is the source of truth.
- `GET /api/userextras/me` is available to authenticated users.
- `/api/users` and `/api/users/**` require `MOVIES_ADMIN`.
- Movie update and delete operations require `MOVIES_ADMIN`.
- New movie creation and commenting require authentication.

## Persistence Rules

- Flyway migrations define the database schema.
- JPA entities model the current persistence shape.
- Application services are transaction/orchestration boundaries.
- Repositories are injected into services through existing service classes where possible.

## Frontend Rule

The Angular UI may route directly to current screens (`/home`, `/wizard`, `/admin/users`), but menu visibility must not
be treated as authorization. Any future Admin sub-sections should remain under `/admin/*` and continue to rely on API
authorization.
