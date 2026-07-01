# Epic EP-015: View Users Recommended Movies

**Use Case ID:** view-users-recommended-movies  
**Use Case Name:** View Users Recommended Movies  
**Primary Actor:** Authenticated User  
**Goal:** Show movies recommended by similar users, excluding movies the current user has already recommended.  
**Status:** Implemented

## Short Description

This epic adds the `view-users-recommended-movies` use case to the `movie-catalog` capability under the
`movie-recommendation` activity. The list excludes every movie that already has a `movie_recommendations` row for the
current user and ranks the remaining movies by weighted Movie Challenge votes from other users.

The relative weight between the current user and another user is the count of completed `user_movie_pair_challenge`
rows where both users have the same sorted movie pair and the same `movie1_wins` value. The current user is never
compared with themself. Candidate movie scores multiply each other user's movie vote count by that relative weight and
divide by the total relative weight for the current user. Movies with no positive weighted score are not shown.

## Acceptance Criteria

```gherkin
Feature: view-users-recommended-movies

  Scenario: Users recommended movies prefer votes from users with similar movie challenge choices
    Given movie "tt101" exists with title "Movie One"
    And movie "tt102" exists with title "Movie Two"
    And movie "tt201" exists with title "Pair One"
    And movie "tt202" exists with title "Pair Two"
    And movie "tt203" exists with title "Pair Three"
    And user "user" has completed movie pair "tt201" and "tt202" with movie1_wins true
    And user "user" has completed movie pair "tt201" and "tt203" with movie1_wins true
    And user "user" has completed movie pair "tt202" and "tt203" with movie1_wins false
    And user "alice" has completed movie pair "tt201" and "tt202" with movie1_wins true
    And user "alice" has completed movie pair "tt201" and "tt203" with movie1_wins true
    And user "alice" has completed movie pair "tt202" and "tt203" with movie1_wins false
    And user "bob" has completed movie pair "tt201" and "tt202" with movie1_wins true
    And movie "tt101" has already received 2 votes from "alice"
    And movie "tt102" has already received 5 votes from "bob"
    When regular user "user" requests users recommended movies
    Then users recommended movies show "tt101" before "tt102"

  Scenario: Users recommended movies exclude movies already recommended by the current user
    Given movie "tt101" exists with title "Movie One"
    And movie "tt102" exists with title "Movie Two"
    And movie "tt201" exists with title "Pair One"
    And movie "tt202" exists with title "Pair Two"
    And user "user" has completed movie pair "tt201" and "tt202" with movie1_wins true
    And user "alice" has completed movie pair "tt201" and "tt202" with movie1_wins true
    And movie "tt101" has already received 10 votes from "alice"
    And movie "tt102" has already received 1 vote from "alice"
    And movie "tt101" is already recommended by "user"
    When regular user "user" requests users recommended movies
    Then users recommended movies do not contain "tt101"
    And users recommended movies contain 1 movies

  Scenario: Users recommended movies are empty without matching movie challenge history
    Given movie "tt101" exists with title "Movie One"
    And movie "tt101" has already received 5 votes from "admin"
    When regular user "user" requests users recommended movies
    Then users recommended movies contain 0 movies
```
