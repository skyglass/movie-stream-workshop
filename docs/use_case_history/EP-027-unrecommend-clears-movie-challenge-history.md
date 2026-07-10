# Epic EP-027: Unrecommend Clears Movie Challenge History

**Use Case ID:** recommend-movie  
**Use Case Name:** Recommend Movie  
**Primary Actor:** Authenticated User  
**Goal:** Remove an unrecommended movie from the user's challenge-derived ranks and rank history.  
**Status:** Implemented

## Short Description

When a user unrecommends a movie, the movie is no longer eligible for the user's favorite/rank views. Any direct
challenge votes involving that movie are deleted, challenge counts are rebuilt from the remaining direct votes, and
the Bradley-Terry rank projection is recalculated without the unrecommended movie.

Remaining direct challenge votes are preserved, so ranks and ratings for the rest of the user's challenged movies are
rebuilt from the surviving history.

## Acceptance Criteria

```gherkin
Feature: recommend-movie

  Scenario: Unrecommend removes the movie from challenge history and rebuilds ranks
    Given movie "tt101" exists with title "Movie One"
    And movie "tt102" exists with title "Movie Two"
    And movie "tt103" exists with title "Movie Three"
    And movie "tt101" has already beaten movie "tt102" for "user"
    And movie "tt101" has already beaten movie "tt103" for "user"
    And movie "tt102" has already beaten movie "tt103" for "user"
    When regular user "user" unrecommends movie "tt102"
    Then movie "tt102" is not recommended by "user"
    And movie "tt102" has no direct challenge votes for "user"
    And movie "tt102" has no rank and rating for "user"
    And movie "tt102" has no challenge count for "user"
    And movie "tt101" has rank 1 and rating "10.00" for "user"
    And movie "tt103" has rank 2 and rating "1.00" for "user"
    And movie "tt101" is recorded as winner over "tt103" for "user"
```
