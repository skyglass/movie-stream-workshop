# User Access Glossary

| Term | Meaning |
|------|---------|
| Authenticated user | A caller with a valid JWT accepted by the gateway and API. |
| Regular user | Authenticated user with `MOVIES_USER` but without `MOVIES_ADMIN`. |
| Admin | Authenticated user with `MOVIES_ADMIN`. |
| User extra | Local profile projection containing username, email, and avatar seed. |
| Avatar seed | Stable string used by the UI to generate an avatar image. |
| Registered users list | Admin-only read model over all `USER_EXTRA` records. |
| Realm role | Keycloak role claim mapped into `realm_access.roles`. |
| Admin menu | UI navigation entry shown only when the browser token includes `MOVIES_ADMIN`. |
