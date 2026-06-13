Feature: view-registered-users

  Scenario: Admin can view registered users
    Given admin user "admin" is authenticated
    When the admin requests the registered users list
    Then the API response status is 200
    And the registered users list contains "user"

  Scenario: Regular user is forbidden from registered users list
    Given regular user "user" is authenticated
    When the regular user requests the registered users list
    Then the API response status is 403
