package com.ivanfranchin.moviesapi.userextra.application.service;

import com.ivanfranchin.moviesapi.userextra.UserExtraService;
import com.ivanfranchin.moviesapi.userextra.model.UserExtra;
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

    @Transactional
    public UserExtra viewOwnProfile(String username, String email) {
        UserExtra userExtra = userExtraService.getUserExtra(username)
                .orElseGet(() -> new UserExtra(username, email));
        userExtra.setEmail(email);
        if (userExtra.getAvatar() == null || userExtra.getAvatar().isBlank()) {
            userExtra.setAvatar(username);
        }
        return userExtraService.saveUserExtra(userExtra);
    }
}
