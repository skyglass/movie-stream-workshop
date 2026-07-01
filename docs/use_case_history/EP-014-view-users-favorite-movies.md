# Epic EP-014: View Users Favorite Movies

**Use Case ID:** view-users-favorite-movies  
**Use Case Name:** View Users Favorite Movies  
**Primary Actor:** Authenticated User  
**Goal:** Show community favorite movies ordered by total transitive Movie Challenge wins across all users.  
**Status:** Implemented

## Short Description

This epic adds the `view-users-favorite-movies` use case to the `movie-catalog` capability under the
`movie-recommendation` activity. The view reads `user_movie_winner_loser_all`, groups rows by `winner_id`, and orders
movies by total transitive win count descending. Distinct voter count and movie title are used only as deterministic
tie-breakers.

## Acceptance Criteria

```gherkin
Feature: view-users-favorite-movies

  Scenario: Users favorite movies are ordered by total transitive wins across all users
    Given movie "tt101" exists with title "Movie One"
    And movie "tt102" exists with title "Movie Two"
    And movie "tt103" exists with title "Movie Three"
    And movie "tt101" has already won 2 challenge comparisons for "user"
    And movie "tt101" has already won 3 challenge comparisons for "admin"
    And movie "tt102" has already won 6 challenge comparisons for "user"
    And movie "tt103" has already won 1 challenge comparison for "admin"
    When regular user "user" requests users favorite movies
    Then users favorite movies show "tt102" before "tt101"
    And users favorite movies show "tt101" before "tt103"

  Scenario: Users favorite movies are empty before any movie challenge votes
    Given movie "tt101" exists with title "Movie One"
    When regular user "user" requests users favorite movies
    Then users favorite movies contain 0 movies
```
