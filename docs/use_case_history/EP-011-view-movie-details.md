# Epic EP-011: View Movie Details Recommended State

**Use Case ID:** view-movie-details  
**Use Case Name:** View Movie Details  
**Primary Actor:** Authenticated User  
**Goal:** Show whether the viewed movie is already recommended by the current user.  
**Status:** Implemented

## Short Description

This epic updates the existing `view-movie-details` use case with personalized recommendation state. The developer task is
to merge the scenario below into the current `view-movie-details` feature.

## Acceptance Criteria

```gherkin
Feature: view-movie-details

  Scenario: Movie details mark movies recommended by the current user
    Given movie "tt0083658" exists with title "Blade Runner"
    And movie "tt0083658" is already recommended by "user"
    When regular user "user" opens details for movie "tt0083658"
    Then the viewed movie is marked recommended
```
