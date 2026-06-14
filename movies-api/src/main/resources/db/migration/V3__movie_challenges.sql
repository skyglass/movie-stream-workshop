create table movie_user_votes (
    user_id varchar(255) not null references users (username) on delete cascade,
    movie_id varchar(32) not null references movies (imdb_id) on delete cascade,
    vote_count integer not null default 0,
    primary key (user_id, movie_id),
    constraint chk_movie_user_votes_vote_count_non_negative check (vote_count >= 0)
);

create index idx_movie_user_votes_movie_id on movie_user_votes (movie_id);

create table user_movie_challenge (
    user_id varchar(255) not null references users (username) on delete cascade,
    movie_id varchar(32) not null references movies (imdb_id) on delete cascade,
    challenge_count integer not null default 0,
    primary key (user_id, movie_id),
    constraint chk_user_movie_challenge_count_non_negative check (challenge_count >= 0)
);

create index idx_user_movie_challenge_movie_id on user_movie_challenge (movie_id);
create index idx_user_movie_challenge_user_count on user_movie_challenge (user_id, challenge_count);

create table user_movie_pair_challenge (
    user_id varchar(255) not null references users (username) on delete cascade,
    movie1_id varchar(32) not null references movies (imdb_id) on delete cascade,
    movie2_id varchar(32) not null references movies (imdb_id) on delete cascade,
    primary key (user_id, movie1_id, movie2_id),
    constraint chk_user_movie_pair_challenge_order check (movie1_id < movie2_id)
);

create index idx_user_movie_pair_challenge_movie1 on user_movie_pair_challenge (movie1_id);
create index idx_user_movie_pair_challenge_movie2 on user_movie_pair_challenge (movie2_id);
