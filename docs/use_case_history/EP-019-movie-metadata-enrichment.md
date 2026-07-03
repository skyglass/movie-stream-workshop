# Epic EP-019: Movie Metadata Enrichment

**Use Case ID:** movie-metadata-enrichment  
**Use Case Name:** Movie Metadata Enrichment  
**Primary Actor:** Authenticated User  
**Goal:** Preserve writer, genre, and country metadata supplied by movie catalog and recommendation workflows.  
**Status:** Implemented

## Short Description

The movie catalog now stores writer, genre, and country in addition to title, director, release year, and poster.
Catalog contribution persists this metadata when supplied by the caller. The recommendation workflow also accepts full
movie metadata so a user can recommend a movie that is not yet in the catalog; the system creates that movie first and
then stores the user's recommendation.

Writer is part of the persisted movie metadata. Genre and country are optional because existing catalog movies may not
have those values.

## Acceptance Criteria

```gherkin
Feature: add-movie-to-catalog

  Scenario: Authenticated user adds a movie with extended metadata
    Given the movie catalog is empty
    When contributor "user" adds movie "tt0133093" titled "The Matrix" with director "Lana Wachowski, Lilly Wachowski", writer "Lilly Wachowski, Lana Wachowski", year "1999", genre "Action, Sci-Fi", and country "United States, Australia"
    Then movie "tt0133093" exists in the catalog with title "The Matrix"
    And movie "tt0133093" exists in the catalog with director "Lana Wachowski, Lilly Wachowski", writer "Lilly Wachowski, Lana Wachowski", year "1999", genre "Action, Sci-Fi", and country "United States, Australia"

Feature: recommend-movie

  Scenario: Regular user recommends a movie that is not yet in the catalog
    Given the movie catalog is empty
    When regular user "user" recommends new movie "tt0098936" titled "Twin Peaks" with director "Mark Frost, David Lynch", writer "Mark Frost, David Lynch", year "1990-1991", genre "Crime, Drama, Mystery", and country "United States"
    Then movie "tt0098936" is recommended by "user"
    And movie "tt0098936" exists in the catalog with title "Twin Peaks"
    And movie "tt0098936" exists in the catalog with director "Mark Frost, David Lynch", writer "Mark Frost, David Lynch", year "1990-1991", genre "Crime, Drama, Mystery", and country "United States"
    And the recommendation response marks movie "tt0098936" as recommended

Feature: view-movie-details

  Scenario: Movie details include extended metadata
    Given movie "tt0133093" exists with title "The Matrix", director "Lana Wachowski, Lilly Wachowski", writer "Lilly Wachowski, Lana Wachowski", year "1999", genre "Action, Sci-Fi", and country "United States, Australia"
    When the viewer opens details for movie "tt0133093"
    Then the viewed movie details show director "Lana Wachowski, Lilly Wachowski", writer "Lilly Wachowski, Lana Wachowski", year "1999", genre "Action, Sci-Fi", and country "United States, Australia"
```
