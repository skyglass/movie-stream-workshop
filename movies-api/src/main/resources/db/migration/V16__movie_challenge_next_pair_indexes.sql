create index idx_movie_recommendations_user_positive_movie
    on movie_recommendations (user_id, positive, movie_id);
