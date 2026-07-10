Feature: share-my-favorite-movies

  Scenario: Favorite movies are private by default
    Given movie "tt101" exists with title "Movie One"
    And movie "tt102" exists with title "Movie Two"
    And user "sky composer" has already ranked movies "tt101,tt102" from best to worst
    When anonymous viewer requests shared favorite movies for encoded username "sky%20composer"
    Then the movie API response status is 404

  Scenario: User shares favorite movies through a public encoded username link
    Given movie "tt101" exists with title "Movie One"
    And movie "tt102" exists with title "Movie Two"
    And user "sky composer" has already ranked movies "tt101,tt102" from best to worst
    When regular user "sky composer" shares own favorite movies
    Then favorite movies sharing for "sky composer" is public
    And my favorite movies share path is "/my-favorite-movies/sky%20composer"
    When anonymous viewer requests shared favorite movies for encoded username "sky%20composer"
    Then the movie API response status is 200
    And favorite movies contain movie "tt101"
    And favorite movies contain movie "tt102"
    And favorite movies total count is 2

  Scenario: Public shared favorite movies can be filtered
    Given movie "tt101" exists with title "Shared Signal"
    And movie "tt102" exists with title "Movie Two"
    And user "sky composer" has already ranked movies "tt101,tt102" from best to worst
    When regular user "sky composer" shares own favorite movies
    And anonymous viewer requests shared favorite movies for encoded username "sky%20composer" filtered by "shared"
    Then the movie API response status is 200
    And favorite movies contain 1 movies
    And favorite movies total count is 1
    And favorite movies contain movie "tt101"
    And favorite movies do not contain movie "tt102"

  Scenario: User makes shared favorite movies private
    Given movie "tt101" exists with title "Movie One"
    And movie "tt102" exists with title "Movie Two"
    And user "sky composer" has already ranked movies "tt101,tt102" from best to worst
    When regular user "sky composer" shares own favorite movies
    And regular user "sky composer" makes own favorite movies private
    Then favorite movies sharing for "sky composer" is private
    When anonymous viewer requests shared favorite movies for encoded username "sky%20composer"
    Then the movie API response status is 404
