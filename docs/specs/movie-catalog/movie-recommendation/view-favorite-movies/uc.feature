Feature: view-favorite-movies

  Scenario: Favorite movies are ordered by direct challenge rank
    Given movie "tt101" exists with title "Movie One"
    And movie "tt102" exists with title "Movie Two"
    And movie "tt103" exists with title "Movie Three"
    And user "user" has already ranked movies "tt102,tt101,tt103" from best to worst
    When regular user "user" requests favorite movies
    Then favorite movies show "tt102" before "tt101"
    And favorite movies show "tt101" before "tt103"
    And favorite movies total count is 3

  Scenario: User without movie challenge votes has no favorite movies yet
    Given movie "tt101" exists with title "Movie One"
    When regular user "user" requests favorite movies
    Then favorite movies contain 0 movies

  Scenario: Recommended movies without challenge votes are not favorite movies yet
    Given movie "tt101" exists with title "Movie One"
    And movie "tt102" exists with title "Movie Two"
    And movie "tt101" is already recommended by "user"
    And movie "tt102" is already recommended by "user"
    When regular user "user" requests favorite movies
    Then favorite movies contain 0 movies
    And favorite movies total count is 0

  Scenario: Favorite movies pagination preserves winner ranking on later pages
    Given movie "tt101" exists with title "Movie One"
    And movie "tt102" exists with title "Movie Two"
    And movie "tt103" exists with title "Movie Three"
    And user "user" has already ranked movies "tt101,tt102,tt103" from best to worst
    When regular user "user" requests page 2 of favorite movies with 2 movies per page
    Then favorite movies contain 1 movies
    And favorite movies total count is 3
    And favorite movies contain movie "tt103"
    And movie "tt103" has rank 3 and rating "1.00" for "user"

  Scenario: Favorite movies can be filtered by movie metadata
    Given movie "tt101" exists with title "Matrix Signal", director "Lana Wachowski", writer "Lilly Wachowski", year "1999", genre "Action", country "US", and type "movie"
    And movie "tt102" exists with title "Movie Two", director "Other Director", writer "Other Writer", year "2000", genre "Drama", country "US", and type "movie"
    And user "user" has already ranked movies "tt101,tt102" from best to worst
    When regular user "user" requests favorite movies filtered by "matrix"
    Then favorite movies contain 1 movies
    And favorite movies total count is 1
    And favorite movies contain movie "tt101"
    And favorite movies do not contain movie "tt102"
