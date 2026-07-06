# Epic EP-023: Confidence-Aware Movie Challenge Ranking

**Use Case ID:** movie-challenge  
**Use Case Name:** Movie Challenge  
**Primary Actor:** Authenticated User  
**Goal:** Keep Movie Challenge efficient without permanently skipping weakly inferred movie pairs.  
**Status:** Implemented

## Short Description

Movie Challenge no longer treats every transitive winner-loser relationship as a completed pair or as a vote. The
source of truth is now `user_movie_challenge_vote`, which stores only direct choices. `user_movie_rank` is rebuilt from
those direct votes and stores rank position, an internal positive regularized ranking score, direct comparison count, and
confidence. The
`user_movie_rating` view maps each user's rank positions to ratings from `10` for the best ranked movie to `0` for the
worst ranked movie.

The implementation does not use machine learning or a probabilistic ranking engine. It uses direct votes, SQL window
functions, a positive regularized ranking score, and confidence thresholds. Ratings are evenly distributed with a linear rank formula:
`rating = 10 - 10 * ((rank_position - 1) / (movie_count - 1))`, with a single ranked movie rated as `10`.

The next challenge selector uses the rank projection to reduce unnecessary work:

1. Directly completed pairs are never offered again.
2. Pairs with weak inferred ordering can still be offered.
3. A pair is skipped without direct voting only when both movies have enough direct comparisons, both have enough
   confidence, and their rank positions are far apart.
4. Among eligible pairs, the selector prefers the pair with the least direct evidence.

For performance, the selector no longer asks the database to build and sort the full recommendation-pair cross product.
It loads the user's positive challenge candidates and completed direct pairs through indexed queries, then applies the
same pair ordering and confidence-skip rules in memory.

`My Favorite Movies` lists the current user's positive recommendations. Challenge rank and rating sort and annotate
those movies when available, but an internal ranking score never hides a positively recommended movie.

This supersedes the closure-table scoring behavior from EP-016 and EP-022. The obsolete
`user_movie_winner_loser_all` closure table and restored `user_movie_winner_loser` table are dropped after direct votes
are copied into `user_movie_challenge_vote`; new challenge writes and ranking queries use direct votes and rank/rating
projections.

## Acceptance Criteria

```gherkin
Feature: movie-challenge

  Scenario: Regular user gets next challenge from recommended movies with least direct evidence
    Given movie "tt101" exists with title "Movie One"
    And movie "tt102" exists with title "Movie Two"
    And movie "tt103" exists with title "Movie Three"
    And movie "tt104" exists with title "Movie Four"
    And movie "tt101" is already recommended by "user"
    And movie "tt102" is already recommended by "user"
    And movie "tt103" is already recommended by "user"
    And movie "tt104" is already recommended by "user"
    And movie "tt101" has already beaten movie "tt102" for "user"
    When regular user "user" requests the next movie challenge
    Then the movie challenge contains movies "tt103" and "tt104"

  Scenario: Weak inferred ranking can still be offered as a challenge
    Given movie "tt101" exists with title "Movie One"
    And movie "tt102" exists with title "Movie Two"
    And movie "tt103" exists with title "Movie Three"
    And movie "tt101" is already recommended by "user"
    And movie "tt102" is already recommended by "user"
    And movie "tt103" is already recommended by "user"
    And movie "tt101" has already beaten movie "tt102" for "user"
    And movie "tt102" has already beaten movie "tt103" for "user"
    When regular user "user" requests the next movie challenge
    Then the movie challenge contains movies "tt101" and "tt103"

  Scenario: No movie challenge is available when the only remaining pair is transitively confident
    Given movie "tt101" exists with title "Movie One"
    And movie "tt102" exists with title "Movie Two"
    And movie "tt103" exists with title "Movie Three"
    And movie "tt104" exists with title "Movie Four"
    And movie "tt105" exists with title "Movie Five"
    And movie "tt106" exists with title "Movie Six"
    And movie "tt107" exists with title "Movie Seven"
    And movie "tt108" exists with title "Movie Eight"
    And movie "tt109" exists with title "Movie Nine"
    And movie "tt110" exists with title "Movie Ten"
    And movie "tt111" exists with title "Movie Eleven"
    And movie "tt112" exists with title "Movie Twelve"
    And movie "tt101" is already recommended by "user"
    And movie "tt102" is already recommended by "user"
    And movie "tt103" is already recommended by "user"
    And movie "tt104" is already recommended by "user"
    And movie "tt105" is already recommended by "user"
    And movie "tt106" is already recommended by "user"
    And movie "tt107" is already recommended by "user"
    And movie "tt108" is already recommended by "user"
    And movie "tt109" is already recommended by "user"
    And movie "tt110" is already recommended by "user"
    And movie "tt111" is already recommended by "user"
    And movie "tt112" is already recommended by "user"
    And user "user" has already ranked movies "tt101,tt102,tt103,tt104,tt105,tt106,tt107,tt108,tt109,tt110,tt111,tt112" from best to worst except pair "tt101" and "tt112"
    When regular user "user" requests the next movie challenge
    Then no movie challenge is available
```
