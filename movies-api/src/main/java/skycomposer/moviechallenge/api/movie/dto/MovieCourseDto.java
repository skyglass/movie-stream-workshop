package skycomposer.moviechallenge.api.movie.dto;

import java.math.BigDecimal;
import java.util.List;
import skycomposer.moviechallenge.api.movie.model.MovieJourneyType;

public record MovieCourseDto(
        long id, String header, String title, String description, MovieJourneyType type, String typeDescription,
        String creator, boolean owner,
        boolean applied, boolean expert, BigDecimal averageRating, int movieCount,
        List<CourseMovieDto> movies, List<CourseSuggestionDto> suggestedCourses) {

    public record CourseMovieDto(
            String imdbId, String title, String header, String description, String year, String director,
            String writer, String genre, String poster, int watchOrder, Long linkedCourseId,
            String linkedCourseTitle, boolean liked, boolean disliked, Integer rankPosition, BigDecimal rating) {}

    public record CourseSuggestionDto(long id, String title) {}
}
