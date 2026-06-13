package com.ivanfranchin.moviesapi.movie.application;

import com.ivanfranchin.moviesapi.movie.MovieService;
import com.ivanfranchin.moviesapi.movie.dto.MovieDto;
import com.ivanfranchin.moviesapi.movie.mapper.MovieDtoMapper;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class ViewMovieCatalogUseCase {

    private final MovieService movieService;
    private final MovieDtoMapper movieMapper;

    @Transactional(readOnly = true)
    public List<MovieDto> viewCatalog() {
        return movieService.getMovies().stream()
                .map(movieMapper::toMovieDto)
                .toList();
    }
}
