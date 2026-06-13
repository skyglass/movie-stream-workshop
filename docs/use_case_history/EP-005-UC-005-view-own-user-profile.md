# Epic EP-005: View Own User Profile

**Use Case ID:** view-own-user-profile  
**Use Case Name:** View Own User Profile  
**Primary Actor:** Authenticated User  
**Goal:** Synchronize and return the current user's local profile projection.  
**Status:** Simulated historical epic, implemented

## Short Description

The profile epic introduced `USER_EXTRA`, which stores username, email, and avatar seed derived from JWT claims. The
endpoint is deliberately limited to the current user's own profile.

## Acceptance Criteria

```gherkin
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
```
