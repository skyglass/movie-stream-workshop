# Testing

Read this before writing or modifying tests.

## Framework

- Cucumber JVM executes use-case scenarios from `docs/capabilities/**/uc.feature`.
- `Feature:` names must equal the dash-separated use-case folder name.
- Step definitions live under `movies-api/src/test/java/com/ivanfranchin/moviesapi/bdd`.
- Step definitions call use-case application services for business behavior.
- API authorization scenarios use `MockMvc` with JWT test support so the API lock is tested directly.

## Fixture Rules

- Test-only fixtures live under `movies-api/src/test/java/com/ivanfranchin/moviesapi/bdd`.
- Fixtures prepare test data; they do not duplicate production business rules.
- Database cleanup runs before and after every Cucumber scenario.
- Cleanup must be idempotent. It must be safe to run the same Cucumber pipeline twice against the same workspace.

## Database

Cucumber uses an in-memory H2 database in PostgreSQL mode. Flyway applies the production schema, and fixtures create
only the records required by each scenario.

## Step Definition Rules

- Reuse generic steps where the wording is shared across use cases.
- Keep assertions business-readable: catalog order, profile status, forbidden access, and comment visibility.
- Use MockMvc only for REST access-policy checks.
- Use application services for use-case behavior checks.

## Required Verification

After changing use-case services, Cucumber steps, or feature files, run the same pipeline twice:

```bash
mvn -pl movies-api test
mvn -pl movies-api test
```

Because this project targets Java 25, run the commands with a Java 25 runtime when the local JDK is older.
