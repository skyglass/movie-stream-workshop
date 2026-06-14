# Epic EP-014: View Users Favorite Movies

**Use Case ID:** view-users-favorite-movies  
**Use Case Name:** View Users Favorite Movies  
**Primary Actor:** Authenticated User  
**Goal:** Show community favorite movies ordered by total Movie Challenge votes across all users.  
**Status:** Implemented

## Short Description

This epic adds the `view-users-favorite-movies` use case to the `movie-catalog` capability under the
`movie-recommendation` activity. The view reads `movie_user_votes`, groups votes by `movie_id`, and orders movies by
`sum(vote_count)` descending. Distinct voter count and movie title are used only as deterministic tie-breakers.

## Acceptance Criteria

```gherkin
Feature: view-users-favorite-movies

  Scenario: Users favorite movies are ordered by total votes across all users
    Given movie "tt101" exists with title "Movie One"
    And movie "tt102" exists with title "Movie Two"
    And movie "tt103" exists with title "Movie Three"
    And movie "tt101" has already received 2 votes from "user"
    And movie "tt101" has already received 3 votes from "admin"
    And movie "tt102" has already received 6 votes from "user"
    And movie "tt103" has already received 1 vote from "admin"
    When regular user "user" requests users favorite movies
    Then users favorite movies show "tt102" before "tt101"
    And users favorite movies show "tt101" before "tt103"

  Scenario: Users favorite movies are empty before any movie challenge votes
    Given movie "tt101" exists with title "Movie One"
    When regular user "user" requests users favorite movies
    Then users favorite movies contain 0 movies
```
