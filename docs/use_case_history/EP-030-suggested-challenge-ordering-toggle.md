# Epic EP-030: Suggested Challenge Ordering Toggle

**Use Case ID:** movie-challenge  
**Use Case Name:** Movie Challenge  
**Primary Actor:** Authenticated User  
**Goal:** Let users choose whether Suggested Challenges prioritize rank uncertainty or higher-ranked favorite movies.  
**Status:** Implemented

## Short Description

The Search Wizard Suggested Challenges section provides a `Higher Ranked First` checkbox in the top-right controls.
The checkbox is unchecked by default. When unchecked, suggested refinement challenges are ordered by bucketed
Bradley-Terry uncertainty first, then higher-ranked movies and close rank distance. When checked, the request includes
`higherRankedFirst=true` and ranked refinement suggestions are ordered by higher-ranked movies first, then rank distance,
then uncertainty bucket.

The default unchecked mode still gives exploration challenges first, so newly recommended or under-compared movies are
not starved. The checked mode is an explicit user preference for more dramatic comparisons among favorite high-ranked
movies, and falls back to exploration suggestions when no ranked refinement candidates are available.

Suggested challenge movie cards display each movie's `Confidence` as a percentage on the same ten-bucket scale as the
uncertainty ordering signal, reversed so higher uncertainty produces lower confidence.

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
    And movie "tt101" has rank 1, 3 direct comparisons, mu "0.1", and sigma "0.5" for "user"
    And movie "tt102" has rank 2, 3 direct comparisons, mu "0.0", and sigma "0.5" for "user"
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
    And movie "tt101" has rank 1, 3 direct comparisons, mu "0.1", and sigma "0.5" for "user"
    And movie "tt102" has rank 2, 3 direct comparisons, mu "0.0", and sigma "0.5" for "user"
    And movie "tt103" has rank 50 and 1 direct comparison for "user"
    And movie "tt104" has rank 51 and 1 direct comparison for "user"
    When regular user "user" requests suggested movie challenges page 1 with size 2 higher ranked first
    Then the suggested movie challenge total count is 1
    And the suggested movie challenge list contains 1 challenge
    And suggested movie challenge 1 is movie "tt101" against movie "tt102"

  Scenario: Suggested refinement challenges prefer uncertainty bucket before rank by default
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
    And movie "tt101" has rank 1, 3 direct comparisons, mu "0.1", and sigma "0.5" for "user"
    And movie "tt102" has rank 5, 3 direct comparisons, mu "0.0", and sigma "0.5" for "user"
    And movie "tt103" has rank 2, 3 direct comparisons, mu "0.0", and sigma "0.9" for "user"
    And movie "tt104" has rank 3, 3 direct comparisons, mu "0.0", and sigma "0.9" for "user"
    When regular user "user" requests suggested movie challenges page 1 with size 2
    Then the suggested movie challenge total count is 3
    And the suggested movie challenge list contains 2 challenges
    And suggested movie challenge 1 is movie "tt103" against movie "tt104"
```
