Feature: add-movie-comment

  Scenario: Admin user comments on a movie
    Given movie "tt0111161" exists with title "The Shawshank Redemption"
    When admin user "admin" comments "A careful rewatch still holds up" on movie "tt0111161" through the movie API
    Then the comment API response status is 201
    And commented movie "tt0111161" has comment "A careful rewatch still holds up" by "admin"

  Scenario: Regular user cannot comment on a movie
    Given movie "tt0111161" exists with title "The Shawshank Redemption"
    When regular user "user" tries to comment "A careful rewatch still holds up" on movie "tt0111161" through the movie API
    Then the comment API response status is 403

  Scenario: Blank comment is rejected
    Given movie "tt0111161" exists with title "The Shawshank Redemption"
    When user "user" tries to comment blank text on movie "tt0111161"
    Then the blank movie comment is rejected because text is required
