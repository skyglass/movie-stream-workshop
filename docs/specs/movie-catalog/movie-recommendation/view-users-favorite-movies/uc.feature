Feature: view-users-favorite-movies

  Scenario: Users favorite movies are ordered by community challenge ratings
    Given movie "tt101" exists with title "Movie One"
    And movie "tt102" exists with title "Movie Two"
    And movie "tt103" exists with title "Movie Three"
    And user "user" has already ranked movies "tt102,tt101,tt103" from best to worst
    And user "admin" has already ranked movies "tt102,tt101,tt103" from best to worst
    When regular user "user" requests users favorite movies
    Then users favorite movies show "tt102" before "tt101"
    And users favorite movies show "tt101" before "tt103"

  Scenario: Users favorite movies are empty before any movie challenge votes
    Given movie "tt101" exists with title "Movie One"
    When regular user "user" requests users favorite movies
    Then users favorite movies contain 0 movies

  Scenario: Users favorite movies keep total count when requested page is empty
    Given movie "tt101" exists with title "Movie One"
    And movie "tt102" exists with title "Movie Two"
    And movie "tt103" exists with title "Movie Three"
    And user "user" has already ranked movies "tt101,tt102,tt103" from best to worst
    When regular user "user" requests page 3 of users favorite movies with 2 movies per page
    Then users favorite movies contain 0 movies
    And users favorite movies total count is 3
