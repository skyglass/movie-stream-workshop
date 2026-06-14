# Movie Catalog Glossary

| Term | Meaning                                                                         |
|------|---------------------------------------------------------------------------------|
| Movie | A catalog title identified by IMDb id and shown in the movie grid.              |
| IMDb id | Stable external identifier used as the application movie key.                   |
| Movie metadata | Title, director, release year, and poster URL captured from OMDb or user input. |
| Catalog | The sorted collection of movies displayed to users.                             |
| Movie details | The selected movie plus its comments.                                           |
| Comment | Authenticated user feedback attached to a movie.                                |
| Comment author | Username from the authenticated principal at the time the comment is created.   |
| Recommendation | Authenticated user's persisted endorsement of one movie.                        |
| Poster fallback | Local placeholder image used when the poster URL is empty or `N/A`.             |
| Catalog administration | Admin-only ability to view Admin Menu and Registered Users.                     |
