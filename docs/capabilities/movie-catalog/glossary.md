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
| Movie challenge | A two-movie comparison chosen from a user's recommended movies.                 |
| Completed challenge pair | Alphabetically sorted recommended movie pair already shown to a user.           |
| Challenge count | Number of times a movie has appeared in that user's movie challenges.           |
| Movie vote | Count of times a user selected a movie as a movie challenge winner.             |
| Favorite movie | Movie ordered by the current user's Movie Challenge vote count.                 |
| Users favorite movie | Community favorite movie ordered by total Movie Challenge votes across users.   |
| Poster fallback | Local placeholder image used when the poster URL is empty or `N/A`.             |
| Catalog administration | Admin-only ability to view Admin Menu and Registered Users.                     |
