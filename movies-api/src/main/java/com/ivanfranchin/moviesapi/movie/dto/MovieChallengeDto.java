package com.ivanfranchin.moviesapi.movie.dto;

public record MovieChallengeDto(MovieChallengeMovieDto movie1, MovieChallengeMovieDto movie2) {

    public record MovieChallengeMovieDto(String imdbId, String title, String poster) {
    }
}
