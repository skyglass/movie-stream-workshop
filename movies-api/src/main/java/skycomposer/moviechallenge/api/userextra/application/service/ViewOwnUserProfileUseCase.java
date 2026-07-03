package skycomposer.moviechallenge.api.userextra.application.service;

import skycomposer.moviechallenge.api.userextra.UserExtraService;
import skycomposer.moviechallenge.api.userextra.model.UserExtra;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class ViewOwnUserProfileUseCase {

    private final UserExtraService userExtraService;

    @Transactional
    public UserExtra viewOwnProfile(Jwt jwt) {
        return userExtraService.syncFromJwt(jwt);
    }
}
