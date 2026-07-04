# Movie Catalog Glossary

| Term | Meaning                                                                         |
|------|---------------------------------------------------------------------------------|
| Movie | A catalog title identified by IMDb id and shown in the movie grid.              |
| IMDb id | Stable external identifier used as the application movie key.                   |
| Movie metadata | Title, director, writer, release year, type, poster URL, genre, and country captured from OMDb or user input. |
| Movie type | Classification of a title as Movie or Series, stored as an explicit enum code. |
| Catalog | The sorted collection of movies displayed to users.                             |
| Movie details | The selected movie plus its comments.                                           |
| Comment | Authenticated user feedback attached to a movie.                                |
| Comment author | Username from the authenticated principal at the time the comment is created.   |
| Recommendation | Authenticated user's persisted positive endorsement of one movie.               |
| Disliked movie | Authenticated user's persisted negative feedback for one movie.                 |
| Movie challenge | A two-title comparison chosen from a user's recommended movies and full series. |
| Completed challenge pair | Alphabetically sorted recommended movie pair already shown to a user.           |
| Challenge count | Number of times a movie has appeared in that user's movie challenges.           |
| Movie vote | Count of times a user selected a movie as a movie challenge winner.             |
| Direct winner-loser result | Actual Movie Challenge choice where one movie was selected over the other. |
| Transitive winner-loser result | Direct or inferred Movie Challenge ranking derived from the user's choices. |
| Favorite movie | Movie ordered by the current user's Movie Challenge vote count.                 |
| Users favorite movie | Community favorite movie ordered by total Movie Challenge votes across users.   |
| Relative user rating | Count of completed challenge pairs where another user chose the same winner as the current user. |
| Weighted movie rating | Other users' movie votes multiplied by their relative user rating and divided by the current user's total relative rating. |
| Users recommended movie | Movie not yet recommended or disliked by the current user, ordered by weighted movie rating from similar users. |
| Poster fallback | Local placeholder image used when the poster URL is empty or `N/A`.             |
| Catalog administration | Admin-only ability to view Admin Menu and Registered Users.                     |
