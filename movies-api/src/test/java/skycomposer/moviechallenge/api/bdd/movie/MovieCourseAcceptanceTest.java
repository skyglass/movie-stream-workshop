package skycomposer.moviechallenge.api.bdd.movie;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import skycomposer.moviechallenge.api.bdd.fixture.RestApiFixture;
import skycomposer.moviechallenge.api.movie.MovieCourseRepository;
import skycomposer.moviechallenge.api.movie.dto.AddCourseMovieRequest;
import skycomposer.moviechallenge.api.movie.dto.CreateMovieCourseRequest;
import skycomposer.moviechallenge.api.movie.dto.MovieCourseDto;

import java.util.Arrays;
import java.util.List;
import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

public class MovieCourseAcceptanceTest {
    private final MovieCourseRepository courses;
    private final RestApiFixture restApi;
    private final MockMvc mockMvc;
    private final JdbcTemplate jdbc;
    private MovieCourseDto course;

    public MovieCourseAcceptanceTest(MovieCourseRepository courses, RestApiFixture restApi, MockMvc mockMvc,
                                     JdbcTemplate jdbc) {
        this.courses = courses;
        this.restApi = restApi;
        this.mockMvc = mockMvc;
        this.jdbc = jdbc;
    }

    @Given("movie course {string} with title {string} and description {string} is created by {string}")
    public void courseCreated(String header, String title, String description, String creator) {
        course = courses.create(new CreateMovieCourseRequest(header, title, description), creator);
    }

    @When("regular user {string} requests Movie Courses through the API")
    public void listCourses(String username) throws Exception {
        restApi.record(mockMvc.perform(get("/api/movie-courses")
                .with(restApi.jwt(username, "USER"))).andReturn());
    }

    @When("an anonymous viewer requests Movie Journeys through the API")
    public void listJourneysAnonymously() throws Exception {
        restApi.record(mockMvc.perform(get("/api/movie-journeys")).andReturn());
    }

    @When("regular user {string} applies to the Movie Course through the API")
    public void apply(String username) throws Exception {
        restApi.record(mockMvc.perform(post("/api/movie-courses/{id}/applications", course.id())
                .with(restApi.jwt(username, "USER"))).andReturn());
    }

    @When("regular user {string} tries to edit the Movie Course through the API")
    public void edit(String username) throws Exception {
        restApi.record(mockMvc.perform(put("/api/movie-courses/{id}", course.id())
                .with(restApi.jwt(username, "USER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"header":"Changed","title":"Changed","description":"Changed"}
                        """)).andReturn());
    }

    @When("regular user {string} tries to delete the Movie Course through the API")
    public void deleteCourse(String username) throws Exception {
        restApi.record(mockMvc.perform(delete("/api/movie-courses/{id}", course.id())
                .with(restApi.jwt(username, "USER"))).andReturn());
    }

    @Then("the Movie Course API response status is {int}")
    public void status(int status) { restApi.assertStatus(status); }

    @Then("the Movie Course response contains header {string}, title {string}, and description {string}")
    public void responseContains(String header, String title, String description) throws Exception {
        String body = restApi.responseBody();
        assertTrue(body.contains("\"header\":\"" + header + "\""));
        assertTrue(body.contains("\"title\":\"" + title + "\""));
        assertTrue(body.contains("\"description\":\"" + description + "\""));
    }

    @Then("user {string} is enrolled in the Movie Course")
    public void enrolled(String username) { assertEquals(1, participantCount(username)); }

    @Then("user {string} is not enrolled in the Movie Course")
    public void notEnrolled(String username) { assertEquals(0, participantCount(username)); }

    @Given("the creator adds movie {string} to the Movie Course")
    public void addMovie(String movieId) {
        course = courses.addMovie(course.id(), new AddCourseMovieRequest(movieId, "Course focus", "Why it matters", 99, null),
                course.creator());
    }

    @Then("the Movie Course movies for {string} are {string} in sequence order")
    public void moviesInOrder(String username, String csv) {
        List<String> expected = Arrays.stream(csv.split(",")).map(String::trim).toList();
        List<MovieCourseDto.CourseMovieDto> movies = courses.find(course.id(), username).movies();
        assertEquals(expected, movies.stream().map(MovieCourseDto.CourseMovieDto::imdbId).toList());
        assertEquals(List.of(1, 2), movies.stream().map(MovieCourseDto.CourseMovieDto::watchOrder).toList());
    }

    @Then("course movie {string} is liked and course movie {string} is disliked for {string}")
    public void ratingStates(String likedId, String dislikedId, String username) {
        List<MovieCourseDto.CourseMovieDto> movies = courses.find(course.id(), username).movies();
        assertTrue(movies.stream().filter(movie -> movie.imdbId().equals(likedId)).findFirst().orElseThrow().liked());
        assertTrue(movies.stream().filter(movie -> movie.imdbId().equals(dislikedId)).findFirst().orElseThrow().disliked());
    }

    @Then("the Movie Journey has Your Average Rating {string} for {string}")
    public void personalAverageRating(String expected, String username) {
        MovieCourseDto journey = courses.findAll(username).stream()
                .filter(candidate -> candidate.id() == course.id())
                .findFirst()
                .orElseThrow();
        assertEquals(0, new BigDecimal(expected).compareTo(journey.averageRating()));
    }

    private int participantCount(String username) {
        return jdbc.queryForObject("select count(*) from user_movie_journey where user_id=? and movie_journey_id=?",
                Integer.class, username, course.id());
    }
}
