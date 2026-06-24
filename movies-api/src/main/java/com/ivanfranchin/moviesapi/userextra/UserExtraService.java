package com.ivanfranchin.moviesapi.userextra;

import com.ivanfranchin.moviesapi.userextra.exception.UserExtraNotFoundException;
import com.ivanfranchin.moviesapi.userextra.model.UserExtra;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
@Service
public class UserExtraService {

    private final UserExtraRepository userExtraRepository;

    @Transactional(readOnly = true)
    public UserExtra validateAndGetUserExtra(String username) {
        return getUserExtra(username).orElseThrow(() -> new UserExtraNotFoundException(username));
    }

    @Transactional(readOnly = true)
    public Optional<UserExtra> getUserExtra(String username) {
        return userExtraRepository.findById(username);
    }

    @Transactional(readOnly = true)
    public List<UserExtra> getUsers() {
        return userExtraRepository.findAll(Sort.by(Sort.Direction.ASC, "username"));
    }

    @Transactional
    public UserExtra syncFromJwt(Jwt jwt) {
        String username = firstNonBlank(
                jwt.getClaimAsString("preferred_username"),
                jwt.getClaimAsString("username"),
                jwt.getSubject());
        String email = UserExtra.emailForUsername(username);

        UserExtra userExtra = getUserExtra(username).orElseGet(() -> new UserExtra(username, email));
        userExtra.setEmail(email);
        if (userExtra.getAvatar() == null || userExtra.getAvatar().isBlank()) {
            userExtra.setAvatar(username);
        }
        return saveUserExtra(userExtra);
    }

    @Transactional
    public UserExtra saveUserExtra(UserExtra userExtra) {
        return userExtraRepository.save(userExtra);
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        throw new IllegalArgumentException("No usable username found in JWT");
    }
}
