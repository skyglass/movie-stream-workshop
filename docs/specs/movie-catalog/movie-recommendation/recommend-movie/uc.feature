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

  Scenario: Regular user can unrecommend a movie
    Given movie "tt0083658" exists with title "Blade Runner"
    And movie "tt0083658" is already recommended by "user"
    When regular user "user" unrecommends movie "tt0083658"
    Then movie "tt0083658" is not recommended by "user"
    And the recommendation response marks movie "tt0083658" as not recommended

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
