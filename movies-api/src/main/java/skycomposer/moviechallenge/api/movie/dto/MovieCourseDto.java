package skycomposer.moviechallenge.api.movie.dto;

import java.math.BigDecimal;
import java.util.List;

public record MovieCourseDto(
        long id, String title, String description, String creator, boolean owner,
        boolean applied, boolean expert, BigDecimal averageRating, int movieCount,
        List<CourseMovieDto> movies, List<CourseSuggestionDto> suggestedCourses) {

    public record CourseMovieDto(
            String imdbId, String title, String description, String year, String director,
            String writer, String genre, String poster, int watchOrder, Long linkedCourseId,
            String linkedCourseTitle, boolean liked, boolean disliked, BigDecimal rating) {}

    public record CourseSuggestionDto(long id, String title) {}
}
