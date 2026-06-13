# Epic EP-003: Add Movie To Catalog

**Use Case ID:** add-movie-to-catalog  
**Use Case Name:** Add Movie To Catalog  
**Primary Actor:** Authenticated User  
**Goal:** Add a movie selected or entered through the movie wizard into the catalog.  
**Status:** Simulated historical epic, implemented

## Short Description

This epic introduced authenticated catalog contribution. The UI can search OMDb and submit the selected metadata; the
API persists the supplied `MOVIE` aggregate.

## Acceptance Criteria

```gherkin
Feature: add-movie-to-catalog

  Scenario: Authenticated user adds a movie
    Given the movie catalog is empty
    When contributor "user" adds movie "tt0120737" titled "The Lord of the Rings"
    Then movie "tt0120737" exists in the catalog with title "The Lord of the Rings"

  Scenario: Anonymous caller cannot add a movie through the API
    When an anonymous caller tries to add movie "tt0000001"
    Then the API response status is 401 or 403
```
