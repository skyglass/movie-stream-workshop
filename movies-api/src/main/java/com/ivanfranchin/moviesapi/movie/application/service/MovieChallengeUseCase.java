package com.ivanfranchin.moviesapi.movie.application.service;

import com.ivanfranchin.moviesapi.movie.MovieChallengeRepository;
import com.ivanfranchin.moviesapi.movie.dto.MovieChallengeDto;
import com.ivanfranchin.moviesapi.movie.exception.MovieChallengeUnavailableException;
import com.ivanfranchin.moviesapi.userextra.UserExtraService;
import com.ivanfranchin.moviesapi.userextra.model.UserExtra;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class MovieChallengeUseCase {

    private final MovieChallengeRepository movieChallengeRepository;
    private final UserExtraService userExtraService;

    @Transactional
    public Optional<MovieChallengeDto> nextChallenge(Jwt jwt) {
        UserExtra userExtra = userExtraService.syncFromJwt(jwt);
        return nextChallenge(userExtra.getUsername());
    }

    @Transactional(readOnly = true)
    public Optional<MovieChallengeDto> nextChallenge(String username) {
        return movieChallengeRepository.findNextChallenge(username);
    }

    @Transactional
    public void selectMovie(Jwt jwt, String movie1Id, String movie2Id, String selectedMovieId) {
        UserExtra userExtra = userExtraService.syncFromJwt(jwt);
        selectMovie(userExtra.getUsername(), movie1Id, movie2Id, selectedMovieId);
    }

    @Transactional
    public void selectMovie(String username, String movie1Id, String movie2Id, String selectedMovieId) {
        if (!selectedMovieId.equals(movie1Id) && !selectedMovieId.equals(movie2Id)) {
            throw new IllegalArgumentException("Selected movie must be one movie from the challenge pair");
        }

        String loserId = selectedMovieId.equals(movie1Id) ? movie2Id : movie1Id;
        if (!movieChallengeRepository.canRecordWinnerLoser(username, selectedMovieId, loserId)) {
            throw new MovieChallengeUnavailableException();
        }

        movieChallengeRepository.insertWinnerLoserClosure(username, selectedMovieId, loserId);
        movieChallengeRepository.incrementChallengeCount(username, movie1Id);
        movieChallengeRepository.incrementChallengeCount(username, movie2Id);
    }
}
