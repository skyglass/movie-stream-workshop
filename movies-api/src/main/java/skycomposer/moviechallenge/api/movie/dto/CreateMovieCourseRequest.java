package skycomposer.moviechallenge.api.movie.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import skycomposer.moviechallenge.api.movie.model.MovieJourneyType;

public record CreateMovieCourseRequest(
        @NotBlank @Size(max = 200) String header,
        @NotBlank @Size(max = 200) String title,
        @Size(max = 10000) String description,
        MovieJourneyType type) {
    public CreateMovieCourseRequest {
        type = type == null ? MovieJourneyType.JOURNEY : type;
    }
}
