# Epic EP-026: Movie Rank History

**Use Case ID:** view-movie-details  
**Use Case Name:** View Movie Details  
**Primary Actor:** Authenticated User  
**Goal:** See which directly challenged movies ranked higher or lower than the movie being inspected.  
**Status:** Implemented

## Short Description

Movie details now include a `Rank History` section below comments. The section is split into `Higher Ranks` and
`Lower Ranks`: higher ranks are direct challenge winners where the current movie was the loser, and lower ranks are
direct challenge losers where the current movie was the winner.

Each rank-history item is shown as a compact movie card with poster, title, year, director, and the viewer's current
rank/rating rendered as `Your Rank: #2(9.86)`. Both lists are sorted by current rank ascending, so `#1` appears first.

## Acceptance Criteria

```gherkin
Feature: view-movie-details

  Scenario: Movie details show rank history from direct challenge votes
    Given movie "tt101" exists with title "Higher One", director "Director A", writer "N/A", year "1999", genre "Drama", country "United States", and type "Movie"
    And movie "tt102" exists with title "Current Movie", director "Director B", writer "N/A", year "2000", genre "Drama", country "United States", and type "Movie"
    And movie "tt103" exists with title "Higher Two", director "Director C", writer "N/A", year "2001", genre "Drama", country "United States", and type "Movie"
    And movie "tt104" exists with title "Lower One", director "Director D", writer "N/A", year "2002", genre "Drama", country "United States", and type "Movie"
    And movie "tt101" has already beaten movie "tt102" for "user"
    And movie "tt103" has already beaten movie "tt102" for "user"
    And movie "tt102" has already beaten movie "tt104" for "user"
    And user "user" has rank history display values
      | imdbId | rank | directComparisons |
      | tt101  | 1    | 3                 |
      | tt103  | 2    | 3                 |
      | tt102  | 3    | 3                 |
      | tt104  | 4    | 3                 |
    When regular user "user" opens rank history for movie "tt102"
    Then the higher rank history contains movies
      | imdbId | title      | year | director   | rank | rating |
      | tt101  | Higher One | 1999 | Director A | 1    | 10.00  |
      | tt103  | Higher Two | 2001 | Director C | 2    | 7.00   |
    And the lower rank history contains movies
      | imdbId | title     | year | director   | rank | rating |
      | tt104  | Lower One | 2002 | Director D | 4    | 1.00   |

  Scenario: Rank history requires an authenticated viewer
    Given movie "tt102" exists with title "Current Movie"
    When anonymous viewer requests rank history for movie "tt102"
    Then the movie API response status is 401 or 403
```
