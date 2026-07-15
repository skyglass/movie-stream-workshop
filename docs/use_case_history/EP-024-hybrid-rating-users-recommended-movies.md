# Epic EP-024: Pearson Rating Users Recommended Movies

**Use Case ID:** view-users-recommended-movies  
**Use Case Name:** Users Recommended Movies  
**Primary Actor:** Authenticated User  
**Goal:** Recommend movies from similar users using calculated ratings as the movie value signal.  
**Status:** Implemented

## Short Description

Users Recommended Movies no longer treats matching direct vote count as the movie score. Candidate movie value comes
from `user_movie_rating.rating`, which is the calculated `0-10` rating derived from Movie Challenge rank.

The read model derives user similarity only from the calculated ratings produced by Movie Challenge ranking:

1. Pearson correlation is calculated over at least two shared calculated movie ratings.
2. Correlation is smoothly confidence-weighted as `shared_count / (shared_count + 8)`.
3. Users with a non-positive confidence-weighted correlation do not contribute candidates.

Direct challenge choices already feed the Bradley-Terry rating projection and are not counted a second time as a
separate similarity component.

Recommended movie score uses a catalog-wide average rating as a prior with strength `1`:

`recommended_score = (catalog_average + sum(candidate_rating * similarity)) / (1 + sum(similarity))`

This keeps a weak sole contributor from passing through their full rating unchanged. The list is ordered by recommended
score, then total similarity weight, then similar user count, title, and IMDb id.

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
