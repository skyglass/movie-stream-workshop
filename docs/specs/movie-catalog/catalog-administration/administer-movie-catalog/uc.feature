Feature: administer-movie-catalog

  Scenario: Admin updates movie metadata
    Given movie "tt0083658" exists with title "Blade Runner"
    When admin updates movie "tt0083658" title to "Blade Runner Final Cut"
    Then movie "tt0083658" exists in the catalog with title "Blade Runner Final Cut"

  Scenario: Regular user cannot delete a movie through the API
    Given movie "tt0083658" exists with title "Blade Runner"
    When regular user "user" tries to delete movie "tt0083658"
    Then the movie API response status is 403

  Scenario: Regular user cannot update movie metadata through the API
    Given movie "tt0083658" exists with title "Blade Runner"
    When regular user "user" tries to update movie "tt0083658" title to "Blade Runner Final Cut"
    Then the movie API response status is 403
    And movie "tt0083658" exists in the catalog with title "Blade Runner"
