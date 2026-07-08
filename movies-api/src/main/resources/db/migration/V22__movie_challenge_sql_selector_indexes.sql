create index idx_user_movie_rank_user_direct_comparisons
    on user_movie_rank (user_id, direct_comparisons, rank_position, movie_id);
