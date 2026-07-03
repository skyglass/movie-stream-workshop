package skycomposer.moviechallenge.api.userextra;

import skycomposer.moviechallenge.api.userextra.dto.UserExtraRequest;
import skycomposer.moviechallenge.api.userextra.model.UserExtra;
import skycomposer.moviechallenge.api.userextra.application.service.ChangeOwnAvatarUseCase;
import skycomposer.moviechallenge.api.userextra.application.service.ViewOwnUserProfileUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static skycomposer.moviechallenge.api.config.SwaggerConfig.BEARER_KEY_SECURITY_SCHEME;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/userextras")
public class UserExtraController {

    private final ViewOwnUserProfileUseCase viewOwnUserProfile;
    private final ChangeOwnAvatarUseCase changeOwnAvatar;

    @Operation(security = {@SecurityRequirement(name = BEARER_KEY_SECURITY_SCHEME)})
    @GetMapping("/me")
    public UserExtra getUserExtra(@AuthenticationPrincipal Jwt jwt) {
        return viewOwnUserProfile.viewOwnProfile(jwt);
    }

    @Operation(security = {@SecurityRequirement(name = BEARER_KEY_SECURITY_SCHEME)})
    @PostMapping("/me")
    public UserExtra saveUserExtra(@Valid @RequestBody UserExtraRequest updateUserExtraRequest,
                                   @AuthenticationPrincipal Jwt jwt) {
        return changeOwnAvatar.changeAvatar(jwt, updateUserExtraRequest.avatar());
    }
}
