Feature: view-users-recommended-movies

  Scenario: Users recommended movies prefer votes from users with similar movie challenge choices
    Given movie "tt101" exists with title "Movie One"
    And movie "tt102" exists with title "Movie Two"
    And movie "tt201" exists with title "Pair One"
    And movie "tt202" exists with title "Pair Two"
    And movie "tt203" exists with title "Pair Three"
    And user "user" has already chosen "tt201" over "tt202" in movie challenges
    And user "user" has already chosen "tt202" over "tt203" in movie challenges
    And user "alice" has already chosen "tt201" over "tt202" in movie challenges
    And user "alice" has already chosen "tt202" over "tt203" in movie challenges
    And user "bob" has already chosen "tt201" over "tt202" in movie challenges
    And movie "tt101" has already won 2 challenge comparisons for "alice"
    And movie "tt102" has already won 5 challenge comparisons for "bob"
    When regular user "user" requests users recommended movies
    Then users recommended movies show "tt101" before "tt102"

  Scenario: Users recommended movies exclude movies already recommended by the current user
    Given movie "tt101" exists with title "Movie One"
    And movie "tt102" exists with title "Movie Two"
    And movie "tt201" exists with title "Pair One"
    And movie "tt202" exists with title "Pair Two"
    And user "user" has already chosen "tt201" over "tt202" in movie challenges
    And user "alice" has already chosen "tt201" over "tt202" in movie challenges
    And movie "tt101" has already won 10 challenge comparisons for "alice"
    And movie "tt102" has already won 1 challenge comparison for "alice"
    And movie "tt101" is already recommended by "user"
    When regular user "user" requests users recommended movies
    Then users recommended movies do not contain "tt101"
    And users recommended movies contain 1 movies

  Scenario: Users recommended movies are empty without matching movie challenge history
    Given movie "tt101" exists with title "Movie One"
    And movie "tt101" has already won 5 challenge comparisons for "admin"
    When regular user "user" requests users recommended movies
    Then users recommended movies contain 0 movies
