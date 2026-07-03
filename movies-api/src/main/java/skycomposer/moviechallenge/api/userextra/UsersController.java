package skycomposer.moviechallenge.api.userextra;

import skycomposer.moviechallenge.api.userextra.model.UserExtra;
import skycomposer.moviechallenge.api.userextra.application.service.ViewRegisteredUsersUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static skycomposer.moviechallenge.api.config.SwaggerConfig.BEARER_KEY_SECURITY_SCHEME;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/users")
public class UsersController {

    private final ViewRegisteredUsersUseCase viewRegisteredUsers;

    @Operation(security = {@SecurityRequirement(name = BEARER_KEY_SECURITY_SCHEME)})
    @GetMapping
    public List<UserExtra> getUsers() {
        return viewRegisteredUsers.viewRegisteredUsers();
    }
}
