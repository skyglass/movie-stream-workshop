package skycomposer.moviechallenge.api.userextra.application.service;

import skycomposer.moviechallenge.api.userextra.UserExtraService;
import skycomposer.moviechallenge.api.userextra.model.UserExtra;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class ViewRegisteredUsersUseCase {

    private final UserExtraService userExtraService;

    @Transactional(readOnly = true)
    public List<UserExtra> viewRegisteredUsers() {
        return userExtraService.getUsers();
    }
}
