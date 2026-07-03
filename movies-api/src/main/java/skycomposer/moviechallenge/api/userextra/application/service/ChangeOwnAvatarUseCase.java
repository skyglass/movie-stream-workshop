package skycomposer.moviechallenge.api.userextra.application.service;

import skycomposer.moviechallenge.api.userextra.UserExtraService;
import skycomposer.moviechallenge.api.userextra.model.UserExtra;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class ChangeOwnAvatarUseCase {

    private final UserExtraService userExtraService;

    @Transactional
    public UserExtra changeAvatar(Jwt jwt, String avatar) {
        UserExtra userExtra = userExtraService.syncFromJwt(jwt);
        return changeAvatar(userExtra, avatar);
    }

    @Transactional
    public UserExtra changeAvatar(String username, String avatar) {
        String syntheticEmail = UserExtra.emailForUsername(username);
        UserExtra userExtra = userExtraService.getUserExtra(username)
                .orElseGet(() -> new UserExtra(username, syntheticEmail));
        userExtra.setEmail(syntheticEmail);
        return changeAvatar(userExtra, avatar);
    }

    private UserExtra changeAvatar(UserExtra userExtra, String avatar) {
        if (avatar == null || avatar.isBlank()) {
            throw new IllegalArgumentException("Avatar is required");
        }
        userExtra.setAvatar(avatar.trim());
        return userExtraService.saveUserExtra(userExtra);
    }
}
