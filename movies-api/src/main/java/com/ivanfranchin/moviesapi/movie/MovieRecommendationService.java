package com.ivanfranchin.moviesapi.movie;

import com.ivanfranchin.moviesapi.movie.model.MovieRecommendation;
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
        return movieRecommendationRepository.existsByUsernameAndMovieImdbId(username, imdbId);
    }

    @Transactional(readOnly = true)
    public Set<String> recommendedMovieIds(String username) {
        return movieRecommendationRepository.findByUsername(username).stream()
                .map(MovieRecommendation::getMovieImdbId)
                .collect(Collectors.toSet());
    }

    @Transactional
    public void recommend(String username, String imdbId) {
        if (!isRecommended(username, imdbId)) {
            movieRecommendationRepository.save(new MovieRecommendation(username, imdbId));
        }
    }

    @Transactional
    public void unrecommend(String username, String imdbId) {
        movieRecommendationRepository.deleteByUsernameAndMovieImdbId(username, imdbId);
    }
}
