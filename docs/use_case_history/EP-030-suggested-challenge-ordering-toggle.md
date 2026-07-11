# Epic EP-030: Suggested Challenge Ordering Toggle

**Use Case ID:** movie-challenge  
**Use Case Name:** Movie Challenge  
**Primary Actor:** Authenticated User  
**Goal:** Let users choose whether Suggested Challenges prioritize rank uncertainty or higher-ranked favorite movies.  
**Status:** Implemented

## Short Description

The Search Wizard Suggested Challenges section provides a `Higher Ranked First` checkbox in the top-right controls.
The checkbox is unchecked by default. When unchecked, suggested refinement challenges are ordered by an ascending
binary pair-confidence priority (`0` at or below `50%`, `1` above `50%`), Movie 1 rank, close rank distance,
direct-comparison counts, and Movie 2 rank. Because maximum pair information maps to `0%` confidence, the most
informative half remains first. When checked, the request includes
`higherRankedFirst=true` and ranked refinement suggestions are ordered by the best rank in either movie ascending, rank distance
ascending, pair information descending, Movie 2 rank ascending, higher and lower direct-comparison counts ascending,
then Movie 1 and Movie 2 IDs ascending.

The default unchecked mode still gives exploration challenges first, so newly recommended or under-compared movies are
not starved. The checked mode selects refinement before exploration and prioritizes the best rank in the pair and rank distance,
then pair information and Movie 2 rank; it falls back to exploration when no ranked refinement candidates are available.

Suggested challenge movie cards display each movie's `Rank` with a shared pair-confidence percentage in parentheses.
Confidence inversely normalizes pair information: maximum information is `0%`, minimum information is `100%`, and
intermediate values are rounded to an integer.

## Acceptance Criteria

```gherkin
Feature: movie-challenge

  Scenario: Suggested exploration challenges stay ahead of top-ranked refinement
    Given movie "tt101" exists with title "Movie One"
    And movie "tt102" exists with title "Movie Two"
    And movie "tt103" exists with title "Movie Three"
    And movie "tt104" exists with title "Movie Four"
    And movie "tt101" is already recommended by "user"
    And movie "tt102" is already recommended by "user"
    And movie "tt103" is already recommended by "user"
    And movie "tt104" is already recommended by "user"
    And movie "tt101" has rank 1, 4 direct comparisons, mu "0.1", and sigma "0.5" for "user"
    And movie "tt102" has rank 2, 4 direct comparisons, mu "0.0", and sigma "0.5" for "user"
    And movie "tt103" has rank 50 and 1 direct comparison for "user"
    And movie "tt104" has rank 51 and 1 direct comparison for "user"
    When regular user "user" requests suggested movie challenges page 1 with size 2
    Then the suggested movie challenge total count is 1
    And the suggested movie challenge list contains 1 challenge
    And suggested movie challenge 1 is movie "tt103" against movie "tt104"

  Scenario: Suggested challenges can prefer higher-ranked movies on request
    Given movie "tt101" exists with title "Movie One"
    And movie "tt102" exists with title "Movie Two"
    And movie "tt103" exists with title "Movie Three"
    And movie "tt104" exists with title "Movie Four"
    And movie "tt101" is already recommended by "user"
    And movie "tt102" is already recommended by "user"
    And movie "tt103" is already recommended by "user"
    And movie "tt104" is already recommended by "user"
    And movie "tt101" has rank 1, 4 direct comparisons, mu "0.1", and sigma "0.5" for "user"
    And movie "tt102" has rank 2, 4 direct comparisons, mu "0.0", and sigma "0.5" for "user"
    And movie "tt103" has rank 50 and 1 direct comparison for "user"
    And movie "tt104" has rank 51 and 1 direct comparison for "user"
    When regular user "user" requests suggested movie challenges page 1 with size 2 higher ranked first
    Then the suggested movie challenge total count is 1
    And the suggested movie challenge list contains 1 challenge
    And suggested movie challenge 1 is movie "tt101" against movie "tt102"

  Scenario: Higher ranks first refinement uses the best rank in either movie
    Given equally informative unresolved refinement pairs
    When regular user "user" requests suggested movie challenges higher ranked first
    Then the pair containing the better-ranked movie is listed before a closer-rank pair

  Scenario: Suggested refinement challenges prefer pair information by default
    Given movie "tt101" exists with title "Movie One"
    And movie "tt102" exists with title "Movie Two"
    And movie "tt103" exists with title "Movie Three"
    And movie "tt104" exists with title "Movie Four"
    And movie "tt101" is already recommended by "user"
    And movie "tt102" is already recommended by "user"
    And movie "tt103" is already recommended by "user"
    And movie "tt104" is already recommended by "user"
    And movie pair "tt101" and "tt103" is already completed for "user"
    And movie pair "tt101" and "tt104" is already completed for "user"
    And movie "tt101" has rank 1, 9 direct comparisons, mu "0.1", and sigma "0.5" for "user"
    And movie "tt102" has rank 5, 9 direct comparisons, mu "0.0", and sigma "0.5" for "user"
    And movie "tt103" has rank 2, 4 direct comparisons, mu "0.0", and sigma "0.9" for "user"
    And movie "tt104" has rank 3, 4 direct comparisons, mu "0.0", and sigma "0.9" for "user"
    When regular user "user" requests suggested movie challenges page 1 with size 2
    Then the suggested movie challenge total count is 3
    And the suggested movie challenge list contains 2 challenges
    And suggested movie challenge 1 is movie "tt103" against movie "tt104"

  Scenario: Suggested refinement challenges use direct-comparison counts after rank distance
    Given movie "tt101" exists with title "Movie One"
    And movie "tt102" exists with title "Movie Two"
    And movie "tt103" exists with title "Movie Three"
    And movie "tt101" is already recommended by "user"
    And movie "tt102" is already recommended by "user"
    And movie "tt103" is already recommended by "user"
    And movie pair "tt101" and "tt102" is already completed for "user"
    And movie "tt101" has rank 1, 9 direct comparisons, mu "0.0", and sigma "0.5" for "user"
    And movie "tt102" has rank 2, 9 direct comparisons, mu "0.0", and sigma "0.5" for "user"
    And movie "tt103" has rank 3, 4 direct comparisons, mu "0.0", and sigma "0.9" for "user"
    When regular user "user" requests suggested movie challenges page 1 with size 2
    Then the suggested movie challenge total count is 2
    And the suggested movie challenge list contains 2 challenges
    And suggested movie challenge 1 is movie "tt103" against movie "tt102"
    And suggested movie challenge 2 is movie "tt103" against movie "tt101"
```
