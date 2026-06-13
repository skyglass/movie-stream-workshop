Feature: add-movie-to-catalog

  Scenario: Authenticated user adds a movie
    Given the movie catalog is empty
    When contributor "user" adds movie "tt0120737" titled "The Lord of the Rings"
    Then movie "tt0120737" exists in the catalog with title "The Lord of the Rings"

  Scenario: Anonymous caller cannot add a movie through the API
    When an anonymous caller tries to add movie "tt0000001"
    Then the API response status is 401 or 403
