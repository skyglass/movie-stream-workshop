# Epic EP-002: View Movie Details

**Use Case ID:** view-movie-details  
**Use Case Name:** View Movie Details  
**Primary Actor:** Authenticated User  
**Goal:** Inspect movie metadata and discussion for one selected movie.  
**Status:** Simulated historical epic, implemented

## Short Description

Movie details extended the catalog from a list to a focused movie page and introduced comment ordering as part of the
movie aggregate read model.

## Acceptance Criteria

```gherkin
Feature: view-movie-details

  Scenario: Movie details include metadata
    Given movie "tt0133093" exists with title "The Matrix"
    When the viewer opens details for movie "tt0133093"
    Then the movie details show title "The Matrix"

  Scenario: Movie details include comments newest first
    Given movie "tt0133093" exists with title "The Matrix"
    And movie "tt0133093" has comment "First comment" by "user"
    And movie "tt0133093" has comment "Second comment" by "admin"
    When the viewer opens details for movie "tt0133093"
    Then the first movie comment is "Second comment"
```
