# Epic EP-015: View Users Recommended Movies

**Use Case ID:** view-users-recommended-movies  
**Use Case Name:** View Users Recommended Movies  
**Primary Actor:** Authenticated User  
**Goal:** Show movies recommended by similar users, excluding movies the current user has already recommended or disliked.  
**Status:** Implemented; superseded by EP-024 for hybrid rating similarity and rating-weighted recommendations.

> Current behavior uses a hybrid similarity score from shared calculated ratings and direct vote agreement, then weights
> other users' `user_movie_rating` rows. This earlier epic describes the original transitive-win implementation.

## Short Description

This epic adds the `view-users-recommended-movies` use case to the `movie-catalog` capability under the
`movie-recommendation` activity. The list excludes every movie that already has a positive or negative
`movie_recommendations` row for the current user and ranks the remaining movies by weighted Movie Challenge votes from
other users.

The relative weight between the current user and another user is the count of matching transitive winner-loser rows in
`user_movie_winner_loser_all`. The current user is never compared with themself. Candidate movie scores multiply each
other user's transitive win count for that movie by that relative weight and divide by the total relative weight for the
current user. Movies with no positive weighted score are not shown.

## Acceptance Criteria

```gherkin
Feature: view-users-recommended-movies

  Scenario: Users recommended movies prefer votes from users with similar movie challenge choices
    Given movie "tt101" exists with title "Movie One"
    And movie "tt102" exists with title "Movie Two"
    And movie "tt201" exists with title "Pair One"
    And movie "tt202" exists with title "Pair Two"
    And movie "tt203" exists with title "Pair Three"
    And user "user" has already chosen "tt201" over "tt202" in movie challenges
    And user "user" has already chosen "tt202" over "tt203" in movie challenges
    And user "alice" has already chosen "tt201" over "tt202" in movie challenges
    And user "alice" has already chosen "tt202" over "tt203" in movie challenges
    And user "bob" has already chosen "tt201" over "tt202" in movie challenges
    And movie "tt101" has already won 2 challenge comparisons for "alice"
    And movie "tt102" has already won 5 challenge comparisons for "bob"
    When regular user "user" requests users recommended movies
    Then users recommended movies show "tt101" before "tt102"

  Scenario: Users recommended movies exclude movies already recommended by the current user
    Given movie "tt101" exists with title "Movie One"
    And movie "tt102" exists with title "Movie Two"
    And movie "tt201" exists with title "Pair One"
    And movie "tt202" exists with title "Pair Two"
    And user "user" has already chosen "tt201" over "tt202" in movie challenges
    And user "alice" has already chosen "tt201" over "tt202" in movie challenges
    And movie "tt101" has already won 10 challenge comparisons for "alice"
    And movie "tt102" has already won 1 challenge comparison for "alice"
    And movie "tt101" is already recommended by "user"
    When regular user "user" requests users recommended movies
    Then users recommended movies do not contain "tt101"
    And users recommended movies contain 1 movies

  Scenario: Users recommended movies exclude movies disliked by the current user
    Given movie "tt101" exists with title "Movie One"
    And movie "tt102" exists with title "Movie Two"
    And movie "tt201" exists with title "Pair One"
    And movie "tt202" exists with title "Pair Two"
    And user "user" has already chosen "tt201" over "tt202" in movie challenges
    And user "alice" has already chosen "tt201" over "tt202" in movie challenges
    And movie "tt101" has already won 10 challenge comparisons for "alice"
    And movie "tt102" has already won 1 challenge comparison for "alice"
    And movie "tt101" is already disliked by "user"
    When regular user "user" requests users recommended movies
    Then users recommended movies do not contain "tt101"
    And users recommended movies contain 1 movies

  Scenario: Users recommended movies are empty without matching movie challenge history
    Given movie "tt101" exists with title "Movie One"
    And movie "tt101" has already won 5 challenge comparisons for "admin"
    When regular user "user" requests users recommended movies
    Then users recommended movies contain 0 movies
```
