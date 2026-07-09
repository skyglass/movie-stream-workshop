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
| Minimal direct comparisons | Movie Challenge first-movie threshold of `10` direct comparisons. A movie at or below this count remains eligible as the first movie. |
| Comparison balance | A first movie above the minimal direct-comparison threshold remains eligible only while it is more than `5` direct comparisons behind the user's maximum direct-comparison count. |
| Comparison step | Rounded integer step that creates the preferred second-movie comparison target at `first_movie.direct_comparisons + max_direct_comparisons / 10`, with a minimum step of `1`. Candidates inside that target are considered before candidates that overshoot it. |
| Initial comparison phase | Selector phase when the spread between maximum and minimum direct comparisons is less than `3`; second-movie selection prioritizes rank distance in this phase but deprioritizes candidates already at the maximum direct-comparison count. |
| Second-movie balance window | Once the first movie has reached `10` direct comparisons, the second movie must be below the current maximum direct-comparison count. If the first movie is within `10` direct comparisons of that maximum, the second movie must also be no more than `4` direct comparisons ahead of the first movie. |
| Challenge confidence | Calculated value from direct comparison count and stored with the user movie rank. |
| Movie rating | Rating from 1 to 10 derived from the user's current rank scores. |
| Your Rank | Current viewer's own rank for a movie; list cards show it in parentheses after `Your Rating`. |
| Your Rating | UI label for the current viewer's own 1-10 rating; list cards show `rating (#rank)` or `-`. |
| Favorite movie | Current user's positive recommendation, ordered by rank when challenge evidence exists. |
| Users favorite movie | Community favorite movie ordered by average movie rating and voter count. |
| Rating similarity | Similarity from shared calculated movie ratings between the current user and another user. |
| Direct vote agreement | Similarity from same winner choices on shared direct Movie Challenge pairs. |
| Weighted movie rating | Other users' calculated movie ratings averaged with hybrid similarity weights. |
| Users recommended movie | Movie not yet recommended or disliked by the current user, ordered by weighted movie rating from similar users. |
| Poster fallback | Local placeholder image used when the poster URL is empty or `N/A`.             |
| Catalog administration | Admin-only ability to view Admin Menu and Registered Users.                     |
