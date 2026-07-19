Feature: view-similar-to-favorite-movies

  Scenario: A well-supported category outranks a sparsely-supported one even when its lone rating is higher
    Given movie "tt301" exists with title "Sole SciFi Favorite", director "Dir Sci", writer "Dir Sci", year "2001", genre "SciFi", country "United States", and type "Movie"
    And movie "tt201" exists with title "Crime Pick One", director "Dir C1", writer "Dir C1", year "2002", genre "Crime", country "United States", and type "Movie"
    And movie "tt202" exists with title "Crime Pick Two", director "Dir C2", writer "Dir C2", year "2003", genre "Crime", country "United States", and type "Movie"
    And movie "tt203" exists with title "Crime Pick Three", director "Dir C3", writer "Dir C3", year "2004", genre "Crime", country "United States", and type "Movie"
    And movie "tt204" exists with title "Crime Pick Four", director "Dir C4", writer "Dir C4", year "2005", genre "Crime", country "United States", and type "Movie"
    And movie "tt205" exists with title "Crime Pick Five", director "Dir C5", writer "Dir C5", year "2006", genre "Crime", country "United States", and type "Movie"
    And movie "tt206" exists with title "Filler One", director "Dir F1", writer "Dir F1", year "2007", genre "Filler", country "United States", and type "Movie"
    And movie "tt207" exists with title "Filler Two", director "Dir F2", writer "Dir F2", year "2008", genre "Filler", country "United States", and type "Movie"
    And movie "tt208" exists with title "Filler Three", director "Dir F3", writer "Dir F3", year "2009", genre "Filler", country "United States", and type "Movie"
    And movie "tt209" exists with title "Filler Four", director "Dir F4", writer "Dir F4", year "2010", genre "Filler", country "United States", and type "Movie"
    And movie "tt210" exists with title "Filler Five", director "Dir F5", writer "Dir F5", year "2011", genre "Filler", country "United States", and type "Movie"
    And movie "tt213" exists with title "Filler Six", director "Dir F6", writer "Dir F6", year "2012", genre "Filler", country "United States", and type "Movie"
    And user "user" has already ranked movies "tt301,tt201,tt202,tt203,tt204,tt205,tt206,tt207,tt208,tt209,tt210,tt213" from best to worst
    And movie "tt401" exists with title "New Crime Movie", director "Dir NC", writer "Dir NC", year "2020", genre "Crime", country "United States", and type "Movie"
    And movie "tt402" exists with title "New SciFi Movie", director "Dir NS", writer "Dir NS", year "2020", genre "SciFi", country "United States", and type "Movie"
    When regular user "user" requests similar to favorite movies
    Then similar to favorite movies show "tt401" before "tt402"
    And similar to favorite movies contain movie "tt402"

  Scenario: A movie matching two scored categories outranks one matching only one
    Given movie "tt211" exists with title "Heist Favorite", director "Michael Mann", writer "Writer Mann One", year "2001", genre "Crime", country "United States", and type "Movie"
    And movie "tt212" exists with title "Second Crime Favorite", director "Michael Mann", writer "Writer Mann Two", year "2002", genre "Crime", country "United States", and type "Movie"
    And user "user" has already ranked movies "tt211,tt212" from best to worst
    And movie "tt411" exists with title "Double Match Candidate", director "Michael Mann", writer "Writer Mann Three", year "2010", genre "Crime", country "United States", and type "Movie"
    And movie "tt412" exists with title "Single Match Candidate", director "Michael Mann", writer "Writer Mann Four", year "2010", genre "Drama", country "United States", and type "Movie"
    When regular user "user" requests similar to favorite movies
    Then similar to favorite movies show "tt411" before "tt412"

  Scenario: Candidates tied on category score break the tie by how other users have rated them
    Given movie "tt261" exists with title "Base Favorite", director "Dir T1", writer "Dir T1", year "2001", genre "Thriller", country "United States", and type "Movie"
    And movie "tt262" exists with title "Filler Favorite", director "Dir T2", writer "Dir T2", year "2002", genre "Filler", country "United States", and type "Movie"
    And user "user" has already ranked movies "tt261,tt262" from best to worst
    And movie "tt461" exists with title "Thriller Candidate Unseen By Anyone", director "Dir T3", writer "Dir T3", year "2010", genre "Thriller", country "United States", and type "Movie"
    And movie "tt462" exists with title "Thriller Candidate Loved By Others", director "Dir T4", writer "Dir T4", year "2010", genre "Thriller", country "United States", and type "Movie"
    And movie "tt463" exists with title "Other Filler", director "Dir T5", writer "Dir T5", year "2011", genre "Filler2", country "United States", and type "Movie"
    And user "alice" has already ranked movies "tt462,tt463" from best to worst
    When regular user "user" requests similar to favorite movies
    Then similar to favorite movies show "tt462" before "tt461"

  Scenario: Similar to favorite movies exclude movies already recommended by the current user
    Given movie "tt221" exists with title "Base Favorite", director "Dir A1", writer "Dir A1", year "2001", genre "Adventure", country "United States", and type "Movie"
    And movie "tt222" exists with title "Filler Favorite", director "Dir A2", writer "Dir A2", year "2002", genre "Filler", country "United States", and type "Movie"
    And user "user" has already ranked movies "tt221,tt222" from best to worst
    And movie "tt421" exists with title "Adventure Candidate", director "Dir A3", writer "Dir A3", year "2010", genre "Adventure", country "United States", and type "Movie"
    And movie "tt421" is already recommended by "user"
    When regular user "user" requests similar to favorite movies
    Then similar to favorite movies do not contain "tt421"
    And similar to favorite movies contain 0 movies

  Scenario: Similar to favorite movies exclude movies already disliked by the current user
    Given movie "tt223" exists with title "Base Favorite", director "Dir A4", writer "Dir A4", year "2001", genre "Fantasy", country "United States", and type "Movie"
    And movie "tt224" exists with title "Filler Favorite", director "Dir A5", writer "Dir A5", year "2002", genre "Filler", country "United States", and type "Movie"
    And user "user" has already ranked movies "tt223,tt224" from best to worst
    And movie "tt423" exists with title "Fantasy Candidate", director "Dir A6", writer "Dir A6", year "2010", genre "Fantasy", country "United States", and type "Movie"
    And movie "tt423" is already disliked by "user"
    When regular user "user" requests similar to favorite movies
    Then similar to favorite movies do not contain "tt423"
    And similar to favorite movies contain 0 movies

  Scenario: Similar to favorite movies can be filtered by movie metadata
    Given movie "tt231" exists with title "Base Favorite", director "Dir A7", writer "Dir A7", year "2001", genre "Mystery", country "United States", and type "Movie"
    And movie "tt232" exists with title "Filler Favorite", director "Dir A8", writer "Dir A8", year "2002", genre "Filler", country "United States", and type "Movie"
    And user "user" has already ranked movies "tt231,tt232" from best to worst
    And movie "tt431" exists with title "Mystery Signal", director "Dir A9", writer "Dir A9", year "2010", genre "Mystery", country "United States", and type "Movie"
    And movie "tt432" exists with title "Mystery Noise", director "Dir A10", writer "Dir A10", year "2011", genre "Mystery", country "United States", and type "Movie"
    When regular user "user" requests similar to favorite movies filtered by "Signal"
    Then similar to favorite movies contain 1 movies
    And similar to favorite movies contain movie "tt431"
    And similar to favorite movies do not contain "tt432"

  Scenario: A user with no rated movies has no similar movies
    Given movie "tt241" exists with title "Unrelated Movie", director "Dir B1", writer "Dir B1", year "2001", genre "Horror", country "United States", and type "Movie"
    When regular user "user" requests similar to favorite movies
    Then similar to favorite movies contain 0 movies
