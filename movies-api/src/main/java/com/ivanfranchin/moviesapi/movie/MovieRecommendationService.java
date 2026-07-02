package com.ivanfranchin.moviesapi.movie;

import com.ivanfranchin.moviesapi.movie.model.MovieRecommendation;
import com.ivanfranchin.moviesapi.movie.model.MovieRecommendationId;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class MovieRecommendationService {

    private final MovieRecommendationRepository movieRecommendationRepository;

    @Transactional(readOnly = true)
    public boolean isRecommended(String username, String imdbId) {
        return movieRecommendationRepository.existsByUsernameAndMovieImdbIdAndPositiveTrue(username, imdbId);
    }

    @Transactional(readOnly = true)
    public boolean isDisliked(String username, String imdbId) {
        return movieRecommendationRepository.existsByUsernameAndMovieImdbIdAndPositiveFalse(username, imdbId);
    }

    @Transactional(readOnly = true)
    public Set<String> recommendedMovieIds(String username) {
        return movieRecommendationRepository.findByUsernameAndPositiveTrue(username).stream()
                .map(MovieRecommendation::getMovieImdbId)
                .collect(Collectors.toSet());
    }

    @Transactional(readOnly = true)
    public Set<String> dislikedMovieIds(String username) {
        return movieRecommendationRepository.findByUsernameAndPositiveFalse(username).stream()
                .map(MovieRecommendation::getMovieImdbId)
                .collect(Collectors.toSet());
    }

    @Transactional
    public void recommend(String username, String imdbId) {
        saveRecommendation(username, imdbId, true);
    }

    @Transactional
    public void dislike(String username, String imdbId) {
        saveRecommendation(username, imdbId, false);
    }

    @Transactional
    public void unrecommend(String username, String imdbId) {
        movieRecommendationRepository.deleteByUsernameAndMovieImdbId(username, imdbId);
    }

    private void saveRecommendation(String username, String imdbId, boolean positive) {
        MovieRecommendation recommendation = movieRecommendationRepository
                .findById(new MovieRecommendationId(username, imdbId))
                .orElseGet(() -> new MovieRecommendation(username, imdbId));
        recommendation.setPositive(positive);
        movieRecommendationRepository.save(recommendation);
    }
}
