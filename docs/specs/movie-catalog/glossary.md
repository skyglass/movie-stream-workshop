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
| Movie challenge | A two-title comparison chosen from a user's positively recommended movies. |
| Direct challenge vote | Actual Movie Challenge choice where one movie was selected over the other. |
| Completed challenge pair | Recommended movie pair that already has a direct challenge vote from the user. |
| User movie rank | Per-user ranking projection rebuilt from direct challenge votes. |
| Direct comparison count | Number of direct challenge votes where the movie was either winner or loser. |
| Challenge confidence | Calculated value from direct comparison count; high confidence lets the selector skip far-apart ranked pairs. |
| Movie rating | Rating from 0 to 10 derived from the user's current rank positions. |
| Your Rank | Current viewer's own rank for a movie; list cards show it in parentheses after `Your Rating`. |
| Your Rating | UI label for the current viewer's own 0-10 rating; list cards show `rating (#rank)` or `-`. |
| Favorite movie | Current user's positive recommendation, ordered by rank when challenge evidence exists. |
| Users favorite movie | Community favorite movie ordered by average movie rating and voter count. |
| Rating similarity | Similarity from shared calculated movie ratings between the current user and another user. |
| Direct vote agreement | Similarity from same winner choices on shared direct Movie Challenge pairs. |
| Weighted movie rating | Other users' calculated movie ratings averaged with hybrid similarity weights. |
| Users recommended movie | Movie not yet recommended or disliked by the current user, ordered by weighted movie rating from similar users. |
| Poster fallback | Local placeholder image used when the poster URL is empty or `N/A`.             |
| Catalog administration | Admin-only ability to view Admin Menu and Registered Users.                     |
