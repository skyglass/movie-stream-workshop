Feature: movie-challenge

  Scenario: Next challenge starts with the recommended movie that has the fewest direct comparisons
    Given movie "tt101" exists with title "Movie One"
    And movie "tt102" exists with title "Movie Two"
    And movie "tt103" exists with title "Movie Three"
    And movie "tt104" exists with title "Movie Four"
    And movie "tt101" is already recommended by "user"
    And movie "tt102" is already recommended by "user"
    And movie "tt103" is already recommended by "user"
    And movie "tt104" is already recommended by "user"
    And movie "tt101" has rank 1 and 6 direct comparisons for "user"
    And movie "tt102" has rank 2 and 1 direct comparison for "user"
    And movie "tt103" has rank 3 and 4 direct comparisons for "user"
    And movie "tt104" has rank 4 and 7 direct comparisons for "user"
    When regular user "user" requests the next movie challenge
    Then the movie challenge is movie "tt102" against movie "tt103"

  Scenario: Directly completed movie pairs are not offered again
    Given movie "tt101" exists with title "Movie One"
    And movie "tt102" exists with title "Movie Two"
    And movie "tt101" is already recommended by "user"
    And movie "tt102" is already recommended by "user"
    And movie pair "tt101" and "tt102" is already completed for "user"
    When regular user "user" requests the next movie challenge
    Then no movie challenge is available

  Scenario: The selector advances when the lowest-comparison movie has no available pair
    Given movie "tt101" exists with title "Movie One"
    And movie "tt102" exists with title "Movie Two"
    And movie "tt103" exists with title "Movie Three"
    And movie "tt101" is already recommended by "user"
    And movie "tt102" is already recommended by "user"
    And movie "tt103" is already recommended by "user"
    And movie pair "tt101" and "tt102" is already completed for "user"
    And movie pair "tt101" and "tt103" is already completed for "user"
    And movie "tt101" has rank 1 and 1 direct comparison for "user"
    And movie "tt102" has rank 2 and 2 direct comparisons for "user"
    And movie "tt103" has rank 3 and 3 direct comparisons for "user"
    When regular user "user" requests the next movie challenge
    Then the movie challenge is movie "tt102" against movie "tt103"

  Scenario: Exploration continues while a movie has fewer than three direct comparisons
    Given movie "tt101" exists with title "Movie One"
    And movie "tt102" exists with title "Movie Two"
    And movie "tt103" exists with title "Movie Three"
    And movie "tt101" is already recommended by "user"
    And movie "tt102" is already recommended by "user"
    And movie "tt103" is already recommended by "user"
    And movie "tt101" has rank 1 and 3 direct comparisons for "user"
    And movie "tt102" has rank 10 and 3 direct comparisons for "user"
    And movie "tt103" has rank 5 and 2 direct comparisons for "user"
    When regular user "user" requests the next movie challenge
    Then the movie challenge is movie "tt103" against movie "tt101"

  Scenario: Suggested challenges are paginated with win chances
    Given movie "tt101" exists with title "Movie One"
    And movie "tt102" exists with title "Movie Two"
    And movie "tt103" exists with title "Movie Three"
    And movie "tt104" exists with title "Movie Four"
    And movie "tt101" is already recommended by "user"
    And movie "tt102" is already recommended by "user"
    And movie "tt103" is already recommended by "user"
    And movie "tt104" is already recommended by "user"
    When regular user "user" requests suggested movie challenges page 1 with size 2
    Then the suggested movie challenge total count is 3
    And the suggested movie challenge list contains 2 challenges
    And suggested movie challenge 1 is movie "tt101" against movie "tt102"

  Scenario: Suggested exploration challenges stay ahead of top-ranked refinement
    Given movie "tt101" exists with title "Movie One"
    And movie "tt102" exists with title "Movie Two"
    And movie "tt103" exists with title "Movie Three"
    And movie "tt104" exists with title "Movie Four"
    And movie "tt101" is already recommended by "user"
    And movie "tt102" is already recommended by "user"
    And movie "tt103" is already recommended by "user"
    And movie "tt104" is already recommended by "user"
    And movie "tt101" has rank 1, 3 direct comparisons, mu "0.1", and sigma "0.5" for "user"
    And movie "tt102" has rank 2, 3 direct comparisons, mu "0.0", and sigma "0.5" for "user"
    And movie "tt103" has rank 50 and 1 direct comparison for "user"
    And movie "tt104" has rank 51 and 1 direct comparison for "user"
    When regular user "user" requests suggested movie challenges page 1 with size 2
    Then the suggested movie challenge total count is 1
    And the suggested movie challenge list contains 1 challenge
    And suggested movie challenge 1 is movie "tt103" against movie "tt104"

  Scenario: Suggested challenges can prefer higher-ranked movies on request
    Given movie "tt101" exists with title "Movie One"
    And movie "tt102" exists with title "Movie Two"
    And movie "tt103" exists with title "Movie Three"
    And movie "tt104" exists with title "Movie Four"
    And movie "tt101" is already recommended by "user"
    And movie "tt102" is already recommended by "user"
    And movie "tt103" is already recommended by "user"
    And movie "tt104" is already recommended by "user"
    And movie "tt101" has rank 1, 3 direct comparisons, mu "0.1", and sigma "0.5" for "user"
    And movie "tt102" has rank 2, 3 direct comparisons, mu "0.0", and sigma "0.5" for "user"
    And movie "tt103" has rank 50 and 1 direct comparison for "user"
    And movie "tt104" has rank 51 and 1 direct comparison for "user"
    When regular user "user" requests suggested movie challenges page 1 with size 2 higher ranked first
    Then the suggested movie challenge total count is 1
    And the suggested movie challenge list contains 1 challenge
    And suggested movie challenge 1 is movie "tt101" against movie "tt102"

  Scenario: Suggested refinement challenges prefer uncertainty bucket before rank by default
    Given movie "tt101" exists with title "Movie One"
    And movie "tt102" exists with title "Movie Two"
    And movie "tt103" exists with title "Movie Three"
    And movie "tt104" exists with title "Movie Four"
    And movie "tt101" is already recommended by "user"
    And movie "tt102" is already recommended by "user"
    And movie "tt103" is already recommended by "user"
    And movie "tt104" is already recommended by "user"
    And movie pair "tt101" and "tt103" is already completed for "user"
    And movie pair "tt101" and "tt104" is already completed for "user"
    And movie "tt101" has rank 1, 3 direct comparisons, mu "0.1", and sigma "0.5" for "user"
    And movie "tt102" has rank 5, 3 direct comparisons, mu "0.0", and sigma "0.5" for "user"
    And movie "tt103" has rank 2, 3 direct comparisons, mu "0.0", and sigma "0.9" for "user"
    And movie "tt104" has rank 3, 3 direct comparisons, mu "0.0", and sigma "0.9" for "user"
    When regular user "user" requests suggested movie challenges page 1 with size 2
    Then the suggested movie challenge total count is 3
    And the suggested movie challenge list contains 2 challenges
    And suggested movie challenge 1 is movie "tt103" against movie "tt104"
    And suggested movie challenge 1 movie "tt103" has confidence 10 percent
    And suggested movie challenge 1 movie "tt104" has confidence 10 percent

  Scenario: Suggested refinement challenges prefer top-ranked movies within the same uncertainty bucket
    Given movie "tt101" exists with title "Movie One"
    And movie "tt102" exists with title "Movie Two"
    And movie "tt103" exists with title "Movie Three"
    And movie "tt104" exists with title "Movie Four"
    And movie "tt101" is already recommended by "user"
    And movie "tt102" is already recommended by "user"
    And movie "tt103" is already recommended by "user"
    And movie "tt104" is already recommended by "user"
    And movie pair "tt101" and "tt103" is already completed for "user"
    And movie pair "tt101" and "tt104" is already completed for "user"
    And movie "tt101" has rank 1, 3 direct comparisons, mu "0.1", and sigma "0.5" for "user"
    And movie "tt102" has rank 5, 3 direct comparisons, mu "0.0", and sigma "0.5" for "user"
    And movie "tt103" has rank 2, 3 direct comparisons, mu "0.0", and sigma "0.5" for "user"
    And movie "tt104" has rank 3, 3 direct comparisons, mu "0.0", and sigma "0.5" for "user"
    When regular user "user" requests suggested movie challenges page 1 with size 2
    Then the suggested movie challenge total count is 3
    And the suggested movie challenge list contains 2 challenges
    And suggested movie challenge 1 is movie "tt101" against movie "tt102"

  Scenario: Bradley-Terry refinement offers a close uncertain pair after exploration
    Given movie "tt101" exists with title "Movie One"
    And movie "tt102" exists with title "Movie Two"
    And movie "tt103" exists with title "Movie Three"
    And movie "tt101" is already recommended by "user"
    And movie "tt102" is already recommended by "user"
    And movie "tt103" is already recommended by "user"
    And movie "tt101" has rank 1 and 3 direct comparisons for "user"
    And movie "tt102" has rank 2 and 3 direct comparisons for "user"
    And movie "tt103" has rank 10 and 3 direct comparisons for "user"
    When regular user "user" requests the next movie challenge
    Then the movie challenge is movie "tt101" against movie "tt102"

  Scenario: Low-information pairs above the exploration floor are not offered
    Given movie "tt101" exists with title "Movie One"
    And movie "tt102" exists with title "Movie Two"
    And movie "tt103" exists with title "Movie Three"
    And movie "tt101" is already recommended by "user"
    And movie "tt102" is already recommended by "user"
    And movie "tt103" is already recommended by "user"
    And movie "tt101" has rank 1 and 60 direct comparisons for "user"
    And movie "tt102" has rank 2 and 60 direct comparisons for "user"
    And movie "tt103" has rank 3 and 60 direct comparisons for "user"
    When regular user "user" requests the next movie challenge
    Then no movie challenge is available
    When regular user "user" requests suggested movie challenges page 1 with size 3
    Then the suggested movie challenge total count is 0
    And the suggested movie challenge list contains 0 challenges

  Scenario: Distant pairs above the exploration floor are not offered
    Given movie "tt101" exists with title "Movie One"
    And movie "tt102" exists with title "Movie Two"
    And movie "tt101" is already recommended by "user"
    And movie "tt102" is already recommended by "user"
    And movie "tt101" has rank 1 and 3 direct comparisons for "user"
    And movie "tt102" has rank 10 and 3 direct comparisons for "user"
    When regular user "user" requests the next movie challenge
    Then no movie challenge is available
    When regular user "user" requests suggested movie challenges page 1 with size 1
    Then the suggested movie challenge total count is 0
    And the suggested movie challenge list contains 0 challenges

  Scenario: Disliked movies do not count as challenge candidates
    Given movie "tt101" exists with title "Movie One"
    And movie "tt102" exists with title "Movie Two"
    And movie "tt101" is already recommended by "user"
    And movie "tt102" is already disliked by "user"
    When regular user "user" requests the next movie challenge
    Then no movie challenge is available

  Scenario: Regular user selects a challenge winner
    Given movie "tt101" exists with title "Movie One"
    And movie "tt102" exists with title "Movie Two"
    And movie "tt101" is already recommended by "user"
    And movie "tt102" is already recommended by "user"
    When regular user "user" selects movie "tt101" from movie challenge pair "tt101" and "tt102"
    Then movie "tt101" has 1 direct win by "user"
    And movie "tt101" has 1 direct comparison for "user"
    And movie "tt102" has 1 direct comparison for "user"
    And movie "tt101" has rank 1 and rating "10.00" for "user"
    And movie "tt102" has rank 2 and rating "1.00" for "user"
    And movie "tt101" is recorded as winner over "tt102" for "user"
    And movie "tt101" is ranked over "tt102" for "user"

  Scenario: Regular user submits suggested challenge selections in a batch
    Given movie "tt101" exists with title "Movie One"
    And movie "tt102" exists with title "Movie Two"
    And movie "tt103" exists with title "Movie Three"
    And movie "tt101" is already recommended by "user"
    And movie "tt102" is already recommended by "user"
    And movie "tt103" is already recommended by "user"
    When regular user "user" submits movie challenge selections
      | movie1Id | movie2Id | selectedMovieId |
      | tt101    | tt102    | tt101           |
      | tt103    | tt102    | tt103           |
    Then movie "tt101" is recorded as winner over "tt102" for "user"
    And movie "tt103" is recorded as winner over "tt102" for "user"
    And movie "tt102" has 2 direct comparisons for "user"

  Scenario: Movie challenge stores selected movie as winner regardless of request order
    Given movie "tt101" exists with title "Movie One"
    And movie "tt102" exists with title "Movie Two"
    And movie "tt101" is already recommended by "user"
    And movie "tt102" is already recommended by "user"
    When regular user "user" selects movie "tt102" from movie challenge pair "tt102" and "tt101"
    Then movie "tt102" is recorded as winner over "tt101" for "user"
    And movie "tt102" is ranked over "tt101" for "user"
