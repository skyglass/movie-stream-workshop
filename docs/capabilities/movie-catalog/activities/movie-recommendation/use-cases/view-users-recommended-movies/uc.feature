Feature: view-users-recommended-movies

  Scenario: Users recommended movies prefer votes from users with similar movie challenge choices
    Given movie "tt101" exists with title "Movie One"
    And movie "tt102" exists with title "Movie Two"
    And movie "tt201" exists with title "Pair One"
    And movie "tt202" exists with title "Pair Two"
    And movie "tt203" exists with title "Pair Three"
    And user "user" has completed movie pair "tt201" and "tt202" with movie1_wins true
    And user "user" has completed movie pair "tt201" and "tt203" with movie1_wins true
    And user "user" has completed movie pair "tt202" and "tt203" with movie1_wins false
    And user "alice" has completed movie pair "tt201" and "tt202" with movie1_wins true
    And user "alice" has completed movie pair "tt201" and "tt203" with movie1_wins true
    And user "alice" has completed movie pair "tt202" and "tt203" with movie1_wins false
    And user "bob" has completed movie pair "tt201" and "tt202" with movie1_wins true
    And movie "tt101" has already received 2 votes from "alice"
    And movie "tt102" has already received 5 votes from "bob"
    When regular user "user" requests users recommended movies
    Then users recommended movies show "tt101" before "tt102"

  Scenario: Users recommended movies exclude movies already recommended by the current user
    Given movie "tt101" exists with title "Movie One"
    And movie "tt102" exists with title "Movie Two"
    And movie "tt201" exists with title "Pair One"
    And movie "tt202" exists with title "Pair Two"
    And user "user" has completed movie pair "tt201" and "tt202" with movie1_wins true
    And user "alice" has completed movie pair "tt201" and "tt202" with movie1_wins true
    And movie "tt101" has already received 10 votes from "alice"
    And movie "tt102" has already received 1 vote from "alice"
    And movie "tt101" is already recommended by "user"
    When regular user "user" requests users recommended movies
    Then users recommended movies do not contain "tt101"
    And users recommended movies contain 1 movies

  Scenario: Users recommended movies are empty without matching movie challenge history
    Given movie "tt101" exists with title "Movie One"
    And movie "tt101" has already received 5 votes from "admin"
    When regular user "user" requests users recommended movies
    Then users recommended movies contain 0 movies
