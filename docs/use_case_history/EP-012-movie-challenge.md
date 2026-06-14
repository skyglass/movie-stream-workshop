# Epic EP-012: Movie Challenge

**Use Case ID:** movie-challenge  
**Use Case Name:** Movie Challenge  
**Primary Actor:** Authenticated User  
**Goal:** Let a user compare two recommended movies, vote for one, and continue until no unplayed recommended pair remains.  
**Status:** Implemented

## Short Description

This epic adds the `movie-challenge` use case to the `movie-catalog` capability under the `movie-recommendation`
activity. A challenge offers one unplayed pair from the authenticated user's recommended movies. Movie ids in completed
pairs are stored alphabetically so `tt101` versus `tt102` and `tt102` versus `tt101` are treated as the same pair.

The challenge selector prefers movies with the lowest recorded challenge counts and returns no challenge when fewer than
two recommendations are available or every recommended pair has already been completed. Selecting a movie records the
completed pair, increments the selected movie's vote count, and increments challenge counts for both movies.

## Acceptance Criteria

```gherkin
Feature: movie-challenge

  Scenario: Regular user gets next challenge from recommended movies with least challenge counts
    Given movie "tt101" exists with title "Movie One"
    And movie "tt102" exists with title "Movie Two"
    And movie "tt103" exists with title "Movie Three"
    And movie "tt104" exists with title "Movie Four"
    And movie "tt101" is already recommended by "user"
    And movie "tt102" is already recommended by "user"
    And movie "tt103" is already recommended by "user"
    And movie "tt104" is already recommended by "user"
    And movie "tt101" has already participated 1 time in challenges for "user"
    And movie "tt102" has already participated 1 time in challenges for "user"
    When regular user "user" requests the next movie challenge
    Then the movie challenge contains movies "tt103" and "tt104"

  Scenario: Regular user selects a challenge winner
    Given movie "tt101" exists with title "Movie One"
    And movie "tt102" exists with title "Movie Two"
    And movie "tt101" is already recommended by "user"
    And movie "tt102" is already recommended by "user"
    When regular user "user" selects movie "tt101" from movie challenge pair "tt101" and "tt102"
    Then movie "tt101" has 1 vote by "user"
    And movie "tt101" has participated 1 time in challenges for "user"
    And movie "tt102" has participated 1 time in challenges for "user"
    And movie pair "tt101" and "tt102" is completed for "user"

  Scenario: Movie challenge stores completed pairs alphabetically
    Given movie "tt101" exists with title "Movie One"
    And movie "tt102" exists with title "Movie Two"
    And movie "tt101" is already recommended by "user"
    And movie "tt102" is already recommended by "user"
    When regular user "user" selects movie "tt102" from movie challenge pair "tt102" and "tt101"
    Then movie pair "tt101" and "tt102" is completed for "user"

  Scenario: Completed movie pairs are not offered again
    Given movie "tt101" exists with title "Movie One"
    And movie "tt102" exists with title "Movie Two"
    And movie "tt101" is already recommended by "user"
    And movie "tt102" is already recommended by "user"
    And movie pair "tt101" and "tt102" is already completed for "user"
    When regular user "user" requests the next movie challenge
    Then no movie challenge is available

  Scenario: Less than two recommended movies cannot start a movie challenge
    Given movie "tt101" exists with title "Movie One"
    And movie "tt101" is already recommended by "user"
    When regular user "user" requests the next movie challenge
    Then no movie challenge is available
```
