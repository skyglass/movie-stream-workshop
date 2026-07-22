create table personality_movie_rank (
    personality_id bigint not null references movie_guide (id) on delete cascade,
    movie_id varchar(32) not null references movies (imdb_id) on delete cascade,
    rank integer not null,
    primary key (personality_id, movie_id),
    constraint uq_personality_movie_rank_position unique (personality_id, rank),
    constraint chk_personality_movie_rank_positive check (rank > 0)
);

create index idx_personality_movie_rank_movie on personality_movie_rank (movie_id);
