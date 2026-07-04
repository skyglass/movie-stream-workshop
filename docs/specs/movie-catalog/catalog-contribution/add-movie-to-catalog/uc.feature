Feature: add-movie-to-catalog

  Scenario: Authenticated user adds a movie
    Given the movie catalog is empty
    When contributor "user" adds movie "tt0120737" titled "The Lord of the Rings"
    Then movie "tt0120737" exists in the catalog with title "The Lord of the Rings"

  Scenario: Authenticated user adds a movie with extended metadata
    Given the movie catalog is empty
    When contributor "user" adds movie "tt0133093" titled "The Matrix" with director "Lana Wachowski, Lilly Wachowski", writer "Lilly Wachowski, Lana Wachowski", year "1999", genre "Action, Sci-Fi", country "United States, Australia", and type "Movie"
    Then movie "tt0133093" exists in the catalog with title "The Matrix"
    And movie "tt0133093" exists in the catalog with director "Lana Wachowski, Lilly Wachowski", writer "Lilly Wachowski, Lana Wachowski", year "1999", genre "Action, Sci-Fi", country "United States, Australia", and type "Movie"

  Scenario: Authenticated user cannot add an episode type movie
    Given the movie catalog is empty
    When contributor "user" tries to add movie "tt0583459" titled "The One Where Monica Gets a Roommate" through the movie API with type "Episode"
    Then the movie API response status is 400

  Scenario: Anonymous caller cannot add a movie through the API
    When an anonymous caller tries to add movie "tt0000001"
    Then the movie API response status is 401 or 403
