Feature: movie-challenge

  Scenario: Regular user gets next challenge from recommended movies with least direct evidence
    Given movie "tt101" exists with title "Movie One"
    And movie "tt102" exists with title "Movie Two"
    And movie "tt103" exists with title "Movie Three"
    And movie "tt104" exists with title "Movie Four"
    And movie "tt101" is already recommended by "user"
    And movie "tt102" is already recommended by "user"
    And movie "tt103" is already recommended by "user"
    And movie "tt104" is already recommended by "user"
    And movie "tt101" has already beaten movie "tt102" for "user"
    When regular user "user" requests the next movie challenge
    Then the movie challenge contains movies "tt103" and "tt104"

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
    And movie "tt102" has rank 2 and rating "0.00" for "user"
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

  Scenario: Directly completed movie pairs are not offered again
    Given movie "tt101" exists with title "Movie One"
    And movie "tt102" exists with title "Movie Two"
    And movie "tt101" is already recommended by "user"
    And movie "tt102" is already recommended by "user"
    And movie pair "tt101" and "tt102" is already completed for "user"
    When regular user "user" requests the next movie challenge
    Then no movie challenge is available

  Scenario: No movie challenge is available when the user has no positive recommendations
    Given movie "tt101" exists with title "Movie One"
    And movie "tt102" exists with title "Movie Two"
    When regular user "user" requests the next movie challenge
    Then no movie challenge is available

  Scenario: Disliked movies do not count as challenge candidates
    Given movie "tt101" exists with title "Movie One"
    And movie "tt102" exists with title "Movie Two"
    And movie "tt101" is already recommended by "user"
    And movie "tt102" is already disliked by "user"
    When regular user "user" requests the next movie challenge
    Then no movie challenge is available

  Scenario: No movie challenge is available when every positive recommendation pair is completed
    Given movie "tt101" exists with title "Movie One"
    And movie "tt102" exists with title "Movie Two"
    And movie "tt103" exists with title "Movie Three"
    And movie "tt101" is already recommended by "user"
    And movie "tt102" is already recommended by "user"
    And movie "tt103" is already recommended by "user"
    And movie pair "tt101" and "tt102" is already completed for "user"
    And movie pair "tt101" and "tt103" is already completed for "user"
    And movie pair "tt102" and "tt103" is already completed for "user"
    When regular user "user" requests the next movie challenge
    Then no movie challenge is available

  Scenario: Weak inferred ranking can still be offered as a challenge
    Given movie "tt101" exists with title "Movie One"
    And movie "tt102" exists with title "Movie Two"
    And movie "tt103" exists with title "Movie Three"
    And movie "tt101" is already recommended by "user"
    And movie "tt102" is already recommended by "user"
    And movie "tt103" is already recommended by "user"
    And movie "tt101" has already beaten movie "tt102" for "user"
    And movie "tt102" has already beaten movie "tt103" for "user"
    When regular user "user" requests the next movie challenge
    Then the movie challenge contains movies "tt101" and "tt103"

  Scenario: No movie challenge is available when only a far low-value pair remains
    Given movie "tt101" exists with title "Movie One"
    And movie "tt102" exists with title "Movie Two"
    And movie "tt103" exists with title "Movie Three"
    And movie "tt104" exists with title "Movie Four"
    And movie "tt105" exists with title "Movie Five"
    And movie "tt106" exists with title "Movie Six"
    And movie "tt107" exists with title "Movie Seven"
    And movie "tt108" exists with title "Movie Eight"
    And movie "tt101" is already recommended by "user"
    And movie "tt102" is already recommended by "user"
    And movie "tt103" is already recommended by "user"
    And movie "tt104" is already recommended by "user"
    And movie "tt105" is already recommended by "user"
    And movie "tt106" is already recommended by "user"
    And movie "tt107" is already recommended by "user"
    And movie "tt108" is already recommended by "user"
    And user "user" has already ranked movies "tt101,tt102,tt103,tt104,tt105,tt106,tt107,tt108" from best to worst except pair "tt101" and "tt108"
    When regular user "user" requests the next movie challenge
    Then no movie challenge is available

  Scenario: Close ranked movies can still be challenged after the comparison target is reached
    Given movie "tt101" exists with title "Movie One"
    And movie "tt102" exists with title "Movie Two"
    And movie "tt103" exists with title "Movie Three"
    And movie "tt104" exists with title "Movie Four"
    And movie "tt105" exists with title "Movie Five"
    And movie "tt106" exists with title "Movie Six"
    And movie "tt107" exists with title "Movie Seven"
    And movie "tt108" exists with title "Movie Eight"
    And movie "tt101" is already recommended by "user"
    And movie "tt102" is already recommended by "user"
    And movie "tt103" is already recommended by "user"
    And movie "tt104" is already recommended by "user"
    And movie "tt105" is already recommended by "user"
    And movie "tt106" is already recommended by "user"
    And movie "tt107" is already recommended by "user"
    And movie "tt108" is already recommended by "user"
    And user "user" has already ranked movies "tt101,tt102,tt103,tt104,tt105,tt106,tt107,tt108" from best to worst except pair "tt104" and "tt105"
    When regular user "user" requests the next movie challenge
    Then the movie challenge contains movies "tt104" and "tt105"

  Scenario: New movies can be challenged against movies that already have enough comparisons
    Given movie "tt101" exists with title "Movie One"
    And movie "tt102" exists with title "Movie Two"
    And movie "tt103" exists with title "Movie Three"
    And movie "tt104" exists with title "Movie Four"
    And movie "tt105" exists with title "Movie Five"
    And movie "tt106" exists with title "Movie Six"
    And movie "tt107" exists with title "Movie Seven"
    And movie "tt108" exists with title "Movie Eight"
    And movie "tt109" exists with title "Movie Nine"
    And movie "tt101" is already recommended by "user"
    And movie "tt102" is already recommended by "user"
    And movie "tt103" is already recommended by "user"
    And movie "tt104" is already recommended by "user"
    And movie "tt105" is already recommended by "user"
    And movie "tt106" is already recommended by "user"
    And movie "tt107" is already recommended by "user"
    And movie "tt108" is already recommended by "user"
    And movie "tt109" is already recommended by "user"
    And user "user" has already ranked movies "tt101,tt102,tt103,tt104,tt105,tt106,tt107,tt108" from best to worst
    When regular user "user" requests the next movie challenge
    Then the movie challenge contains movies "tt101" and "tt109"

  Scenario: No movie challenge is available when the only remaining pair is transitively confident
    Given movie "tt101" exists with title "Movie One"
    And movie "tt102" exists with title "Movie Two"
    And movie "tt103" exists with title "Movie Three"
    And movie "tt104" exists with title "Movie Four"
    And movie "tt105" exists with title "Movie Five"
    And movie "tt106" exists with title "Movie Six"
    And movie "tt107" exists with title "Movie Seven"
    And movie "tt108" exists with title "Movie Eight"
    And movie "tt109" exists with title "Movie Nine"
    And movie "tt110" exists with title "Movie Ten"
    And movie "tt111" exists with title "Movie Eleven"
    And movie "tt112" exists with title "Movie Twelve"
    And movie "tt101" is already recommended by "user"
    And movie "tt102" is already recommended by "user"
    And movie "tt103" is already recommended by "user"
    And movie "tt104" is already recommended by "user"
    And movie "tt105" is already recommended by "user"
    And movie "tt106" is already recommended by "user"
    And movie "tt107" is already recommended by "user"
    And movie "tt108" is already recommended by "user"
    And movie "tt109" is already recommended by "user"
    And movie "tt110" is already recommended by "user"
    And movie "tt111" is already recommended by "user"
    And movie "tt112" is already recommended by "user"
    And user "user" has already ranked movies "tt101,tt102,tt103,tt104,tt105,tt106,tt107,tt108,tt109,tt110,tt111,tt112" from best to worst except pair "tt101" and "tt112"
    When regular user "user" requests the next movie challenge
    Then no movie challenge is available

  Scenario: Only one positive recommendation cannot start a movie challenge
    Given movie "tt101" exists with title "Movie One"
    And movie "tt101" is already recommended by "user"
    When regular user "user" requests the next movie challenge
    Then no movie challenge is available
