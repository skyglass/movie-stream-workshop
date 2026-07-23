Feature: curate-movie-guide

  Background:
    Given "curator" creates a Movie Guide named "Heist Movies"

  Scenario: A regular user can create a Movie Guide and becomes its owner
    Then the Movie Guide API response status is 201
    And the Movie Guide "Heist Movies" is owned by "curator"
    And the Movie Guide "Heist Movies" has type "Guide" under root category "Guides"

  Scenario: A regular user can create a Movie Personality under its own root
    When "curator" creates a Movie Personality named "What Kubrick Would Watch"
    Then the Movie Guide API response status is 201
    And the Movie Guide "What Kubrick Would Watch" has type "Personality" under root category "Personalities"

  Scenario: Creating a Movie Guide with a duplicate name is rejected
    When "curator" creates a Movie Guide named "Heist Movies"
    Then the Movie Guide API response status is 409

  Scenario: An anonymous viewer can look up a Movie Guide by its category
    When an anonymous viewer requests the Movie Guide "Heist Movies" through the API
    Then the Movie Guide API response status is 200
    And the Movie Guide response body identifies the owner as "curator"

  Scenario: The owner can subscribe their Guide to an existing category
    Given category "Genres" exists
    When user "curator" with role "USER" subscribes the Movie Guide "Heist Movies" to category "Genres"
    Then the Movie Guide API response status is 201
    And the Movie Guide "Heist Movies" is subscribed to category "Genres"

  Scenario: Two different guides can subscribe to the same category
    Given category "Genres" exists
    And "curator" creates a Movie Guide named "Second Guide"
    And user "curator" with role "USER" subscribes the Movie Guide "Heist Movies" to category "Genres"
    When user "curator" with role "USER" subscribes the Movie Guide "Second Guide" to category "Genres"
    Then the Movie Guide API response status is 201
    And the Movie Guide "Second Guide" is subscribed to category "Genres"

  Scenario: Unsubscribing removes a category from a Guide's subscriptions without touching its original parent
    Given category "Genres" exists
    And category "Drama" exists under category "Genres"
    And user "curator" with role "USER" subscribes the Movie Guide "Heist Movies" to category "Drama"
    When user "curator" with role "USER" unsubscribes the Movie Guide "Heist Movies" from category "Drama"
    Then the Movie Guide API response status is 204
    And the Movie Guide "Heist Movies" is not subscribed to category "Drama"
    And category "Drama" still has parent "Genres"

  Scenario: Subscribing to a category preserves its original parent and adds an independent subscription entry
    Given category "Genres" exists
    And category "Drama" exists under category "Genres"
    When user "curator" with role "USER" subscribes the Movie Guide "Heist Movies" to category "Drama"
    Then the Movie Guide API response status is 201
    And category "Drama" still has parent "Genres"
    And the Movie Guide "Heist Movies" is subscribed to category "Drama"

  Scenario: Subscribing a Guide to the same category twice succeeds without conflict
    Given category "Genres" exists
    And user "curator" with role "USER" subscribes the Movie Guide "Heist Movies" to category "Genres"
    When user "curator" with role "USER" subscribes the Movie Guide "Heist Movies" to category "Genres"
    Then the Movie Guide API response status is 201
    And the Movie Guide "Heist Movies" is subscribed to category "Genres"

  Scenario: A non-owner cannot subscribe someone else's Guide
    Given category "Genres" exists
    When user "intruder" with role "USER" subscribes the Movie Guide "Heist Movies" to category "Genres"
    Then the Movie Guide API response status is 403

  Scenario: A MOVIES_GUIDE curator can subscribe someone else's Guide
    Given category "Genres" exists
    When user "helper" with role "MOVIES_GUIDE" subscribes the Movie Guide "Heist Movies" to category "Genres"
    Then the Movie Guide API response status is 201

  Scenario: The owner can add an existing movie to their Guide
    Given movie "tt101" exists with title "Movie One"
    When user "curator" with role "USER" adds movie "tt101" to the Movie Guide "Heist Movies"
    Then the Movie Guide API response status is 204
    And the Movie Guide "Heist Movies" movie list contains "tt101"

  Scenario: Adding a movie to a subscribed category fans it out to the category it follows
    Given category "Genres" exists
    And user "curator" with role "USER" subscribes the Movie Guide "Heist Movies" to category "Genres"
    And movie "tt101" exists with title "Movie One"
    When user "curator" with role "USER" adds movie "tt101" to category "Genres" of the Movie Guide "Heist Movies"
    Then the Movie Guide API response status is 204
    And category "Genres" still contains movie "tt101"

  Scenario: CSV import links an existing catalog movie by imdb id
    Given movie "tt101" exists with title "Movie One"
    When "curator" imports CSV row "tt101" into the Movie Guide "Heist Movies"
    Then the Movie Guide API response status is 200
    And the Movie Guide "Heist Movies" movie list contains "tt101"
    And the Movie Guide CSV import reports no failed rows

  Scenario: CSV import routes a movie to a suggested category path rooted at the guide
    Given movie "tt101" exists with title "Movie One"
    When "curator" imports CSV row "tt101" with suggested category "Genres.Drama" into the Movie Guide "Heist Movies"
    Then the Movie Guide API response status is 200
    And the Movie Guide "Heist Movies" has a sub-category path "Genres.Drama" containing movie "tt101"

  Scenario: Removing a movie from the Guide's own category also removes it from every native sub-category beneath it
    Given category "Sub Genres" exists under the Movie Guide "Heist Movies"
    And category "Sub Drama" exists under category "Sub Genres"
    And movie "tt101" exists with title "Movie One"
    And user "curator" with role "USER" adds movie "tt101" to category "Sub Drama" of the Movie Guide "Heist Movies"
    When user "curator" with role "USER" removes movie "tt101" from the Movie Guide "Heist Movies" scoped to category "Heist Movies"
    Then the Movie Guide API response status is 204
    And the Movie Guide "Heist Movies" movie list does not contain "tt101"

  Scenario: Removing a movie scoped to the Guide's own root never touches a subscribed category's original movies
    Given category "Genres" exists
    And user "curator" with role "USER" subscribes the Movie Guide "Heist Movies" to category "Genres"
    And movie "tt101" exists with title "Movie One"
    And user "admin" with role "MOVIES_ADMIN" adds movie "tt101" to category "Genres" of the Movie Guide "Heist Movies"
    When user "curator" with role "USER" removes movie "tt101" from the Movie Guide "Heist Movies" scoped to category "Heist Movies"
    Then the Movie Guide API response status is 204
    And category "Genres" still contains movie "tt101"

  Scenario: Deleting a Guide never destroys a category it only subscribed to
    Given category "Genres" exists
    And user "curator" with role "USER" subscribes the Movie Guide "Heist Movies" to category "Genres"
    When user "curator" with role "USER" deletes the Movie Guide "Heist Movies"
    Then the Movie Guide API response status is 204
    And category "Genres" still exists

  Scenario: A non-owner cannot delete someone else's Guide
    When user "intruder" with role "USER" deletes the Movie Guide "Heist Movies"
    Then the Movie Guide API response status is 403

  Scenario: A user only sees their own Guides in "mine"
    Given "other" creates a Movie Guide named "Other Guide"
    When "curator" requests their own Movie Guides through the API
    Then the "mine" response contains the category for "Heist Movies" but not for "Other Guide"
