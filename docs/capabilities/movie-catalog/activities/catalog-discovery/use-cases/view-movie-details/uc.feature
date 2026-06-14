Feature: view-movie-details

  Scenario: Movie details include metadata
    Given movie "tt0133093" exists with title "The Matrix"
    When the viewer opens details for movie "tt0133093"
    Then the viewed movie details show title "The Matrix"

  Scenario: Movie details include comments newest first
    Given movie "tt0133093" exists with title "The Matrix"
    And movie "tt0133093" for detail viewing has comment "First comment" by "user"
    And movie "tt0133093" for detail viewing has comment "Second comment" by "admin"
    When the viewer opens details for movie "tt0133093"
    Then the first viewed movie comment is "Second comment"
