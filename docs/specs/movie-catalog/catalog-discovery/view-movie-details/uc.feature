Feature: view-movie-details

  Scenario: Movie details include metadata
    Given movie "tt0133093" exists with title "The Matrix"
    When the viewer opens details for movie "tt0133093"
    Then the viewed movie details show title "The Matrix"

  Scenario: Movie details include extended metadata
    Given movie "tt0133093" exists with title "The Matrix", director "Lana Wachowski, Lilly Wachowski", writer "Lilly Wachowski, Lana Wachowski", year "1999", genre "Action, Sci-Fi", country "United States, Australia", and type "Movie"
    When the viewer opens details for movie "tt0133093"
    Then the viewed movie details show director "Lana Wachowski, Lilly Wachowski", writer "Lilly Wachowski, Lana Wachowski", year "1999", genre "Action, Sci-Fi", country "United States, Australia", and type "Movie"

  Scenario: Movie details include comments newest first
    Given movie "tt0133093" exists with title "The Matrix"
    And movie "tt0133093" for detail viewing has comment "First comment" by "user"
    And movie "tt0133093" for detail viewing has comment "Second comment" by "admin"
    When the viewer opens details for movie "tt0133093"
    Then the first viewed movie comment is "Second comment"

  Scenario: Movie details mark movies recommended by the current user
    Given movie "tt0083658" exists with title "Blade Runner"
    And movie "tt0083658" is already recommended by "user"
    When regular user "user" opens details for movie "tt0083658"
    Then the viewed movie is marked recommended

  Scenario: Movie details are available to anonymous viewers
    Given movie "tt0133093" exists with title "The Matrix"
    When anonymous viewer requests movie details for "tt0133093"
    Then the movie API response status is 200
