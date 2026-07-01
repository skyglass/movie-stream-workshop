create index idx_user_movie_pair_challenge_pair_winner_user
    on user_movie_pair_challenge (movie1_id, movie2_id, movie1_wins, user_id);
