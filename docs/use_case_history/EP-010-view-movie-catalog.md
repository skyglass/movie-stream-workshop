# Epic EP-010: View Movie Catalog Recommended State

**Use Case ID:** view-movie-catalog  
**Use Case Name:** View Movie Catalog  
**Primary Actor:** Authenticated User  
**Goal:** Show whether each listed movie is already recommended by the current user.  
**Status:** Implemented

## Short Description

This epic updates the existing `view-movie-catalog` use case with personalized recommendation state. The developer task is
to merge the scenario below into the current `view-movie-catalog` feature.

## Acceptance Criteria

```gherkin
Feature: view-movie-catalog

  Scenario: Catalog marks movies recommended by the current user
    Given movie "tt0083658" exists with title "Blade Runner"
    And movie "tt0083658" is already recommended by "user"
    When regular user "user" requests the personalized movie catalog
    Then catalog movie "tt0083658" is marked recommended
```
