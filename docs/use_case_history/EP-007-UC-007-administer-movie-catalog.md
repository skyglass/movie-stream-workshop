# Epic EP-007: Administer Movie Catalog

**Use Case ID:** administer-movie-catalog  
**Use Case Name:** Administer Movie Catalog  
**Primary Actor:** Admin  
**Goal:** Maintain movie metadata and remove catalog entries when necessary.  
**Status:** Simulated historical epic, implemented

## Short Description

This epic established admin-only mutation endpoints for existing movies. Regular users can contribute new movies and
comments, but updates and deletes are reserved for `MOVIES_ADMIN`.

## Acceptance Criteria

```gherkin
Feature: administer-movie-catalog

  Scenario: Admin updates movie metadata
    Given movie "tt0083658" exists with title "Blade Runner"
    When admin updates movie "tt0083658" title to "Blade Runner Final Cut"
    Then movie "tt0083658" exists in the catalog with title "Blade Runner Final Cut"

  Scenario: Regular user cannot delete a movie through the API
    Given movie "tt0083658" exists with title "Blade Runner"
    When regular user "user" tries to delete movie "tt0083658"
    Then the API response status is 403
```
