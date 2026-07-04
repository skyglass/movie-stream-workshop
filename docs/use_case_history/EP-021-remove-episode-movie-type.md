# Epic EP-021: Remove Episode Movie Type

**Use Case ID:** movie-type-classification  
**Use Case Name:** Movie Type Classification  
**Primary Actor:** Authenticated User  
**Goal:** Keep movie classification limited to movies and full series.  
**Status:** Implemented

## Short Description

The application supports only two movie types: Movie and Series. Episode is removed from the application type model,
from the UI selectors, and from the database constraint.

Create, update, and recommendation APIs reject Episode as an unsupported movie type. Existing rows with the old Episode
code are migrated back to Movie before the database constraint is narrowed.

## Acceptance Criteria

```gherkin
Feature: add-movie-to-catalog

  Scenario: Authenticated user cannot add an episode type movie
    Given the movie catalog is empty
    When contributor "user" tries to add movie "tt0583459" titled "The One Where Monica Gets a Roommate" through the movie API with type "Episode"
    Then the movie API response status is 400

Feature: administer-movie-catalog

  Scenario: Admin cannot update a movie to episode type
    Given movie "tt0083658" exists with title "Blade Runner"
    When admin user "admin" updates movie "tt0083658" through the movie API with title "Blade Runner Final Cut", director "Ridley Scott", writer "Hampton Fancher, David Webb Peoples", year "1982", genre "Sci-Fi, Thriller", country "United States", and type "Episode"
    Then the movie API response status is 400

Feature: recommend-movie

  Scenario: Regular user cannot recommend a new episode type movie
    Given the movie catalog is empty
    When regular user "user" tries to recommend new movie "tt0583459" titled "The One Where Monica Gets a Roommate" through the movie API with type "Episode"
    Then the movie API response status is 400
```
