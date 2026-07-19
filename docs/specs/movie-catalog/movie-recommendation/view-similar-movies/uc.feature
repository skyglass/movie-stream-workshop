Feature: view-similar-movies

  Scenario: Recommends movies sharing the seed movie's own categories
    Given movie "tt501" exists with title "Seed Movie", director "Sidney Lumet", writer "Writer Lumet Seed", year "1957", genre "Drama", country "United States", and type "Movie"
    And movie "tt502" exists with title "Other Lumet Favorite", director "Sidney Lumet", writer "Writer Lumet Other", year "1975", genre "Drama", country "United States", and type "Movie"
    And movie "tt503" exists with title "Unrelated Favorite", director "Dir X", writer "Dir X", year "1980", genre "Comedy", country "United States", and type "Movie"
    And user "user" has already ranked movies "tt502,tt503" from best to worst
    And movie "tt601" exists with title "Another Lumet Film", director "Sidney Lumet", writer "Writer Lumet Candidate", year "1962", genre "Drama", country "United States", and type "Movie"
    And movie "tt602" exists with title "Unrelated Comedy", director "Dir Y", writer "Dir Y", year "1990", genre "Comedy", country "United States", and type "Movie"
    When regular user "user" requests movies similar to movie "tt501"
    Then movies similar to that movie contain movie "tt601"
    And movies similar to that movie do not contain "tt602"

  Scenario: A category where the user has only rated the seed movie contributes no recommendations
    Given movie "tt521" exists with title "Seed Movie", director "Wong Kar-wai", writer "Writer Wong Seed", year "2000", genre "Romance", country "Hong Kong", and type "Movie"
    And movie "tt522" exists with title "Unrelated Favorite", director "Dir Z", writer "Dir Z", year "1990", genre "Comedy", country "United States", and type "Movie"
    And user "user" has already ranked movies "tt521,tt522" from best to worst
    And movie "tt621" exists with title "Another Wong Kar-wai Film", director "Wong Kar-wai", writer "Writer Wong Candidate", year "2004", genre "Romance", country "Hong Kong", and type "Movie"
    When regular user "user" requests movies similar to movie "tt521"
    Then movies similar to that movie contain 0 movies

  Scenario: The seed movie never appears as its own similar-movie candidate
    Given movie "tt531" exists with title "Seed Movie", director "Akira Kurosawa", writer "Writer Kurosawa Seed", year "1954", genre "Action", country "Japan", and type "Movie"
    And movie "tt532" exists with title "Another Kurosawa Favorite", director "Akira Kurosawa", writer "Writer Kurosawa Other", year "1961", genre "Action", country "Japan", and type "Movie"
    And movie "tt533" exists with title "Filler Favorite", director "Dir Filler", writer "Dir Filler", year "1970", genre "Filler", country "Japan", and type "Movie"
    And user "user" has already ranked movies "tt532,tt533" from best to worst
    When regular user "user" requests movies similar to movie "tt531"
    Then movies similar to that movie do not contain "tt531"

  Scenario: Similar movies are empty for anonymous viewers when nobody has rated anything in the seed's categories
    Given movie "tt541" exists with title "Some Movie"
    When anonymous viewer requests movies similar to movie "tt541"
    Then the movie API response status is 200
    And movies similar to that movie contain 0 movies

  Scenario: Similar movies for anonymous viewers fall back to every registered user's ratings in the shared categories
    Given movie "tt551" exists with title "Seed Movie", director "Ingmar Bergman", writer "Writer Bergman Seed", year "1957", genre "Drama", country "Sweden", and type "Movie"
    And movie "tt552" exists with title "Other Bergman Favorite", director "Ingmar Bergman", writer "Writer Bergman Other", year "1966", genre "Drama", country "Sweden", and type "Movie"
    And movie "tt651" exists with title "Another Bergman Film", director "Ingmar Bergman", writer "Writer Bergman Candidate", year "1972", genre "Drama", country "Sweden", and type "Movie"
    And user "alice" has already ranked movies "tt552,tt651" from best to worst
    When anonymous viewer requests movies similar to movie "tt551"
    Then movies similar to that movie contain movie "tt651"
