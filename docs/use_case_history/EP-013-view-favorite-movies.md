# Epic EP-013: View Favorite Movies

**Use Case ID:** view-favorite-movies  
**Use Case Name:** View Favorite Movies  
**Primary Actor:** Authenticated User  
**Goal:** Show the user's favorite movies ordered from most favorite to less favorite based on Movie Challenge votes.  
**Status:** Implemented

## Short Description

This epic adds the `view-favorite-movies` use case to the `movie-catalog` capability under the `movie-recommendation`
activity. Movie Challenge selections are persisted in `movie_user_votes.vote_count`; the favorites view reads those
votes for the current user and sorts movies by `vote_count` descending.

The feature gives users a personalized favorite-movie list without asking them to assign absolute 1-to-10 ratings.

## Acceptance Criteria

```gherkin
Feature: view-favorite-movies

  Scenario: Favorite movies are ordered by user votes
    Given movie "tt101" exists with title "Movie One"
    And movie "tt102" exists with title "Movie Two"
    And movie "tt103" exists with title "Movie Three"
    And movie "tt101" has already received 2 votes from "user"
    And movie "tt102" has already received 5 votes from "user"
    And movie "tt103" has already received 1 vote from "user"
    When regular user "user" requests favorite movies
    Then favorite movies show "tt102" before "tt101"
    And favorite movies show "tt101" before "tt103"

  Scenario: User without movie challenge votes has no favorite movies yet
    Given movie "tt101" exists with title "Movie One"
    When regular user "user" requests favorite movies
    Then favorite movies contain 0 movies
```
