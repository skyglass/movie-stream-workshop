# Epic EP-001: View Movie Catalog

**Use Case ID:** view-movie-catalog  
**Use Case Name:** View Movie Catalog  
**Primary Actor:** Authenticated User  
**Goal:** Show the user the available movie catalog in a stable title order.  
**Status:** Simulated historical epic, implemented

## Short Description

The first catalog capability made Movie Stream useful as a browsing application. It established the `MOVIE` aggregate
and a sorted catalog read model.

## Acceptance Criteria

```gherkin
Feature: view-movie-catalog

  Scenario: Catalog lists movies in title order
    Given the movie catalog contains "Blade Runner" and "Alien"
    When the viewer requests the movie catalog
    Then the catalog lists "Alien" before "Blade Runner"

  Scenario: Empty catalog is available
    Given the movie catalog is empty
    When the viewer requests the movie catalog
    Then the catalog contains 0 movies
```
