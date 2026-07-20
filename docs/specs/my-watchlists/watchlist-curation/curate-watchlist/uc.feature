Feature: curate-watchlist

  Background:
    Given "curator" creates a Watchlist named "Weekend Picks"

  Scenario: A regular user can create a private Watchlist and becomes its owner
    Then the Watchlist API response status is 201
    And the Watchlist "Weekend Picks" is owned by "curator"
    And the Watchlist "Weekend Picks" is anchored under "curator" in the private tree

  Scenario: Creating a Watchlist with a duplicate name for the same owner is rejected
    When "curator" creates a Watchlist named "Weekend Picks"
    Then the Watchlist API response status is 409

  Scenario: A different owner can reuse the same Watchlist name
    When "other" creates a Watchlist named "Weekend Picks"
    Then the Watchlist API response status is 201

  Scenario: A non-owner cannot read someone else's Watchlist
    When user "intruder" with role "USER" requests the Watchlist "Weekend Picks" through the API
    Then the Watchlist API response status is 403

  Scenario: The owner can subscribe their Watchlist to an existing public category
    Given category "New 2026" exists
    When user "curator" with role "USER" subscribes the Watchlist "Weekend Picks" to category "New 2026"
    Then the Watchlist API response status is 200
    And the Watchlist "Weekend Picks" is subscribed to category "New 2026"
    And category "New 2026" still has no other parent

  Scenario: A non-owner cannot subscribe someone else's Watchlist
    Given category "New 2026" exists
    When user "intruder" with role "USER" subscribes the Watchlist "Weekend Picks" to category "New 2026"
    Then the Watchlist API response status is 403

  Scenario: Unsubscribing removes a category from a Watchlist's subscriptions
    Given category "New 2026" exists
    And user "curator" with role "USER" subscribes the Watchlist "Weekend Picks" to category "New 2026"
    When user "curator" with role "USER" submits an empty subscription list for the Watchlist "Weekend Picks"
    Then the Watchlist API response status is 200
    And the Watchlist "Weekend Picks" is not subscribed to category "New 2026"

  Scenario: The owner can create a private sub-category inside their Watchlist
    When user "curator" with role "USER" creates private category "Cozy Nights" under the Watchlist "Weekend Picks"
    Then the Watchlist API response status is 201

  Scenario: A non-owner cannot create a private sub-category inside someone else's Watchlist
    When user "intruder" with role "USER" creates private category "Cozy Nights" under the Watchlist "Weekend Picks"
    Then the Watchlist API response status is 403

  Scenario: The owner can add an existing movie directly to their Watchlist's flat top level
    Given movie "tt101" exists with title "Movie One"
    When user "curator" with role "USER" adds movie "tt101" to the Watchlist "Weekend Picks"
    Then the Watchlist API response status is 204
    And the Watchlist "Weekend Picks" default movie list contains "tt101"

  Scenario: The owner can add a movie into a private sub-category of their Watchlist
    Given user "curator" with role "USER" creates private category "Cozy Nights" under the Watchlist "Weekend Picks"
    And movie "tt101" exists with title "Movie One"
    When user "curator" with role "USER" adds movie "tt101" to private category "Cozy Nights" of the Watchlist "Weekend Picks"
    Then the Watchlist API response status is 204
    And the Watchlist "Weekend Picks" default movie list contains "tt101"
    And the Watchlist "Weekend Picks" scoped to private category "Cozy Nights" contains movie "tt101"

  Scenario: A subscribed public category's movies appear in the Watchlist's default view without touching the public category
    Given category "New 2026" exists
    And movie "tt202" exists with title "Fresh Release"
    And movie "tt202" is filed under category "New 2026"
    And user "curator" with role "USER" subscribes the Watchlist "Weekend Picks" to category "New 2026"
    Then the Watchlist "Weekend Picks" default movie list contains "tt202"
    And category "New 2026" still has no other parent

  Scenario: Removing a movie from the Watchlist never touches the public category tree
    Given category "New 2026" exists
    And movie "tt202" exists with title "Fresh Release"
    And movie "tt202" is filed under category "New 2026"
    And user "curator" with role "USER" subscribes the Watchlist "Weekend Picks" to category "New 2026"
    And movie "tt101" exists with title "Movie One"
    And user "curator" with role "USER" adds movie "tt101" to the Watchlist "Weekend Picks"
    When user "curator" with role "USER" removes movie "tt101" from the Watchlist "Weekend Picks"
    Then the Watchlist API response status is 204
    And the Watchlist "Weekend Picks" default movie list does not contain "tt101"
    And the Watchlist "Weekend Picks" default movie list contains "tt202"

  Scenario: Deleting a Watchlist removes its private categories and movie assignments
    Given user "curator" with role "USER" creates private category "Cozy Nights" under the Watchlist "Weekend Picks"
    When user "curator" with role "USER" deletes the Watchlist "Weekend Picks"
    Then the Watchlist API response status is 204
    And the private category "Cozy Nights" no longer exists

  Scenario: A non-owner cannot delete someone else's Watchlist
    When user "intruder" with role "USER" deletes the Watchlist "Weekend Picks"
    Then the Watchlist API response status is 403

  Scenario: A user only sees their own Watchlists in "mine"
    Given "other" creates a Watchlist named "Other List"
    When "curator" requests their own Watchlists through the API
    Then the "mine" response contains the Watchlist "Weekend Picks" but not the Watchlist "Other List"
