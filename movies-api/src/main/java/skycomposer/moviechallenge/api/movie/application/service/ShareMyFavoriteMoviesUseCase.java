package skycomposer.moviechallenge.api.movie.application.service;

import skycomposer.moviechallenge.api.movie.UserSettingsRepository;
import skycomposer.moviechallenge.api.movie.dto.FavoriteMoviesShareDto;
import skycomposer.moviechallenge.api.movie.dto.MoviePageDto;
import skycomposer.moviechallenge.api.movie.exception.SharedFavoriteMoviesNotFoundException;
import skycomposer.moviechallenge.api.movie.model.UserSettings;
import skycomposer.moviechallenge.api.userextra.UserExtraService;
import skycomposer.moviechallenge.api.userextra.model.UserExtra;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriUtils;

@RequiredArgsConstructor
@Service
public class ShareMyFavoriteMoviesUseCase {

    private static final String SHARE_PATH_PREFIX = "/my-favorite-movies/";

    private final UserSettingsRepository userSettingsRepository;
    private final UserExtraService userExtraService;
    private final ViewFavoriteMoviesUseCase viewFavoriteMovies;
    private final ViewCategorySimilarMoviesUseCase viewCategorySimilarMovies;

    @Transactional
    public FavoriteMoviesShareDto sharingStatus(Jwt jwt) {
        UserExtra userExtra = userExtraService.syncFromJwt(jwt);
        UserSettings settings = getOrCreateSettings(userExtra.getUsername());
        return toShareDto(settings);
    }

    @Transactional
    public FavoriteMoviesShareDto shareMyFavoriteMovies(Jwt jwt) {
        UserExtra userExtra = userExtraService.syncFromJwt(jwt);
        UserSettings settings = getOrCreateSettings(userExtra.getUsername());
        settings.setMyFavoriteMoviesPublic(true);
        return toShareDto(userSettingsRepository.saveAndFlush(settings));
    }

    @Transactional
    public FavoriteMoviesShareDto makeMyFavoriteMoviesPrivate(Jwt jwt) {
        UserExtra userExtra = userExtraService.syncFromJwt(jwt);
        UserSettings settings = getOrCreateSettings(userExtra.getUsername());
        settings.setMyFavoriteMoviesPublic(false);
        return toShareDto(userSettingsRepository.saveAndFlush(settings));
    }

    @Transactional(readOnly = true)
    public MoviePageDto viewSharedFavoriteMovies(String encodedUsername, Pageable pageable) {
        return viewSharedFavoriteMovies(encodedUsername, pageable, null);
    }

    @Transactional(readOnly = true)
    public MoviePageDto viewSharedFavoriteMovies(String encodedUsername, Pageable pageable, String filter) {
        return viewSharedFavoriteMovies(encodedUsername, pageable, filter, null);
    }

    @Transactional(readOnly = true)
    public MoviePageDto viewSharedFavoriteMovies(String encodedUsername, Pageable pageable, String filter, String year) {
        return viewSharedFavoriteMovies(encodedUsername, pageable, filter, year, List.of());
    }

    @Transactional(readOnly = true)
    public MoviePageDto viewSharedFavoriteMovies(String encodedUsername, Pageable pageable, String filter, String year, List<Long> selectedCategories) {
        String username = decodeUsername(encodedUsername);
        if (!userSettingsRepository.existsByUsernameAndMyFavoriteMoviesPublicTrue(username)) {
            throw new SharedFavoriteMoviesNotFoundException(encodedUsername);
        }
        return viewFavoriteMovies.viewFavoriteMovies(username, pageable, filter, year, selectedCategories);
    }

    // Backs "Recommend Similar Movies" on a public shared-favorites page (favorite-movies.ts isPublicView):
    // username here is already plain/decoded (a @PathVariable, not the raw-URI "encodedUsername" the wildcard
    // list endpoint above works with), so this checks the same public-visibility boundary directly rather than
    // going through decodeUsername() again.
    @Transactional(readOnly = true)
    public MoviePageDto viewSharedSimilarToFavoriteMovies(String username, Pageable pageable, String filter, String year, List<Long> selectedCategories) {
        if (!userSettingsRepository.existsByUsernameAndMyFavoriteMoviesPublicTrue(username)) {
            throw new SharedFavoriteMoviesNotFoundException(username);
        }
        return viewCategorySimilarMovies.viewSimilarToFavorites(username, pageable, filter, year, selectedCategories);
    }

    private UserSettings getOrCreateSettings(String username) {
        return userSettingsRepository.findById(username)
                .orElseGet(() -> userSettingsRepository.saveAndFlush(new UserSettings(username)));
    }

    private FavoriteMoviesShareDto toShareDto(UserSettings settings) {
        String encodedUsername = UriUtils.encodePathSegment(settings.getUsername(), StandardCharsets.UTF_8);
        return new FavoriteMoviesShareDto(
                settings.isMyFavoriteMoviesPublic(),
                encodedUsername,
                SHARE_PATH_PREFIX + encodedUsername);
    }

    private String decodeUsername(String encodedUsername) {
        try {
            return URLDecoder.decode(encodedUsername, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException exception) {
            throw new SharedFavoriteMoviesNotFoundException(encodedUsername);
        }
    }
}
