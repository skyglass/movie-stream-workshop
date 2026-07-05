# Epic EP-013: View Favorite Movies

**Use Case ID:** view-favorite-movies  
**Use Case Name:** View Favorite Movies  
**Primary Actor:** Authenticated User  
**Goal:** Show the user's favorite movies ordered from most favorite to less favorite based on transitive Movie Challenge wins.  
**Status:** Implemented; superseded by EP-023 for rank/rating-based favorites.

> Current behavior reads `user_movie_rating`. This earlier epic describes the original transitive-win implementation.

## Short Description

This epic adds the `view-favorite-movies` use case to the `movie-catalog` capability under the `movie-recommendation`
activity. Movie Challenge selections are persisted as winner-loser relationships. The favorites view counts
`user_movie_winner_loser_all` rows where the current user's movie is `winner_id` and sorts movies by that transitive win
count descending.

The feature gives users a personalized favorite-movie list without asking them to assign absolute 1-to-10 ratings.

## Acceptance Criteria

```gherkin
Feature: view-favorite-movies

  Scenario: Favorite movies are ordered by transitive user wins
    Given movie "tt101" exists with title "Movie One"
    And movie "tt102" exists with title "Movie Two"
    And movie "tt103" exists with title "Movie Three"
    And movie "tt101" has already won 2 challenge comparisons for "user"
    And movie "tt102" has already won 5 challenge comparisons for "user"
    And movie "tt103" has already won 1 challenge comparison for "user"
    When regular user "user" requests favorite movies
    Then favorite movies show "tt102" before "tt101"
    And favorite movies show "tt101" before "tt103"

  Scenario: User without movie challenge votes has no favorite movies yet
    Given movie "tt101" exists with title "Movie One"
    When regular user "user" requests favorite movies
    Then favorite movies contain 0 movies
```
