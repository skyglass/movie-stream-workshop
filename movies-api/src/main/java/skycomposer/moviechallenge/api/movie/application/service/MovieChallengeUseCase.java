package skycomposer.moviechallenge.api.movie.application.service;

import skycomposer.moviechallenge.api.movie.MovieChallengeRepository;
import skycomposer.moviechallenge.api.movie.dto.MovieChallengeDto;
import skycomposer.moviechallenge.api.movie.dto.SelectMovieChallengeRequest;
import skycomposer.moviechallenge.api.movie.dto.SuggestedMovieChallengeDto;
import skycomposer.moviechallenge.api.movie.exception.MovieChallengeUnavailableException;
import skycomposer.moviechallenge.api.userextra.UserExtraService;
import skycomposer.moviechallenge.api.userextra.model.UserExtra;
import java.util.List;
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

    @Transactional(readOnly = true)
    public List<SuggestedMovieChallengeDto> suggestedChallenges(Jwt jwt, boolean higherRankedFirst,
                                                               boolean boostHigherRanks, boolean moreInterestingFirst) {
        UserExtra userExtra = userExtraService.syncFromJwt(jwt);
        return suggestedChallenges(userExtra.getUsername(), higherRankedFirst, boostHigherRanks, moreInterestingFirst);
    }

    @Transactional(readOnly = true)
    public List<SuggestedMovieChallengeDto> suggestedChallenges(String username, boolean higherRankedFirst,
                                                               boolean boostHigherRanks, boolean moreInterestingFirst) {
        return movieChallengeRepository.findSuggestedChallenges(username, higherRankedFirst, boostHigherRanks,
                moreInterestingFirst);
    }

    @Transactional
    public void selectMovie(Jwt jwt, String movie1Id, String movie2Id, String selectedMovieId) {
        UserExtra userExtra = userExtraService.syncFromJwt(jwt);
        selectMovie(userExtra.getUsername(), movie1Id, movie2Id, selectedMovieId);
    }

    @Transactional
    public void selectMovie(String username, String movie1Id, String movie2Id, String selectedMovieId) {
        selectMovieWithoutRankRebuild(username, movie1Id, movie2Id, selectedMovieId);
        movieChallengeRepository.rebuildUserMovieRanks(username);
    }

    @Transactional
    public void selectMovies(Jwt jwt, List<SelectMovieChallengeRequest> selections) {
        UserExtra userExtra = userExtraService.syncFromJwt(jwt);
        selectMovies(userExtra.getUsername(), selections);
    }

    @Transactional
    public void selectMovies(String username, List<SelectMovieChallengeRequest> selections) {
        selections.forEach(selection -> selectMovieWithoutRankRebuild(
                username,
                selection.movie1Id(),
                selection.movie2Id(),
                selection.selectedMovieId()));
        movieChallengeRepository.rebuildUserMovieRanks(username);
    }

    private void selectMovieWithoutRankRebuild(String username,
                                               String movie1Id,
                                               String movie2Id,
                                               String selectedMovieId) {
        if (!selectedMovieId.equals(movie1Id) && !selectedMovieId.equals(movie2Id)) {
            throw new IllegalArgumentException("Selected movie must be one movie from the challenge pair");
        }

        String loserId = selectedMovieId.equals(movie1Id) ? movie2Id : movie1Id;
        if (!movieChallengeRepository.canRecordWinnerLoser(username, selectedMovieId, loserId)) {
            throw new MovieChallengeUnavailableException();
        }

        movieChallengeRepository.insertDirectWinnerLoser(username, selectedMovieId, loserId);
    }
}
