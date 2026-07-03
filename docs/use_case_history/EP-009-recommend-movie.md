# Epic EP-009: Recommend Movie

**Use Case ID:** recommend-movie  
**Use Case Name:** Recommend Movie  
**Primary Actor:** Authenticated User  
**Goal:** Let authenticated users mark movies as recommended and remove that recommendation later.  
**Status:** Implemented

## Short Description

This epic adds per-user movie recommendations. A recommendation is persisted by authenticated username and IMDb movie id
so catalog and detail views can show whether the current user has already recommended each movie.

The implemented use case stores recommendation feedback synchronously. Movie Challenge availability is read through the
movie-challenge API when the user opens the challenge flow.

## Acceptance Criteria

```gherkin
Feature: recommend-movie

  Scenario: Regular user can recommend a movie
    Given movie "tt0083658" exists with title "Blade Runner"
    When regular user "user" recommends movie "tt0083658"
    Then movie "tt0083658" is recommended by "user"
    And the recommendation response marks movie "tt0083658" as recommended

  Scenario: Regular user can unrecommend a movie
    Given movie "tt0083658" exists with title "Blade Runner"
    And movie "tt0083658" is already recommended by "user"
    When regular user "user" unrecommends movie "tt0083658"
    Then movie "tt0083658" is not recommended by "user"
    And the recommendation response marks movie "tt0083658" as not recommended

  Scenario: Anonymous caller cannot recommend a movie through the API
    Given movie "tt0083658" exists with title "Blade Runner"
    When an anonymous caller tries to recommend movie "tt0083658"
    Then the movie API response status is 401
```
