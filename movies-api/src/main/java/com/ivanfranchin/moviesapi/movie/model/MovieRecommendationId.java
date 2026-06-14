package com.ivanfranchin.moviesapi.movie.model;

import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MovieRecommendationId implements Serializable {

    private String username;
    private String movieImdbId;
}
