# Epic EP-025: Suggested Movie Challenges

**Use Case ID:** movie-challenge  
**Use Case Name:** Movie Challenge  
**Primary Actor:** Authenticated User  
**Goal:** Let users review and submit multiple high-value Movie Challenge comparisons from the Search Wizard page.  
**Status:** Implemented

## Short Description

The Search Wizard page shows a paginated Suggested Challenges list below movie search. By default, exploration pairs are
shown first while any exploration pair exists; refinement pairs are shown only after exploration is exhausted. Refinement
suggestions are filtered to avoid pairs where the predicted winner would display at 70% or higher, then ordered by a
bucketed pair-confidence signal followed by higher-ranked movies inside the same bucket.

Each challenge contains two compact movie cards separated by a `VS` marker. Each card shows poster, title, year,
director, `Rank`, and `P`, the Bradley-Terry win chance against the paired movie. `Rank` displays the current rank
with rank confidence in parentheses, for example `#1 (60%)`. Rank confidence is a per-movie percentage from `10%` to
`100%`, calculated from direct-comparison coverage and the Bradley-Terry `sigma` band.
Under-explored movies may appear below `60%`; ranked refinement movies are distributed across `60%` to `100%`.
The ranked band starts near the Bradley-Terry prior sigma and approaches `100%` when sigma reaches the stable
score-error band used by challenge refinement.
The value is floored into 10% buckets so confidence works as a stable grouping signal across users and over time.
Clicking the info icon next to `Rank` shows
`Movie Rank and Rank Confidence, based on previous comparisons`.

Default suggested challenge ordering uses a pair-confidence bucket calculated from both movie confidence values and
floored to the nearest 10%. This keeps uncertain pairs ahead while leaving rank as the next priority inside each bucket.

`P` is calculated from the current `user_movie_rank.mu` values and displays only the win chance percentage. Unranked
pairs are shown as `50%`.

Users may select winners for multiple suggested challenges and submit them in one transactional batch. Batch submit
uses the same validation and winner-loser recording behavior as the single Movie Challenge vote, then rebuilds
`user_movie_rank` once after all selected votes are inserted. Discard clears local selections and reloads the current
suggested challenge page without submitting votes.

## Acceptance Criteria

```gherkin
Feature: movie-challenge

  Scenario: Suggested challenges are paginated with win chances
    Given movie "tt101" exists with title "Movie One"
    And movie "tt102" exists with title "Movie Two"
    And movie "tt103" exists with title "Movie Three"
    And movie "tt104" exists with title "Movie Four"
    And movie "tt101" is already recommended by "user"
    And movie "tt102" is already recommended by "user"
    And movie "tt103" is already recommended by "user"
    And movie "tt104" is already recommended by "user"
    When regular user "user" requests suggested movie challenges page 1 with size 2
    Then the suggested movie challenge total count is 3
    And the suggested movie challenge list contains 2 challenges
    And suggested movie challenge 1 is movie "tt101" against movie "tt102"

  Scenario: Regular user submits suggested challenge selections in a batch
    Given movie "tt101" exists with title "Movie One"
    And movie "tt102" exists with title "Movie Two"
    And movie "tt103" exists with title "Movie Three"
    And movie "tt101" is already recommended by "user"
    And movie "tt102" is already recommended by "user"
    And movie "tt103" is already recommended by "user"
    When regular user "user" submits movie challenge selections
      | movie1Id | movie2Id | selectedMovieId |
      | tt101    | tt102    | tt101           |
      | tt103    | tt102    | tt103           |
    Then movie "tt101" is recorded as winner over "tt102" for "user"
    And movie "tt103" is recorded as winner over "tt102" for "user"
    And movie "tt102" has 2 direct comparisons for "user"
```
