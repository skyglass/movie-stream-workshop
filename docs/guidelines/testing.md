# Testing

Read this before writing or modifying tests.

## Framework

- Cucumber JVM executes use-case scenarios from `docs/capabilities/**/uc.feature`.
- `Feature:` names must equal the dash-separated use-case folder name.
- Cucumber glue lives under `movies-api/src/test/java/com/ivanfranchin/moviesapi/bdd`.
- Each use case has a corresponding `*UseCaseTest` class.
- The use-case ID must line up one-to-one across docs, application service, and BDD test:
  `add-movie-comment` -> `AddMovieCommentUseCase` -> `AddMovieCommentUseCaseTest`.
- Put movie use-case BDD classes under `bdd/movie` and user use-case BDD classes under `bdd/user`.
- Keep shared Cucumber infrastructure at the BDD root: Cucumber configuration and hooks.
- Keep domain fixtures and reusable domain step definitions in a `fixture` subpackage under the domain tests: `bdd/movie/fixture` and `bdd/user/fixture`.
- The `*UseCaseTest` class must read in the same Given/When/Then order as the corresponding `uc.feature` scenarios.
- The `*UseCaseTest` class name uses the same CamelCase stem as the corresponding application service.
- `@When` methods execute the corresponding use-case application service or the API boundary being tested.
- `@When` methods are use-case behavior and must stay in the owning `*UseCaseTest`; do not move them to shared step definitions.
- Use-case-specific `@Given` and `@Then` methods stay in the corresponding `*UseCaseTest` class and delegate directly to the domain fixture.
- Reusable cross-use-case `@Given` and `@Then` methods live in the domain step-definition class and delegate to the domain fixture. Move a step there only after actual reuse exists in that domain.
- API authorization scenarios use `MockMvc` with JWT test support so the API lock is tested directly.

## Fixture Rules

- `MovieCatalogFixture` and `UserAccessFixture` are the shared domain fixtures for Cucumber scenarios.
- Use-case tests use the corresponding domain fixture directly for setup, recorded result state, and assertions.
- Keep fixture method names generic. Use-case terminology belongs in `*UseCaseTest` step methods and `uc.feature` files.
- Do not create per-use-case fixture classes that only forward to a domain fixture.
- Fixtures prepare test data; they do not duplicate production business rules.
- Database cleanup runs before and after every Cucumber scenario.
- Cleanup must be idempotent. It must be safe to run the same Cucumber pipeline twice against the same workspace.

## Database

Cucumber uses an in-memory H2 database in PostgreSQL mode. Flyway applies the production schema, and fixtures create
only the records required by each scenario.

## Cucumber Glue Rules

- Keep domain step-definition classes limited to `@Given` and `@Then` steps reused by more than one use case in that domain.
- Do not put `@When` methods in domain step-definition classes; `@When` belongs to the owning use-case test.
- Keep use-case-specific Gherkin wording in the owning use-case test class.
- Keep assertions business-readable: catalog order, profile status, forbidden access, and comment visibility.
- Do not write impossible scenarios. For example, an avatar-only endpoint cannot test username or e-mail mutation through the same request.
- Use MockMvc only for REST access-policy checks.
- Use application services for use-case behavior checks.

## Identity and Authorization Scenarios

- Use `preferred_username` in JWT fixtures so tests catch accidental display of Keycloak subject UUIDs.
- `/me` scenarios must derive the username from the authenticated session.
- Anonymous API scenarios must assert `401`. Use `403` only for authenticated users who are forbidden.
- For profile avatar changes, test the avatar seed string. Do not describe it as image bytes.

## Required Verification

After changing use-case services, use-case tests, fixtures, Cucumber steps, or feature files, run the same pipeline twice:

```bash
mvn -pl movies-api test
mvn -pl movies-api test
```

Because this project targets Java 25, run the commands with a Java 25 runtime when the local JDK is older.
