# Epic EP-018: Dislike Users Recommended Movies

**Use Case ID:** dislike-users-recommended-movies  
**Use Case Name:** Dislike Users Recommended Movies  
**Primary Actor:** Authenticated User  
**Goal:** Let users hide irrelevant user-based recommendations without adding those movies to their positive recommendations.  
**Status:** Implemented

## Short Description

Users Recommended Movies can surface a movie that matches similar users but does not match the current user's taste. The
user can now mark that movie as disliked. The system stores the feedback in `movie_recommendations` with
`positive = false`, excludes that movie from future Users Recommended Movies results for the same user, and shows the
disliked state on the catalog page.

Positive recommendations continue to use the same table with `positive = true`. Removing a recommendation deletes either
positive or negative feedback for that user/movie pair.

## Acceptance Criteria

```gherkin
Feature: recommend-movie

  Scenario: Regular user can dislike a users recommended movie
    Given movie "tt0083658" exists with title "Blade Runner"
    When regular user "user" dislikes movie "tt0083658"
    Then movie "tt0083658" is disliked by "user"
    And the recommendation response marks movie "tt0083658" as disliked

  Scenario: Regular user can clear a disliked movie
    Given movie "tt0083658" exists with title "Blade Runner"
    And movie "tt0083658" is already disliked by "user"
    When regular user "user" unrecommends movie "tt0083658"
    Then movie "tt0083658" is not disliked by "user"
    And the recommendation response marks movie "tt0083658" as not disliked

Feature: view-users-recommended-movies

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

Feature: view-movie-catalog

  Scenario: Catalog marks movies disliked by the current user
    Given movie "tt0083658" exists with title "Blade Runner"
    And movie "tt0083658" is already disliked by "user"
    When regular user "user" requests the personalized movie catalog
    Then catalog movie "tt0083658" is marked disliked
```
