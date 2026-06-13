Feature: view-movie-catalog

  Scenario: Catalog lists movies in title order
    Given the movie catalog contains "Blade Runner" and "Alien"
    When the viewer requests the movie catalog
    Then the catalog lists "Alien" before "Blade Runner"

  Scenario: Empty catalog is available
    Given the movie catalog is empty
    When the viewer requests the movie catalog
    Then the catalog contains 0 movies
