package skycomposer.moviechallenge.api.movie;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

@Component
public class MoviePaging {

    private final int defaultPageSize;

    public MoviePaging(@Value("${MOVIES_PER_PAGE:50}") int defaultPageSize) {
        this.defaultPageSize = defaultPageSize > 0 ? defaultPageSize : 50;
    }

    public Pageable pageable(Integer page, Integer pageSize) {
        int resolvedPage = page == null || page < 1 ? 1 : page;
        int resolvedPageSize = pageSize == null || pageSize < 1 ? defaultPageSize : pageSize;
        return PageRequest.of(resolvedPage - 1, resolvedPageSize);
    }
}
