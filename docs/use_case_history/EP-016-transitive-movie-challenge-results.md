# Epic EP-016: Transitive Movie Challenge Results

**Use Case ID:** movie-challenge  
**Use Case Name:** Movie Challenge  
**Primary Actor:** Authenticated User  
**Goal:** Treat Movie Challenge winner-loser relationships as transitive so already-inferred pairs are not challenged again.  
**Status:** Implemented

## Short Description

Movie Challenge results are no longer modeled as sorted completed pairs plus a separate vote counter. A selected movie is
stored as the winner over the other movie in `user_movie_winner_loser_all`, together with the full transitive closure.

When a user chooses `movie1` over `movie2`, and later chooses `movie2` over `movie3`, the system infers `movie1` over
`movie3`. The `movie1` versus `movie3` pair is therefore considered already ranked and must not be offered as a future
challenge.

Each write updates direct and transitive closure rows atomically in the Movie Challenge transaction. Cycles are rejected
by checking whether the reverse winner-loser relationship already exists in the closure table. Favorite and
recommendation views calculate vote strength from the closure table by counting rows where a movie is the winner.

## Acceptance Criteria

```gherkin
Feature: movie-challenge

  Scenario: Transitive winner-loser relationships are not offered as challenges
    Given movie "tt101" exists with title "Movie One"
    And movie "tt102" exists with title "Movie Two"
    And movie "tt103" exists with title "Movie Three"
    And movie "tt101" is already recommended by "user"
    And movie "tt102" is already recommended by "user"
    And movie "tt103" is already recommended by "user"
    And movie "tt101" has already beaten movie "tt102" for "user"
    And movie "tt102" has already beaten movie "tt103" for "user"
    When regular user "user" requests the next movie challenge
    Then no movie challenge is available

  Scenario: Transitive loser is not suggested against the transitive winner
    Given movie "tt101" exists with title "Movie One"
    And movie "tt102" exists with title "Movie Two"
    And movie "tt103" exists with title "Movie Three"
    And movie "tt104" exists with title "Movie Four"
    And movie "tt101" is already recommended by "user"
    And movie "tt102" is already recommended by "user"
    And movie "tt103" is already recommended by "user"
    And movie "tt104" is already recommended by "user"
    And movie "tt101" has already beaten movie "tt102" for "user"
    And movie "tt102" has already beaten movie "tt103" for "user"
    When regular user "user" requests the next movie challenge
    Then the movie challenge does not contain movies "tt101" and "tt103"
    And the movie challenge contains movies "tt101" and "tt104"

  Scenario: Movie Challenge winner updates transitive winner-loser table
    Given movie "tt101" exists with title "Movie One"
    And movie "tt102" exists with title "Movie Two"
    And movie "tt101" is already recommended by "user"
    And movie "tt102" is already recommended by "user"
    When regular user "user" selects movie "tt101" from movie challenge pair "tt101" and "tt102"
    Then movie "tt101" is recorded as winner over "tt102" for "user"
    And movie "tt101" is transitively ranked over "tt102" for "user"
    And movie "tt101" has 1 transitive win by "user"
```
