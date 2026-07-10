# Epic EP-028: Replay Movie Challenge History

**Use Case ID:** recommend-movie  
**Use Case Name:** Recommend Movie  
**Primary Actor:** Authenticated User  
**Goal:** Let a user reset one movie's challenge history and make it available for future challenges again.  
**Status:** Implemented

## Short Description

Movie details include a `Replay` action. The action runs as one transaction: first it applies the same cleanup as
unrecommend, removing direct challenge votes involving the movie and rebuilding challenge counts and Bradley-Terry
ranks; then it recommends the same movie again.

Replay does not recreate or replay challenge votes inside the transaction. It only resets the movie's challenge
history so future Movie Challenge sessions can offer that movie again.

The UI uses the Material `replay` icon for this action.

## Acceptance Criteria

```gherkin
Feature: recommend-movie

  Scenario: Replay clears the movie challenge history and recommends the movie again
    Given movie "tt101" exists with title "Movie One"
    And movie "tt102" exists with title "Movie Two"
    And movie "tt103" exists with title "Movie Three"
    And movie "tt101" has already beaten movie "tt102" for "user"
    And movie "tt102" has already beaten movie "tt103" for "user"
    When regular user "user" replays movie "tt102" through the movie API
    Then the movie API response status is 200
    And movie "tt102" is recommended by "user"
    And the recommendation response marks movie "tt102" as recommended
    And movie "tt102" has no direct challenge votes for "user"
    And movie "tt102" has no rank and rating for "user"
    And movie "tt102" has no challenge count for "user"
    When regular user "user" requests the next movie challenge
    Then the movie challenge contains movies "tt101" and "tt102"
```
