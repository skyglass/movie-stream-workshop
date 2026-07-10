Feature: view-users-recommended-movies

  Scenario: Users recommended movies prefer calculated ratings from similar users
    Given movie "tt101" exists with title "Movie One"
    And movie "tt102" exists with title "Movie Two"
    And movie "tt201" exists with title "Pair One"
    And movie "tt202" exists with title "Pair Two"
    And user "user" has already ranked movies "tt201,tt202" from best to worst
    And user "alice" has already ranked movies "tt101,tt201,tt202" from best to worst
    And user "bob" has already ranked movies "tt201,tt102,tt202" from best to worst
    When regular user "user" requests users recommended movies
    Then users recommended movies show "tt101" before "tt102"

  Scenario: Users recommended movies exclude movies already recommended by the current user
    Given movie "tt101" exists with title "Movie One"
    And movie "tt102" exists with title "Movie Two"
    And movie "tt201" exists with title "Pair One"
    And movie "tt202" exists with title "Pair Two"
    And user "user" has already chosen "tt201" over "tt202" in movie challenges
    And user "alice" has already chosen "tt201" over "tt202" in movie challenges
    And user "alice" has already ranked movies "tt101,tt102" from best to worst
    And movie "tt101" is already recommended by "user"
    When regular user "user" requests users recommended movies
    Then users recommended movies do not contain "tt101"
    And users recommended movies contain 1 movies

  Scenario: Users recommended movies exclude movies disliked by the current user
    Given movie "tt101" exists with title "Movie One"
    And movie "tt102" exists with title "Movie Two"
    And movie "tt201" exists with title "Pair One"
    And movie "tt202" exists with title "Pair Two"
    And user "user" has already chosen "tt201" over "tt202" in movie challenges
    And user "alice" has already chosen "tt201" over "tt202" in movie challenges
    And user "alice" has already ranked movies "tt101,tt102" from best to worst
    And movie "tt101" is already disliked by "user"
    When regular user "user" requests users recommended movies
    Then users recommended movies do not contain "tt101"
    And users recommended movies contain 1 movies

  Scenario: Users recommended movies are empty without matching movie challenge history
    Given movie "tt101" exists with title "Movie One"
    And movie "tt101" has already won 5 direct challenge comparisons for "admin"
    When regular user "user" requests users recommended movies
    Then users recommended movies contain 0 movies

  Scenario: Users recommended movies second page keeps total recommendation count
    Given movie "tt101" exists with title "Movie One"
    And movie "tt102" exists with title "Movie Two"
    And movie "tt103" exists with title "Movie Three"
    And movie "tt104" exists with title "Movie Four"
    And movie "tt201" exists with title "Pair One"
    And movie "tt202" exists with title "Pair Two"
    And user "user" has already chosen "tt201" over "tt202" in movie challenges
    And user "alice" has already chosen "tt201" over "tt202" in movie challenges
    And user "alice" has already ranked movies "tt101,tt102,tt103,tt104" from best to worst
    When regular user "user" requests page 2 of users recommended movies with 3 movies per page
    Then users recommended movies contain 1 movies
    And users recommended movies total count is 4
    And users recommended movies contain movie "tt104"

  Scenario: Users recommended movies can be filtered by movie metadata
    Given movie "tt101" exists with title "Recommended Signal"
    And movie "tt102" exists with title "Movie Two"
    And movie "tt201" exists with title "Pair One"
    And movie "tt202" exists with title "Pair Two"
    And user "user" has already ranked movies "tt201,tt202" from best to worst
    And user "alice" has already ranked movies "tt101,tt102,tt201,tt202" from best to worst
    When regular user "user" requests users recommended movies filtered by "recommended"
    Then users recommended movies contain 1 movies
    And users recommended movies total count is 1
    And users recommended movies contain movie "tt101"
    And users recommended movies do not contain "tt102"
