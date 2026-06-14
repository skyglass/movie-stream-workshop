# Epic EP-008: Change Own Avatar

**Use Case ID:** change-own-avatar  
**Use Case Name:** Change Own Avatar  
**Primary Actor:** Authenticated User  
**Goal:** Let an authenticated user change the avatar seed on their own local profile.  
**Status:** Implemented

## Short Description

This epic adds avatar mutation for `USER_EXTRA`. The profile endpoint is `/me`-scoped: username and email are still
synchronized from JWT claims, and the request body can only change the avatar seed string used by DiceBear to render an
SVG avatar.

## Acceptance Criteria

```gherkin
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
```
