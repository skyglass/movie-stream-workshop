Feature: view-own-user-profile

  Scenario: Regular user can view own profile
    Given regular user "user" is authenticated
    When the regular user requests own user profile
    Then the user access API response status is 200
    And own profile username is "user"
    And own profile email is "user@example.com"
