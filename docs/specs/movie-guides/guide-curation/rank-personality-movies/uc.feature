Feature: rank-personality-movies

  Background:
    Given a Movie Personality "Robert De Niro Persona" exists, owned by "curator"
    And movie "tt301" exists with title "Movie A"
    And movie "tt302" exists with title "Movie B"
    And movie "tt303" exists with title "Movie C"
    And movies "tt301,tt302,tt303" are filed under the Movie Guide "Robert De Niro Persona"

  Scenario: The owner can submit a ranking for their Movie Personality
    When user "curator" with role "USER" ranks the Movie Personality "Robert De Niro Persona" with movies "tt302,tt301,tt303"
    Then the Movie Guide API response status is 200
    And the Movie Personality "Robert De Niro Persona" movie list is ordered "tt302,tt301,tt303"

  Scenario: Submitting a ranking allocates a ranking username derived from the personality's name
    When user "curator" with role "USER" ranks the Movie Personality "Robert De Niro Persona" with movies "tt301,tt302,tt303"
    Then the Movie Guide API response status is 200
    And the Movie Personality "Robert De Niro Persona" has ranking username "robert-de-niro-persona"

  Scenario: Re-submitting a ranking reuses the same ranking username
    Given user "curator" with role "USER" ranks the Movie Personality "Robert De Niro Persona" with movies "tt301,tt302,tt303"
    When user "curator" with role "USER" ranks the Movie Personality "Robert De Niro Persona" with movies "tt303,tt302,tt301"
    Then the Movie Guide API response status is 200
    And the Movie Personality "Robert De Niro Persona" has ranking username "robert-de-niro-persona"

  Scenario: A Movie Guide (not a Personality) cannot be ranked
    Given a Movie Guide "Heist Movies Guide" exists, owned by "curator"
    And movie "tt304" exists with title "Movie D"
    And movies "tt304" are filed under the Movie Guide "Heist Movies Guide"
    When user "curator" with role "USER" ranks the Movie Personality "Heist Movies Guide" with movies "tt304"
    Then the Movie Guide API response status is 409

  Scenario: A non-owner, non-curator cannot rank someone else's Movie Personality
    When user "intruder" with role "USER" ranks the Movie Personality "Robert De Niro Persona" with movies "tt301,tt302,tt303"
    Then the Movie Guide API response status is 403

  Scenario: A MOVIES_GUIDE curator can rank someone else's Movie Personality
    When user "helper" with role "MOVIES_GUIDE" ranks the Movie Personality "Robert De Niro Persona" with movies "tt301,tt302,tt303"
    Then the Movie Guide API response status is 200

  Scenario: The synthetic ranking user's public favorite movies match the submitted order
    When user "curator" with role "USER" ranks the Movie Personality "Robert De Niro Persona" with movies "tt303,tt301,tt302"
    Then the Movie Guide API response status is 200
    When anonymous viewer requests shared favorite movies for encoded username "robert-de-niro-persona"
    Then the movie list is ordered "tt303,tt301,tt302"

  Scenario: The Movie Personality's own movie grid shows the Personality's Rating for each ranked movie
    When user "curator" with role "USER" ranks the Movie Personality "Robert De Niro Persona" with movies "tt302,tt301,tt303"
    Then the Movie Guide API response status is 200
    When an anonymous viewer requests the Movie Personality "Robert De Niro Persona" movie results
    Then movie "tt302" has Personality's Rating rank 1
    And movie "tt301" has Personality's Rating rank 2
    And movie "tt303" has Personality's Rating rank 3

  Scenario: Renaming an already-ranked Personality renames its synthetic ranking user to match
    Given user "curator" with role "USER" ranks the Movie Personality "Robert De Niro Persona" with movies "tt301,tt302,tt303"
    When user "curator" with role "USER" renames the Movie Guide "Robert De Niro Persona" to "Robert De Niro"
    Then the Movie Guide API response status is 200
    And the Movie Personality "Robert De Niro Persona" has ranking username "robert-de-niro"
    When anonymous viewer requests shared favorite movies for encoded username "robert-de-niro"
    Then the movie list is ordered "tt301,tt302,tt303"
    When anonymous viewer requests shared favorite movies for encoded username "robert-de-niro-persona"
    Then the movie API response status is 404

  Scenario: Submitting only the loaded prefix preserves and rebuilds the unloaded suffix
    Given user "curator" with role "USER" ranks the Movie Personality "Robert De Niro Persona" with movies "tt301,tt302,tt303"
    When user "curator" with role "USER" ranks the Movie Personality "Robert De Niro Persona" with movies "tt302,tt301"
    Then the Movie Guide API response status is 200
    When anonymous viewer requests shared favorite movies for encoded username "robert-de-niro-persona"
    Then the movie list is ordered "tt302,tt301,tt303"
