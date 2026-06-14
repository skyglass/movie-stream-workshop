package com.ivanfranchin.moviesapi.movie.dto;

import jakarta.validation.constraints.NotBlank;

public record SelectMovieChallengeRequest(
        @NotBlank String movie1Id,
        @NotBlank String movie2Id,
        @NotBlank String selectedMovieId) {
}
