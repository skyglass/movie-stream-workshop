Feature: change-own-avatar

  Scenario: Regular user can change own avatar
    Given regular user "user" is authenticated
    When the regular user changes own avatar seed to "user527"
    Then own profile avatar seed is "user527"

  Scenario: Blank avatar seed is rejected
    Given regular user "user" is authenticated
    When the regular user tries to change own avatar seed to blank
    Then the avatar change is rejected because avatar seed is required

  Scenario: Anonymous caller cannot change own avatar
    When an anonymous caller tries to change own avatar
    Then the user access API response status is 401
