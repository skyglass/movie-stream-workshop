# Epic EP-024: Hybrid Rating Users Recommended Movies

**Use Case ID:** view-users-recommended-movies  
**Use Case Name:** Users Recommended Movies  
**Primary Actor:** Authenticated User  
**Goal:** Recommend movies from similar users using calculated ratings as the movie value signal.  
**Status:** Implemented

## Short Description

Users Recommended Movies no longer treats matching direct vote count as the movie score. Candidate movie value comes
from `user_movie_rating.rating`, which is the calculated `0-10` rating derived from Movie Challenge rank.

The read model still uses direct Movie Challenge votes, but only as part of user similarity. Similarity is now hybrid:

1. `70%` from shared calculated movie ratings.
2. `30%` from same winner choices on shared direct challenge pairs.
3. Both signals are capped by confidence, so large vote volume cannot dominate by itself.

Recommended movie score is a normalized weighted average:

`recommended_score = sum(candidate_rating * similarity) / sum(similarity)`

The list is ordered by recommended score, then total similarity weight, then similar user count, title, and IMDb id.

## Acceptance Criteria

```gherkin
Feature: view-users-recommended-movies

  Scenario: Users recommended movies prefer calculated ratings from similar users
    Given movie "tt101" exists with title "Movie One"
    And movie "tt102" exists with title "Movie Two"
    And movie "tt201" exists with title "Pair One"
    And movie "tt202" exists with title "Pair Two"
    And user "user" has already ranked movies "tt201,tt202" from best to worst
    And user "alice" has already ranked movies "tt101,tt201,tt202" from best to worst
    And user "bob" has already ranked movies "tt201,tt102,tt202" from best to worst
    When regular user "user" requests users recommended movies
    Then users recommended movies show "tt101" before "tt102"
```
