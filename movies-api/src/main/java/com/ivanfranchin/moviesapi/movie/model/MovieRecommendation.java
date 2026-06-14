package com.ivanfranchin.moviesapi.movie.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@Entity
@Table(name = "movie_recommendations")
@IdClass(MovieRecommendationId.class)
public class MovieRecommendation {

    @Id
    @Column(name = "user_id", nullable = false, updatable = false)
    private String username;

    @Id
    @Column(name = "movie_id", nullable = false, updatable = false)
    private String movieImdbId;

    public MovieRecommendation(String username, String movieImdbId) {
        this.username = username;
        this.movieImdbId = movieImdbId;
    }
}
