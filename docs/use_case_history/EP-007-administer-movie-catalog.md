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

  Scenario: Admin edits all supported movie fields through the API
    Given movie "tt0083658" exists with title "Blade Runner"
    When admin user "admin" updates movie "tt0083658" through the movie API with title "Blade Runner Final Cut", director "Ridley Scott", writer "Hampton Fancher, David Webb Peoples", year "1982", genre "Sci-Fi, Thriller", country "United States", and type "Movie"
    Then the movie API response status is 200
    And movie "tt0083658" exists in the catalog with title "Blade Runner Final Cut"
    And movie "tt0083658" exists in the catalog with director "Ridley Scott", writer "Hampton Fancher, David Webb Peoples", year "1982", genre "Sci-Fi, Thriller", country "United States", and type "Movie"

  Scenario: Regular user cannot delete a movie through the API
    Given movie "tt0083658" exists with title "Blade Runner"
    When regular user "user" tries to delete movie "tt0083658"
    Then the movie API response status is 403

  Scenario: Regular user cannot update movie metadata through the API
    Given movie "tt0083658" exists with title "Blade Runner"
    When regular user "user" tries to update movie "tt0083658" title to "Blade Runner Final Cut"
    Then the movie API response status is 403
    And movie "tt0083658" exists in the catalog with title "Blade Runner"
```
