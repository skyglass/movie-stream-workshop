package skycomposer.moviechallenge.api.movie.application.service;

import skycomposer.moviechallenge.api.movie.UserSettingsRepository;
import skycomposer.moviechallenge.api.movie.dto.MoviePageDto;
import skycomposer.moviechallenge.api.movie.dto.UsersRecommendedMoviesShareDto;
import skycomposer.moviechallenge.api.movie.exception.SharedRecommendedMoviesNotFoundException;
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

// Mirrors ShareMyFavoriteMoviesUseCase exactly, one level over: same public/private toggle mechanism
// (user_settings.is_my_recommended_movies_public), same "/my-recommended-movies/{encodedUsername}" share path
// shape, same encode/decode dance for arbitrary usernames.
@RequiredArgsConstructor
@Service
public class ShareUsersRecommendedMoviesUseCase {

    private static final String SHARE_PATH_PREFIX = "/my-recommended-movies/";

    private final UserSettingsRepository userSettingsRepository;
    private final UserExtraService userExtraService;
    private final ViewUsersRecommendedMoviesUseCase viewUsersRecommendedMovies;

    @Transactional
    public UsersRecommendedMoviesShareDto sharingStatus(Jwt jwt) {
        UserExtra userExtra = userExtraService.syncFromJwt(jwt);
        UserSettings settings = getOrCreateSettings(userExtra.getUsername());
        return toShareDto(settings);
    }

    @Transactional
    public UsersRecommendedMoviesShareDto shareUsersRecommendedMovies(Jwt jwt) {
        UserExtra userExtra = userExtraService.syncFromJwt(jwt);
        UserSettings settings = getOrCreateSettings(userExtra.getUsername());
        settings.setMyRecommendedMoviesPublic(true);
        return toShareDto(userSettingsRepository.saveAndFlush(settings));
    }

    @Transactional
    public UsersRecommendedMoviesShareDto makeUsersRecommendedMoviesPrivate(Jwt jwt) {
        UserExtra userExtra = userExtraService.syncFromJwt(jwt);
        UserSettings settings = getOrCreateSettings(userExtra.getUsername());
        settings.setMyRecommendedMoviesPublic(false);
        return toShareDto(userSettingsRepository.saveAndFlush(settings));
    }

    @Transactional(readOnly = true)
    public MoviePageDto viewSharedRecommendedMovies(String encodedUsername, String viewerUsername, Pageable pageable,
                                                     String filter, String year, List<Long> selectedCategories) {
        String username = decodeUsername(encodedUsername);
        if (!userSettingsRepository.existsByUsernameAndMyRecommendedMoviesPublicTrue(username)) {
            throw new SharedRecommendedMoviesNotFoundException(encodedUsername);
        }
        return viewUsersRecommendedMovies.viewUsersRecommendedMovies(username, viewerUsername, pageable, filter, year, selectedCategories);
    }

    private UserSettings getOrCreateSettings(String username) {
        return userSettingsRepository.findById(username)
                .orElseGet(() -> userSettingsRepository.saveAndFlush(new UserSettings(username)));
    }

    private UsersRecommendedMoviesShareDto toShareDto(UserSettings settings) {
        String encodedUsername = UriUtils.encodePathSegment(settings.getUsername(), StandardCharsets.UTF_8);
        return new UsersRecommendedMoviesShareDto(
                settings.isMyRecommendedMoviesPublic(),
                encodedUsername,
                SHARE_PATH_PREFIX + encodedUsername);
    }

    private String decodeUsername(String encodedUsername) {
        try {
            return URLDecoder.decode(encodedUsername, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException exception) {
            throw new SharedRecommendedMoviesNotFoundException(encodedUsername);
        }
    }
}
