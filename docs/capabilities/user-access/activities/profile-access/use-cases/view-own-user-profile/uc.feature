Feature: view-own-user-profile

  Scenario: Regular user can view own profile
    Given regular user "user" is authenticated
    When the regular user requests own user profile
    Then the API response status is 200
    And the profile username is "user"

  Scenario: Regular user cannot update own profile through the locked endpoint
    Given regular user "user" is authenticated
    When the regular user tries to update own user profile
    Then the API response status is 403
