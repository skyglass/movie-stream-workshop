Feature: view-movie-catalog

  Scenario: Catalog lists movies in title order
    Given the catalog discovery list contains "Blade Runner" and "Alien"
    When the viewer requests the movie catalog
    Then the catalog discovery list shows "Alien" before "Blade Runner"

  Scenario: Empty catalog is available
    Given the movie catalog is empty
    When the viewer requests the movie catalog
    Then the catalog discovery list contains 0 movies

  Scenario: Catalog marks movies recommended by the current user
    Given movie "tt0083658" exists with title "Blade Runner"
    And movie "tt0083658" is already recommended by "user"
    When regular user "user" requests the personalized movie catalog
    Then catalog movie "tt0083658" is marked recommended
