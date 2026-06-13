Feature: add-movie-comment

  Scenario: Authenticated user comments on a movie
    Given movie "tt0111161" exists with title "The Shawshank Redemption"
    When user "user" comments "A careful rewatch still holds up" on movie "tt0111161"
    Then movie "tt0111161" has a comment "A careful rewatch still holds up" by "user"

  Scenario: Blank comment is rejected
    Given movie "tt0111161" exists with title "The Shawshank Redemption"
    When user "user" tries to comment blank text on movie "tt0111161"
    Then the comment command is rejected because text is required
