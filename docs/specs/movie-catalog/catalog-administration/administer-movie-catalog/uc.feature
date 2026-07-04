Feature: administer-movie-catalog

  Scenario: Admin updates movie metadata
    Given movie "tt0083658" exists with title "Blade Runner"
    When admin updates movie "tt0083658" title to "Blade Runner Final Cut"
    Then movie "tt0083658" exists in the catalog with title "Blade Runner Final Cut"

  Scenario: Admin edits all supported movie fields through the API
    Given movie "tt0083658" exists with title "Blade Runner"
    When admin user "admin" updates movie "tt0083658" through the movie API with title "Blade Runner Final Cut", director "Ridley Scott", writer "Hampton Fancher, David Webb Peoples", year "1982", genre "Sci-Fi, Thriller", country "United States", and type "Movie"
    Then the movie API response status is 200
    And movie "tt0083658" exists in the catalog with title "Blade Runner Final Cut"
    And movie "tt0083658" exists in the catalog with director "Ridley Scott", writer "Hampton Fancher, David Webb Peoples", year "1982", genre "Sci-Fi, Thriller", country "United States", and type "Movie"

  Scenario: Admin cannot update a movie to episode type
    Given movie "tt0083658" exists with title "Blade Runner"
    When admin user "admin" updates movie "tt0083658" through the movie API with title "Blade Runner Final Cut", director "Ridley Scott", writer "Hampton Fancher, David Webb Peoples", year "1982", genre "Sci-Fi, Thriller", country "United States", and type "Episode"
    Then the movie API response status is 400

  Scenario: Regular user cannot delete a movie through the API
    Given movie "tt0083658" exists with title "Blade Runner"
    When regular user "user" tries to delete movie "tt0083658"
    Then the movie API response status is 403

  Scenario: Regular user cannot update movie metadata through the API
    Given movie "tt0083658" exists with title "Blade Runner"
    When regular user "user" tries to update movie "tt0083658" title to "Blade Runner Final Cut"
    Then the movie API response status is 403
    And movie "tt0083658" exists in the catalog with title "Blade Runner"
