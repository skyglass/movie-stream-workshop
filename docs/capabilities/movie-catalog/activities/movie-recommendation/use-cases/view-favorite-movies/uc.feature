Feature: view-favorite-movies

  Scenario: Favorite movies are ordered by transitive user wins
    Given movie "tt101" exists with title "Movie One"
    And movie "tt102" exists with title "Movie Two"
    And movie "tt103" exists with title "Movie Three"
    And movie "tt101" has already won 2 challenge comparisons for "user"
    And movie "tt102" has already won 5 challenge comparisons for "user"
    And movie "tt103" has already won 1 challenge comparison for "user"
    When regular user "user" requests favorite movies
    Then favorite movies show "tt102" before "tt101"
    And favorite movies show "tt101" before "tt103"

  Scenario: User without movie challenge votes has no favorite movies yet
    Given movie "tt101" exists with title "Movie One"
    When regular user "user" requests favorite movies
    Then favorite movies contain 0 movies
