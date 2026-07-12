Feature: movie-courses

  Background:
    Given movie course "Film Language" with title "Learn the grammar of cinema" and description "A guided introduction" is created by "teacher"

  Scenario: A regular user can list all Movie Courses
    When regular user "student" requests Movie Courses through the API
    Then the Movie Course API response status is 200
    And the Movie Course response contains header "Film Language", title "Learn the grammar of cinema", and description "A guided introduction"

  Scenario: An anonymous viewer can list Movie Journeys
    When an anonymous viewer requests Movie Journeys through the API
    Then the Movie Course API response status is 200
    And the Movie Course response contains header "Film Language", title "Learn the grammar of cinema", and description "A guided introduction"

  Scenario: A student can apply to a Movie Course
    When regular user "student" applies to the Movie Course through the API
    Then the Movie Course API response status is 200
    And user "student" is enrolled in the Movie Course

  Scenario: A course creator cannot apply to their own Movie Course
    When regular user "teacher" applies to the Movie Course through the API
    Then the Movie Course API response status is 403
    And user "teacher" is not enrolled in the Movie Course

  Scenario: Only the creator can edit or delete a Movie Course
    When regular user "student" tries to edit the Movie Course through the API
    Then the Movie Course API response status is 403
    When regular user "student" tries to delete the Movie Course through the API
    Then the Movie Course API response status is 403

  Scenario: Course movies retain creator sequence and shared rating state
    Given movie "tt101" exists with title "Movie One"
    And movie "tt102" exists with title "Movie Two"
    And the creator adds movie "tt101" to the Movie Course
    And the creator adds movie "tt102" to the Movie Course
    And movie "tt101" is already recommended by "student"
    And movie "tt102" is already disliked by "student"
    When regular user "student" applies to the Movie Course through the API
    Then the Movie Course movies for "student" are "tt101,tt102" in sequence order
    And course movie "tt101" is liked and course movie "tt102" is disliked for "student"

  Scenario: A journey shows only the current explorer's average rating
    Given movie "tt101" exists with title "Movie One"
    And movie "tt102" exists with title "Movie Two"
    And the creator adds movie "tt101" to the Movie Course
    And movie "tt101" has rank 1, 4 direct comparisons, mu "0.5", and sigma "0.5" for "student"
    And movie "tt102" has rank 2, 4 direct comparisons, mu "-0.5", and sigma "0.5" for "student"
    And movie "tt101" has rank 2, 4 direct comparisons, mu "-0.5", and sigma "0.5" for "other-user"
    And movie "tt102" has rank 1, 4 direct comparisons, mu "0.5", and sigma "0.5" for "other-user"
    Then the Movie Journey has Your Average Rating "10.00" for "student"
