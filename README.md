# Movie Stream Workshop

This repository contains one combined application:

- Angular `movies-ui` served by Nginx.
- Spring Cloud Gateway as the single public REST and Swagger endpoint.
- Spring Boot `movies-api` using Java 25, PostgreSQL, JPA, and Flyway.
- Keycloak with an automatically imported realm for local OAuth2 testing.
- PostgreSQL for movie data and PostgreSQL for Keycloak.
- PgAdmin for inspecting the movie database.

The Java modules are built from one Maven parent project at the repository root.

## Requirements

- Docker with the Docker Compose plugin.
- Free local ports: `5050`, `5432`, `7000`, `8079`, and `8180`.
- An OMDb API key.

Create a `.env.local` file in the repository root before starting Compose:

```text
OMDB_API_KEY=<your-omdb-api-key>
MOVIES_PER_PAGE=50
```

The `.env.local` file is ignored by Git. If it was already staged or tracked, remove it from the Git index while keeping the local file:

```bash
git rm --cached .env.local
```

The Docker Compose stack reads `MOVIES_PER_PAGE` for the API, and the `movies-ui` Docker build reads `OMDB_API_KEY` for OMDb search and `MOVIES_PER_PAGE` for movie lists. Teardown commands such as `docker compose down -v` do not need the env file.

## Build Docker Images

If you want to build the application images before starting the stack, run:

```bash
docker compose --env-file .env.local build movies-api movie-gateway movies-ui
```

The `--env-file .env.local` part is required for the `movies-ui` image because the Docker build reads `OMDB_API_KEY` and writes it into the UI runtime configuration. Do not replace it with a dummy value.

The Java image builds use a shared BuildKit cache mounted at `/root/.m2`, so Maven dependencies are downloaded on the first Docker build and reused by later `movies-api` and `movie-gateway` builds. The cache is local to Docker and can be cleared by Docker builder prune commands.

You can also build just one image when iterating locally:

```bash
docker compose --env-file .env.local build movies-ui
docker compose --env-file .env.local build movies-api
docker compose --env-file .env.local build movie-gateway
```

## Start The Application

From the repository root:

```bash
docker compose --env-file .env.local up -d
```

If the images have not been built yet, or after source changes, use:

```bash
docker compose --env-file .env.local up --build -d
```

Watch startup logs if needed:

```bash
docker compose logs -f movie-gateway movies-api movies-ui keycloak
```

Useful URLs:

- UI: http://localhost:7000
- Gateway health: http://localhost:8079/actuator/health
- Swagger UI: http://localhost:8079/swagger-ui.html
- PgAdmin: http://localhost:5050
- Keycloak admin console: http://localhost:8180

PgAdmin login:

- Email: `admin@movies.dev`
- Password: `admin`

Keycloak admin login:

- Username: `admin`
- Password: `admin`

## Application Login

Use the login form in the top-right of the UI.

Regular user:

- Client ID: `movies-ui`
- Username: `user`
- Password: `user`

Admin user:

- Client ID: `movies-ui`
- Username: `admin`
- Password: `admin`

Regular users can see the movie list, open movie details, add comments, and use the movie wizard to create movies. Admin users can also open the admin users page.

## Specifications

A use case is treated as the minimal marketable feature. Each use case corresponds to a Gherkin `Feature` with Given /
When / Then scenarios in `uc.feature`, plus its path in the specification hierarchy.

Each epic should include a `use-case-id` and acceptance criteria as Given / When / Then scenarios. For a new use case,
add those scenarios under the matching `docs/specs/<capability>/<activity>/<use-case-id>/uc.feature` file. For an
existing use case, merge the epic acceptance criteria with the current `uc.feature` by adding the new scenarios.
Scenarios should only be removed when the epic acceptance criteria explicitly says which scenarios to delete.

## Swagger UI

Open:

```text
http://localhost:8079/swagger-ui.html
```

To call secured endpoints from Swagger:

1. Select `movies`.
2. Click `Authorize`.
3. Use the OAuth2 password form.
4. Enter client ID `movies-ui`, username `admin`, and password `admin`.
5. Leave client secret and scopes empty.

Swagger sends the password grant to the Gateway at `/auth/token`; the Gateway forwards it to Keycloak. For API calls, the Gateway performs token exchange before routing to `movies-api`.

## Public Gateway Routes

All browser and Swagger traffic goes through Spring Cloud Gateway on port `8079`:

- Movies API: `http://localhost:8079/api/movies`
- Current user profile API: `http://localhost:8079/api/userextras`
- Admin users API: `http://localhost:8079/api/users`
- Token endpoint: `http://localhost:8079/auth/token`
- Swagger UI: `http://localhost:8079/swagger-ui.html`

## Keycloak Configuration

Keycloak is configured automatically during Compose startup from:

```text
config/keycloak/realm-movies.json
```

The imported realm is `movies`. User registration is enabled. The UI `Register` action opens the Keycloak
registration page and returns to the current UI route after registration. Newly registered users are assigned to the
default `USERS` group and receive the `MOVIES_USER` role. The registration form asks for username, password, and
password confirmation only. Keycloak receives a hidden synthetic email value derived from the username.
The workshop realm uses long-lived access and SSO session lifetimes so an open browser session does not start returning
401 responses after Keycloak's short default token timeout.

If you change the realm import and need Keycloak to import it from scratch, reset volumes:

```bash
docker compose down -v
docker compose --env-file .env.local up --build -d
```

## Database

`movies-api` owns the movie database schema through Flyway migrations in:

```text
movies-api/src/main/resources/db/migration
```

The PostgreSQL model uses separate relational tables:

- `movies`
- `movie_comments`
- `users`

The `users` table stores `username` and `email` values corresponding to Keycloak users. The API synchronizes the
authenticated user from JWT claims when `/api/userextras/me` is called. This is the application-local user projection:
Keycloak remains the source of truth for identity, while Postgres stores the movie-app profile fields needed by the UI.

## Stop The Application

Stop containers while keeping persisted volumes:

```bash
docker compose down
```

Stop containers and remove persisted database and Keycloak state:

```bash
docker compose down -v
```

## AWS EC2 Deployment

The AWS deployment guide lives in `deployment/README.md`.

To run the full EC2 start workflow while reusing already published Docker images for the configured `IMAGE_VERSION`, skip Docker publishing:

```bash
./deployment/start.sh all skipDockerPublish=true
```

`skipDockerPublish` defaults to `false`; omitting it keeps Docker publishing enabled in `all` mode.

## Java Maven Project

Build all Java modules:

```bash
mvn clean package
```

Build without tests:

```bash
mvn -DskipTests package
```

Build the gateway and required reactor modules:

```bash
mvn -pl movie-gateway -am package
```
