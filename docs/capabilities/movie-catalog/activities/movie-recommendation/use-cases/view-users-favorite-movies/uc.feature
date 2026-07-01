Feature: view-users-favorite-movies

  Scenario: Users favorite movies are ordered by total transitive wins across all users
    Given movie "tt101" exists with title "Movie One"
    And movie "tt102" exists with title "Movie Two"
    And movie "tt103" exists with title "Movie Three"
    And movie "tt101" has already won 2 challenge comparisons for "user"
    And movie "tt101" has already won 3 challenge comparisons for "admin"
    And movie "tt102" has already won 6 challenge comparisons for "user"
    And movie "tt103" has already won 1 challenge comparison for "admin"
    When regular user "user" requests users favorite movies
    Then users favorite movies show "tt102" before "tt101"
    And users favorite movies show "tt101" before "tt103"

  Scenario: Users favorite movies are empty before any movie challenge votes
    Given movie "tt101" exists with title "Movie One"
    When regular user "user" requests users favorite movies
    Then users favorite movies contain 0 movies
