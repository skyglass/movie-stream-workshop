package com.ivanfranchin.moviesapi.movie.dto;

import java.util.List;

public record MoviePageDto(List<MovieDto> movies, long totalCount) {
}
