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

  Scenario: A first movie above the direct-comparison threshold is still offered while it is behind the comparison balance
    Given movie "tt101" exists with title "Movie One"
    And movie "tt102" exists with title "Movie Two"
    And movie "tt103" exists with title "Movie Three"
    And movie "tt101" is already recommended by "user"
    And movie "tt102" is already recommended by "user"
    And movie "tt103" is already recommended by "user"
    And movie "tt101" has rank 5 and 11 direct comparisons for "user"
    And movie "tt102" has rank 4 and 16 direct comparisons for "user"
    And movie "tt103" has rank 7 and 17 direct comparisons for "user"
    When regular user "user" requests the next movie challenge
    Then the movie challenge is movie "tt101" against movie "tt102"

  Scenario: Balanced movies above the direct-comparison threshold are not offered
    Given movie "tt101" exists with title "Movie One"
    And movie "tt102" exists with title "Movie Two"
    And movie "tt103" exists with title "Movie Three"
    And movie "tt101" is already recommended by "user"
    And movie "tt102" is already recommended by "user"
    And movie "tt103" is already recommended by "user"
    And movie "tt101" has rank 1 and 11 direct comparisons for "user"
    And movie "tt102" has rank 2 and 12 direct comparisons for "user"
    And movie "tt103" has rank 3 and 14 direct comparisons for "user"
    When regular user "user" requests the next movie challenge
    Then no movie challenge is available

  Scenario: Second movie priority follows the comparison step before rank distance
    Given movie "tt101" exists with title "Movie One"
    And movie "tt102" exists with title "Movie Two"
    And movie "tt103" exists with title "Movie Three"
    And movie "tt104" exists with title "Movie Four"
    And movie "tt101" is already recommended by "user"
    And movie "tt102" is already recommended by "user"
    And movie "tt103" is already recommended by "user"
    And movie "tt104" is already recommended by "user"
    And movie "tt101" has rank 10 and 4 direct comparisons for "user"
    And movie "tt102" has rank 8 and 6 direct comparisons for "user"
    And movie "tt103" has rank 7 and 5 direct comparisons for "user"
    And movie "tt104" has rank 20 and 24 direct comparisons for "user"
    When regular user "user" requests the next movie challenge
    Then the movie challenge is movie "tt101" against movie "tt102"

  Scenario: Rank step breaks ties between second movies with the same comparison distance
    Given movie "tt101" exists with title "Movie One"
    And movie "tt102" exists with title "Movie Two"
    And movie "tt103" exists with title "Movie Three"
    And movie "tt120" exists with title "Movie Twenty"
    And movie "tt101" is already recommended by "user"
    And movie "tt102" is already recommended by "user"
    And movie "tt103" is already recommended by "user"
    And movie "tt101" has rank 10 and 2 direct comparisons for "user"
    And movie "tt102" has rank 8 and 3 direct comparisons for "user"
    And movie "tt103" has rank 12 and 3 direct comparisons for "user"
    And movie "tt120" has rank 20 and 3 direct comparisons for "user"
    When regular user "user" requests the next movie challenge
    Then the movie challenge is movie "tt101" against movie "tt102"

  Scenario: A new first movie uses the middle rank when choosing a second movie
    Given movie "tt101" exists with title "Movie One"
    And movie "tt102" exists with title "Movie Two"
    And movie "tt103" exists with title "Movie Three"
    And movie "tt120" exists with title "Movie Twenty"
    And movie "tt101" is already recommended by "user"
    And movie "tt102" is already recommended by "user"
    And movie "tt103" is already recommended by "user"
    And movie "tt102" has rank 8 and 1 direct comparison for "user"
    And movie "tt103" has rank 12 and 1 direct comparison for "user"
    And movie "tt120" has rank 20 and 1 direct comparison for "user"
    When regular user "user" requests the next movie challenge
    Then the movie challenge is movie "tt101" against movie "tt102"

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

  Scenario: Movie challenge stores selected movie as winner regardless of request order
    Given movie "tt101" exists with title "Movie One"
    And movie "tt102" exists with title "Movie Two"
    And movie "tt101" is already recommended by "user"
    And movie "tt102" is already recommended by "user"
    When regular user "user" selects movie "tt102" from movie challenge pair "tt102" and "tt101"
    Then movie "tt102" is recorded as winner over "tt101" for "user"
    And movie "tt102" is ranked over "tt101" for "user"
