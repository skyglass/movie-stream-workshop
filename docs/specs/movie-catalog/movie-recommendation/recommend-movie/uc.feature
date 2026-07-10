Feature: recommend-movie

  Scenario: Regular user can recommend a movie
    Given movie "tt0083658" exists with title "Blade Runner"
    When regular user "user" recommends movie "tt0083658"
    Then movie "tt0083658" is recommended by "user"
    And the recommendation response marks movie "tt0083658" as recommended

  Scenario: Regular user recommends a movie that is not yet in the catalog
    Given the movie catalog is empty
    When regular user "user" recommends new movie "tt0098936" titled "Twin Peaks" with director "Mark Frost, David Lynch", writer "Mark Frost, David Lynch", year "1990-1991", genre "Crime, Drama, Mystery", country "United States", and type "Series"
    Then movie "tt0098936" is recommended by "user"
    And movie "tt0098936" exists in the catalog with title "Twin Peaks"
    And movie "tt0098936" exists in the catalog with director "Mark Frost, David Lynch", writer "Mark Frost, David Lynch", year "1990-1991", genre "Crime, Drama, Mystery", country "United States", and type "Series"
    And the recommendation response marks movie "tt0098936" as recommended

  Scenario: Regular user cannot recommend a new episode type movie
    Given the movie catalog is empty
    When regular user "user" tries to recommend new movie "tt0583459" titled "The One Where Monica Gets a Roommate" through the movie API with type "Episode"
    Then the movie API response status is 400

  Scenario: Regular user can unrecommend a movie
    Given movie "tt0083658" exists with title "Blade Runner"
    And movie "tt0083658" is already recommended by "user"
    When regular user "user" unrecommends movie "tt0083658"
    Then movie "tt0083658" is not recommended by "user"
    And the recommendation response marks movie "tt0083658" as not recommended

  Scenario: Unrecommend removes the movie from challenge history and rebuilds ranks
    Given movie "tt101" exists with title "Movie One"
    And movie "tt102" exists with title "Movie Two"
    And movie "tt103" exists with title "Movie Three"
    And movie "tt101" has already beaten movie "tt102" for "user"
    And movie "tt101" has already beaten movie "tt103" for "user"
    And movie "tt102" has already beaten movie "tt103" for "user"
    When regular user "user" unrecommends movie "tt102"
    Then movie "tt102" is not recommended by "user"
    And movie "tt102" has no direct challenge votes for "user"
    And movie "tt102" has no rank and rating for "user"
    And movie "tt102" has no challenge count for "user"
    And movie "tt101" has rank 1 and rating "10.00" for "user"
    And movie "tt103" has rank 2 and rating "1.00" for "user"
    And movie "tt101" is recorded as winner over "tt103" for "user"

  Scenario: Replay clears the movie challenge history and recommends the movie again
    Given movie "tt101" exists with title "Movie One"
    And movie "tt102" exists with title "Movie Two"
    And movie "tt103" exists with title "Movie Three"
    And movie "tt101" has already beaten movie "tt102" for "user"
    And movie "tt102" has already beaten movie "tt103" for "user"
    When regular user "user" replays movie "tt102" through the movie API
    Then the movie API response status is 200
    And movie "tt102" is recommended by "user"
    And the recommendation response marks movie "tt102" as recommended
    And movie "tt102" has no direct challenge votes for "user"
    And movie "tt102" has no rank and rating for "user"
    And movie "tt102" has no challenge count for "user"
    When regular user "user" requests the next movie challenge
    Then the movie challenge contains movies "tt101" and "tt102"

  Scenario: Regular user can dislike a users recommended movie
    Given movie "tt0083658" exists with title "Blade Runner"
    When regular user "user" dislikes movie "tt0083658"
    Then movie "tt0083658" is disliked by "user"
    And the recommendation response marks movie "tt0083658" as disliked

  Scenario: Regular user can clear a disliked movie
    Given movie "tt0083658" exists with title "Blade Runner"
    And movie "tt0083658" is already disliked by "user"
    When regular user "user" unrecommends movie "tt0083658"
    Then movie "tt0083658" is not disliked by "user"
    And the recommendation response marks movie "tt0083658" as not disliked

  Scenario: Anonymous caller cannot recommend a movie through the API
    Given movie "tt0083658" exists with title "Blade Runner"
    When an anonymous caller tries to recommend movie "tt0083658"
    Then the movie API response status is 401
