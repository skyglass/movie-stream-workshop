# Epic EP-025: Suggested Movie Challenges

**Use Case ID:** movie-challenge  
**Use Case Name:** Movie Challenge  
**Primary Actor:** Authenticated User  
**Goal:** Let users review and submit multiple high-value Movie Challenge comparisons from the Search Wizard page.  
**Status:** Implemented

## Short Description

The Search Wizard page shows a paginated Suggested Challenges list below movie search. By default, exploration pairs are
shown first while any exploration pair exists; refinement pairs are shown only after exploration is exhausted. Refinement
suggestions are filtered to avoid pairs where the predicted winner would display at 70% or higher, then ordered by pair
information, pair rank distance, direct-comparison counts, and the two movie ranks.
Exploration continues until each participating movie has at least four direct comparisons.

Each challenge contains two compact movie cards separated by a `VS` marker. Each card shows poster, title, year,
director, `Rank`, and `P`, the Bradley-Terry win chance against the paired movie. `Rank` displays the current rank
with pair confidence in parentheses, for example `#1 (60%)`. Both movies receive the same integer pair-confidence
percentage. It is the inverse linear normalization of the pair's information across all eligible pairs: the maximum
pair information maps to `0%`, the minimum maps to `100%`, and intermediate results are rounded to the nearest integer.
If all eligible pairs have identical information, they receive `100%`; a pair without computable information receives
`0%`.
Clicking the info icon next to `Rank` shows
`Movie Rank and Rank Confidence, based on previous comparisons`.

For default refinement sorting, the existing pair-confidence scale is used: maximum pair information maps to `0%` and
minimum pair information maps to `100%`. Values at or below `50%` receive sorting priority `0`; values above `50%`
receive priority `1`. The priority sorts ascending so pairs nearer the maximum information come first, followed by Movie
1 rank ascending, rank distance ascending, the higher and lower direct-comparison counts ascending, and Movie 2 rank
ascending. Movie IDs provide deterministic tie-breakers.

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
