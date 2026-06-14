Feature: view-users-favorite-movies

  Scenario: Users favorite movies are ordered by total votes across all users
    Given movie "tt101" exists with title "Movie One"
    And movie "tt102" exists with title "Movie Two"
    And movie "tt103" exists with title "Movie Three"
    And movie "tt101" has already received 2 votes from "user"
    And movie "tt101" has already received 3 votes from "admin"
    And movie "tt102" has already received 6 votes from "user"
    And movie "tt103" has already received 1 vote from "admin"
    When regular user "user" requests users favorite movies
    Then users favorite movies show "tt102" before "tt101"
    And users favorite movies show "tt101" before "tt103"

  Scenario: Users favorite movies are empty before any movie challenge votes
    Given movie "tt101" exists with title "Movie One"
    When regular user "user" requests users favorite movies
    Then users favorite movies contain 0 movies
