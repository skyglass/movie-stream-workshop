package skycomposer.moviechallenge.api.movie;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import skycomposer.moviechallenge.api.movie.dto.AddMoviesToCategoryRequest;
import skycomposer.moviechallenge.api.movie.dto.CategoryDto;
import skycomposer.moviechallenge.api.movie.dto.MoveCategoryRequest;
import skycomposer.moviechallenge.api.movie.dto.RecommendMovieRequest;
import skycomposer.moviechallenge.api.movie.dto.SaveCategoryRequest;
import skycomposer.moviechallenge.api.movie.dto.SaveMovieCategoriesRequest;

import java.util.List;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/categories")
public class CategoryController {
    private final CategoryService categories;

    @GetMapping
    public List<CategoryDto> tree() { return categories.tree(null); }

    @GetMapping("/movies/{movieId}")
    public List<CategoryDto> movieTree(@PathVariable String movieId) { return categories.tree(movieId); }

    @GetMapping("/subtree/{rootId}")
    public List<CategoryDto> subtree(@PathVariable long rootId, @RequestParam(required = false) List<Long> exclude) {
        return categories.subtree(rootId, exclude == null ? List.of() : exclude);
    }

    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping
    public CategoryDto create(@Valid @RequestBody SaveCategoryRequest request, Authentication authentication) {
        return categories.create(request, authentication.getName(), isAdminOrGuide(authentication));
    }

    @PutMapping("/{id}")
    public CategoryDto update(@PathVariable long id, @Valid @RequestBody SaveCategoryRequest request,
                               Authentication authentication) {
        return categories.update(id, request, authentication.getName(), isAdminOrGuide(authentication));
    }

    @PostMapping("/{id}/move")
    public CategoryDto move(@PathVariable long id, @Valid @RequestBody MoveCategoryRequest request,
                             Authentication authentication) {
        return categories.move(id, request, authentication.getName(), isAdminOrGuide(authentication));
    }

    @ResponseStatus(HttpStatus.NO_CONTENT)
    @DeleteMapping("/{id}")
    public void delete(@PathVariable long id, @RequestParam long parentId, Authentication authentication) {
        categories.delete(id, parentId, authentication.getName(), isAdminOrGuide(authentication));
    }

    @PutMapping("/movies/{movieId}")
    public List<CategoryDto> saveMovieCategories(@PathVariable String movieId,
                                                  @Valid @RequestBody SaveMovieCategoriesRequest request) {
        return categories.saveMovieCategories(movieId, request);
    }

    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PostMapping("/{categoryId}/movies-from-search")
    public void addMovieFromSearchToCategory(@PathVariable long categoryId,
                                              @Valid @RequestBody RecommendMovieRequest request) {
        categories.addMovieFromSearchToCategory(categoryId, request);
    }

    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PostMapping("/{categoryId}/movies")
    public void addMoviesToCategory(@PathVariable long categoryId,
                                     @Valid @RequestBody AddMoviesToCategoryRequest request) {
        categories.addMoviesToCategory(categoryId, request.imdbIds());
    }

    private boolean isAdminOrGuide(Authentication authentication) {
        return authentication.getAuthorities().stream().map(GrantedAuthority::getAuthority)
                .anyMatch(authority -> authority.equals("ROLE_MOVIES_ADMIN") || authority.equals("ROLE_MOVIES_GUIDE"));
    }
}
