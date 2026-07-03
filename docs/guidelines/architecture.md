# Architecture

Read this before adding production code.

## System Shape

Movie Stream is split into three deployable parts:

- `movies-ui` - Angular UI served by Nginx.
- `movie-gateway` - Spring Cloud Gateway public REST edge. It validates UI bearer tokens and forwards them to `movies-api`.
- `movies-api` - Spring Boot resource server that owns movie and user-profile data.

The documentation hierarchy is capability-first:

```text
docs/specs/<capability>/entity_model.md
docs/specs/<capability>/glossary.md
docs/specs/<capability>/<activity>/<use-case-id>/
```

## Traceability Chain

```text
Software Capability
  -> Software Activity
    -> Use Case ID / Gherkin Feature
      -> Gherkin Scenario
        -> AcceptanceTest
          -> UseCaseApplicationService or API boundary
          -> Domain Fixture
            -> Domain Model / Repository
```

Every `uc.feature` file has a matching use-case application service when the behavior is application logic. Controllers
delegate behavior to these services instead of duplicating orchestration logic. Access-policy checks may be tested at
the API boundary when the point of the scenario is the REST lock itself.

Use-case identity must be one-to-one across documentation, application services, and BDD tests:

```text
docs/specs/movie-catalog/movie-discussion/add-movie-comment/
  -> AddMovieCommentUseCase
  -> AddMovieCommentAcceptanceTest
```

The docs folder keeps the use-case ID in dash-separated form. Java classes use the same use-case ID in CamelCase, with
`UseCase` for the application service and `AcceptanceTest` for the BDD test. Rename these together when a use-case ID
changes.

## Use-case Folder Layout

Use-case source files are grouped by domain package, while the documentation keeps the full capability/activity/use-case
hierarchy:

```text
docs/specs/<capability>/<activity>/<use-case>/
movies-api/src/main/java/.../movie/application/service/
movies-api/src/main/java/.../userextra/application/service/
movies-api/src/test/java/.../bdd/movie/
movies-api/src/test/java/.../bdd/user/
```

Do not repeat the full capability/activity/use-case tree in Java packages. The class name carries the use-case name
(`ViewMovieCatalogUseCase`, `ViewMovieCatalogAcceptanceTest`), while the domain folder (`movie` or `user`) keeps the source
layout easy to scan. Use the same CamelCase stem for the application service and its BDD use-case test.

Shared Cucumber infrastructure stays at `movies-api/src/test/java/skycomposer/moviechallenge/api/bdd`. Domain-specific
use-case tests move into `bdd/movie` or `bdd/user`; fixtures and reusable step definitions move into the corresponding
`fixture` subpackage.

## Use-case Tests

Gherkin glue is organized around use cases, with a small shared step-definition class for steps that are genuinely reused
across use cases.

- Each use case has a corresponding `*AcceptanceTest` class in `movies-api/src/test/java/skycomposer/moviechallenge/api/bdd`.
- The `*AcceptanceTest` class name must match the use-case application service stem exactly: `AddMovieCommentUseCase` pairs with `AddMovieCommentAcceptanceTest`.
- The matching `docs/specs` use-case folder uses the same stem in dash-separated form: `add-movie-comment`.
- The `*AcceptanceTest` class must read in the same Given/When/Then order as the corresponding `uc.feature` scenarios.
- `@When` methods live in the corresponding `*AcceptanceTest` class and invoke the use-case application service or the API boundary being tested.
- `@When` methods are not reused across use cases. If a new use case has a similar action, give it its own `@When` in that use case's `*AcceptanceTest`.
- `@Given` and `@Then` methods that are specific to one use case stay in the corresponding `*AcceptanceTest` class and delegate directly to the domain fixture.
- `@Given` and `@Then` methods move to `MovieCatalogStepDefinitions` or `UserAccessStepDefinitions` only after they are reused by more than one use case in that domain.
- `MovieCatalogFixture` and `UserAccessFixture` expose domain setup, recorded-result, and assertion helpers. Use-case terminology belongs in `*AcceptanceTest` step methods, not in fixture method names.
- Do not create one-line wrapper fixture classes per use case. Add another fixture only when there is real domain complexity that the current domain fixture should not own.
- Keep domain step-definition classes small. They are for reusable setup/assertion vocabulary only, not for use-case-specific behavior.

## Use-case Naming

| Layer | Example |
|-------|---------|
| Software Capability folder | `movie-catalog` |
| Software Activity folder | `catalog-discovery` |
| Use-case folder and Gherkin `Feature:` | `view-movie-catalog` |
| Java application service | `ViewMovieCatalogUseCase` |
| Java service folder | `movie/application/service/` |
| BDD use-case test class | `ViewMovieCatalogAcceptanceTest` |
| BDD use-case test folder | `bdd/movie/` |
| BDD fixture folder | `bdd/movie/fixture/` |
| REST endpoint | `GET /api/movies` |
| Primary aggregate/read model | `MOVIE_CATALOG` |

## Bounded Contexts

### movie-catalog

Owns `MOVIE` and `MOVIE_COMMENT`. Movie comments are child entities because their lifecycle is scoped to the movie.
Comment avatar rendering may read from `user-access`, but comments do not own user profiles.

### user-access

Owns `USER_EXTRA` and access-policy documentation. Keycloak owns identity and token issuance. The API consumes roles
from JWT claims and enforces access with Spring Security.

Profile and registration rules:

- Display and persist usernames from `preferred_username` or the local user record, not from the Keycloak subject UUID.
- E-mail comes from the authenticated identity or local user record and must not be synthesized from an opaque subject ID.
- `/api/userextras/me` resolves the user from the authenticated session. It must not accept a username path parameter or trust a request body username.
- Own-profile avatar changes accept only the avatar seed field. The endpoint must not expose username or e-mail update fields.
- The avatar value is a deterministic seed for the configured avatar generator, not image bytes stored in the application database.
- Keycloak owns registration and password handling. Local persistence must synchronize the registered username/e-mail reliably without making the UI invent fallback identities.

## Security Rules

- UI menu hiding is convenience only. REST authorization is the source of truth.
- For endpoints with a username path parameter, authenticated users may operate only when the parameter matches the current authenticated username, unless the endpoint is explicitly admin-only.
- Anonymous REST calls must receive `401`, not `403`; `403` is for authenticated callers without sufficient authority.
- `GET /api/userextras/me` is available to authenticated users.
- `POST /api/userextras/me` is available to authenticated users for changing only their own avatar.
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
