# Epic EP-022: Restore Direct Movie Challenge Results

**Use Case ID:** movie-challenge  
**Use Case Name:** Movie Challenge  
**Primary Actor:** Authenticated User  
**Goal:** Preserve direct winner-loser challenge rows for auditability while keeping transitive closure for ranking.  
**Status:** Implemented; superseded by EP-023.

> Current behavior stores direct choices in `user_movie_challenge_vote` and uses `user_movie_rank` /
> `user_movie_rating` for ranking. This earlier epic describes the restored direct audit table used before EP-023.

## Short Description

Movie Challenge keeps the transitive `user_movie_winner_loser_all` table for ranking, duplicate-pair prevention, favorite
movies, and users recommended movies. This epic restores `user_movie_winner_loser` as a direct audit table so users and
operators can inspect which challenge choices directly justify inferred rankings.

Existing rows are backfilled from `user_movie_winner_loser_all` by keeping only closure rows that have no intermediate
movie between winner and loser for the same user. This restores the minimal direct graph that explains the current
closure. It cannot recover historical redundant direct votes if a direct pair was also inferable through another path
before the direct table was dropped.

New Movie Challenge votes write both tables in one transaction: one direct row in `user_movie_winner_loser`, followed by
the expanded transitive rows in `user_movie_winner_loser_all`.

## Acceptance Criteria

```gherkin
Feature: movie-challenge

  Scenario: Direct challenge results stay separate from inferred transitive rankings
    Given movie "tt101" exists with title "Movie One"
    And movie "tt102" exists with title "Movie Two"
    And movie "tt103" exists with title "Movie Three"
    And movie "tt101" is already recommended by "user"
    And movie "tt102" is already recommended by "user"
    And movie "tt103" is already recommended by "user"
    When regular user "user" selects movie "tt101" from movie challenge pair "tt101" and "tt102"
    And regular user "user" selects movie "tt102" from movie challenge pair "tt102" and "tt103"
    Then movie "tt101" is recorded as winner over "tt102" for "user"
    And movie "tt102" is recorded as winner over "tt103" for "user"
    And movie "tt101" is not recorded as direct winner over "tt103" for "user"
    And movie "tt101" is transitively ranked over "tt103" for "user"
```
