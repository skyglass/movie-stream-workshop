# Epic EP-006: View Registered Users

**Use Case ID:** view-registered-users  
**Use Case Name:** View Registered Users  
**Primary Actor:** Admin  
**Goal:** Allow an admin to inspect the registered user profile list while keeping it forbidden to regular users.  
**Status:** Simulated historical epic, implemented

## Short Description

This epic introduced the Admin menu and users list. The UI hides the Admin menu for non-admin users, but the API is the
source of truth and returns `403 Forbidden` unless the caller has `MOVIES_ADMIN`.

## Acceptance Criteria

```gherkin
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
```
