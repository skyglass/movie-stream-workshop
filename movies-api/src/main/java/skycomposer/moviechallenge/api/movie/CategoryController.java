package skycomposer.moviechallenge.api.movie;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import skycomposer.moviechallenge.api.movie.dto.CategoryDto;
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

    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping
    public CategoryDto create(@Valid @RequestBody SaveCategoryRequest request) { return categories.create(request); }

    @PutMapping("/{id}")
    public CategoryDto update(@PathVariable long id, @Valid @RequestBody SaveCategoryRequest request) {
        return categories.update(id, request);
    }

    @ResponseStatus(HttpStatus.NO_CONTENT)
    @DeleteMapping("/{id}")
    public void delete(@PathVariable long id) { categories.delete(id); }

    @PutMapping("/movies/{movieId}")
    public List<CategoryDto> saveMovieCategories(@PathVariable String movieId,
                                                  @Valid @RequestBody SaveMovieCategoriesRequest request) {
        return categories.saveMovieCategories(movieId, request);
    }
}
