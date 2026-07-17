package skycomposer.moviechallenge.api.movie.dto;

import java.util.List;

public record CreateMovieGuideResponse(
        long guideCategoryId,
        List<String> failedImdbIds) {}
