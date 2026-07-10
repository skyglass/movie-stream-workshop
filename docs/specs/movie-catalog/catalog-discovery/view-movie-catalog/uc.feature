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

  Scenario: Catalog marks movies disliked by the current user
    Given movie "tt0083658" exists with title "Blade Runner"
    And movie "tt0083658" is already disliked by "user"
    When regular user "user" requests the personalized movie catalog
    Then catalog movie "tt0083658" is marked disliked

  Scenario: Catalog page returns the requested slice and total count
    Given the movie catalog contains 5 titled movies
    When the viewer requests page 2 of the movie catalog with 2 movies per page
    Then the catalog discovery list contains 2 movies
    And the catalog discovery list total count is 5
    And the catalog discovery list shows "Movie 03" before "Movie 04"

  Scenario: Catalog filter matches title, director, or writer
    Given movie "tt301" exists with title "Filter Signal", director "No Match", writer "No Match", year "1999", genre "Drama", country "US", and type "movie"
    And movie "tt302" exists with title "No Match One", director "Filter Director", writer "No Match", year "2000", genre "Drama", country "US", and type "movie"
    And movie "tt303" exists with title "No Match Two", director "No Match", writer "Filter Writer", year "2001", genre "Drama", country "US", and type "movie"
    And movie "tt304" exists with title "Outside Result", director "Other Director", writer "Other Writer", year "2002", genre "Drama", country "US", and type "movie"
    When the viewer requests the movie catalog filtered by "filter"
    Then the catalog discovery list contains 3 movies
    And the catalog discovery list total count is 3
    And the catalog discovery list contains movie "tt301"
    And the catalog discovery list contains movie "tt302"
    And the catalog discovery list contains movie "tt303"
    And the catalog discovery list does not contain movie "tt304"
