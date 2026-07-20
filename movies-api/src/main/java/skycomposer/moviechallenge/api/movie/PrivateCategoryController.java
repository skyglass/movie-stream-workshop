package skycomposer.moviechallenge.api.movie;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.*;
import skycomposer.moviechallenge.api.movie.dto.CategoryDto;
import skycomposer.moviechallenge.api.movie.dto.MoveCategoryRequest;
import skycomposer.moviechallenge.api.movie.dto.SaveCategoryRequest;

import java.util.List;

// Private counterpart to CategoryController: manages the sub-categories inside a single watchlist's own sandbox
// (Users -> username -> Watchlists -> watchlist_name). Every endpoint is ownership-scoped inside
// PrivateCategoryService -- unlike /api/categories there is no public/permitAll read here.
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/private-categories")
public class PrivateCategoryController {
    private final PrivateCategoryService categories;

    @GetMapping("/subtree/{rootId}")
    public List<CategoryDto> subtree(@PathVariable long rootId, Authentication authentication) {
        return categories.subtree(rootId, authentication.getName(), isAdmin(authentication));
    }

    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping
    public CategoryDto create(@Valid @RequestBody SaveCategoryRequest request, Authentication authentication) {
        return categories.create(request, authentication.getName(), isAdmin(authentication));
    }

    @PutMapping("/{id}")
    public CategoryDto update(@PathVariable long id, @Valid @RequestBody SaveCategoryRequest request,
                               Authentication authentication) {
        return categories.update(id, request, authentication.getName(), isAdmin(authentication));
    }

    @PostMapping("/{id}/move")
    public CategoryDto move(@PathVariable long id, @Valid @RequestBody MoveCategoryRequest request,
                             Authentication authentication) {
        return categories.move(id, request, authentication.getName(), isAdmin(authentication));
    }

    @ResponseStatus(HttpStatus.NO_CONTENT)
    @DeleteMapping("/{id}")
    public void delete(@PathVariable long id, @RequestParam long parentId, Authentication authentication) {
        categories.delete(id, parentId, authentication.getName(), isAdmin(authentication));
    }

    private boolean isAdmin(Authentication authentication) {
        return authentication.getAuthorities().stream().map(GrantedAuthority::getAuthority)
                .anyMatch(authority -> authority.equals("ROLE_MOVIES_ADMIN"));
    }
}
