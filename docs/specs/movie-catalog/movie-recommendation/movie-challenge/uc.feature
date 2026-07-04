Feature: movie-challenge

  Scenario: Regular user gets next challenge from recommended movies with least challenge counts
    Given movie "tt101" exists with title "Movie One"
    And movie "tt102" exists with title "Movie Two"
    And movie "tt103" exists with title "Movie Three"
    And movie "tt104" exists with title "Movie Four"
    And movie "tt101" is already recommended by "user"
    And movie "tt102" is already recommended by "user"
    And movie "tt103" is already recommended by "user"
    And movie "tt104" is already recommended by "user"
    And movie "tt101" has already participated 1 time in challenges for "user"
    And movie "tt102" has already participated 1 time in challenges for "user"
    When regular user "user" requests the next movie challenge
    Then the movie challenge contains movies "tt103" and "tt104"

  Scenario: Regular user selects a challenge winner
    Given movie "tt101" exists with title "Movie One"
    And movie "tt102" exists with title "Movie Two"
    And movie "tt101" is already recommended by "user"
    And movie "tt102" is already recommended by "user"
    When regular user "user" selects movie "tt101" from movie challenge pair "tt101" and "tt102"
    Then movie "tt101" has 1 transitive win by "user"
    And movie "tt101" has participated 1 time in challenges for "user"
    And movie "tt102" has participated 1 time in challenges for "user"
    And movie "tt101" is recorded as winner over "tt102" for "user"
    And movie "tt101" is transitively ranked over "tt102" for "user"

  Scenario: Movie challenge stores selected movie as winner regardless of request order
    Given movie "tt101" exists with title "Movie One"
    And movie "tt102" exists with title "Movie Two"
    And movie "tt101" is already recommended by "user"
    And movie "tt102" is already recommended by "user"
    When regular user "user" selects movie "tt102" from movie challenge pair "tt102" and "tt101"
    Then movie "tt102" is recorded as winner over "tt101" for "user"
    And movie "tt102" is transitively ranked over "tt101" for "user"

  Scenario: Completed movie pairs are not offered again
    Given movie "tt101" exists with title "Movie One"
    And movie "tt102" exists with title "Movie Two"
    And movie "tt101" is already recommended by "user"
    And movie "tt102" is already recommended by "user"
    And movie pair "tt101" and "tt102" is already completed for "user"
    When regular user "user" requests the next movie challenge
    Then no movie challenge is available

  Scenario: Transitive winner-loser relationships are not offered as challenges
    Given movie "tt101" exists with title "Movie One"
    And movie "tt102" exists with title "Movie Two"
    And movie "tt103" exists with title "Movie Three"
    And movie "tt101" is already recommended by "user"
    And movie "tt102" is already recommended by "user"
    And movie "tt103" is already recommended by "user"
    And movie "tt101" has already beaten movie "tt102" for "user"
    And movie "tt102" has already beaten movie "tt103" for "user"
    When regular user "user" requests the next movie challenge
    Then no movie challenge is available

  Scenario: Direct challenge results stay separate from inferred transitive rankings
    Given movie "tt101" exists with title "Movie One"
    And movie "tt102" exists with title "Movie Two"
    And movie "tt103" exists with title "Movie Three"
    And movie "tt101" is already recommended by "user"
    And movie "tt102" is already recommended by "user"
    And movie "tt103" is already recommended by "user"
    When regular user "user" selects movie "tt101" from movie challenge pair "tt101" and "tt102"
    And regular user "user" selects movie "tt102" from movie challenge pair "tt102" and "tt103"
    Then movie "tt101" is recorded as winner over "tt102" for "user"
    And movie "tt102" is recorded as winner over "tt103" for "user"
    And movie "tt101" is not recorded as direct winner over "tt103" for "user"
    And movie "tt101" is transitively ranked over "tt103" for "user"

  Scenario: Transitive loser is not suggested against the transitive winner
    Given movie "tt101" exists with title "Movie One"
    And movie "tt102" exists with title "Movie Two"
    And movie "tt103" exists with title "Movie Three"
    And movie "tt104" exists with title "Movie Four"
    And movie "tt101" is already recommended by "user"
    And movie "tt102" is already recommended by "user"
    And movie "tt103" is already recommended by "user"
    And movie "tt104" is already recommended by "user"
    And movie "tt101" has already beaten movie "tt102" for "user"
    And movie "tt102" has already beaten movie "tt103" for "user"
    When regular user "user" requests the next movie challenge
    Then the movie challenge does not contain movies "tt101" and "tt103"
    And the movie challenge contains movies "tt101" and "tt104"

  Scenario: Less than two recommended movies cannot start a movie challenge
    Given movie "tt101" exists with title "Movie One"
    And movie "tt101" is already recommended by "user"
    When regular user "user" requests the next movie challenge
    Then no movie challenge is available
