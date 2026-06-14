package com.ivanfranchin.moviesapi.movie.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public class MovieChallengeUnavailableException extends RuntimeException {

    public MovieChallengeUnavailableException() {
        super("Movie challenge is not available for the selected pair");
    }
}
