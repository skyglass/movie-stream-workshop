create table user_movie_winner_loser (
    user_id varchar(255) not null references users (username) on delete cascade,
    winner_id varchar(32) not null references movies (imdb_id) on delete cascade,
    loser_id varchar(32) not null references movies (imdb_id) on delete cascade,
    primary key (user_id, winner_id, loser_id),
    constraint chk_user_movie_winner_loser_different check (winner_id <> loser_id)
);

create index idx_user_movie_winner_loser_loser on user_movie_winner_loser (user_id, loser_id);
create index idx_user_movie_winner_loser_winner on user_movie_winner_loser (winner_id);

create table user_movie_winner_loser_all (
    user_id varchar(255) not null references users (username) on delete cascade,
    winner_id varchar(32) not null references movies (imdb_id) on delete cascade,
    loser_id varchar(32) not null references movies (imdb_id) on delete cascade,
    primary key (user_id, winner_id, loser_id),
    constraint chk_user_movie_winner_loser_all_different check (winner_id <> loser_id)
);

create index idx_user_movie_winner_loser_all_loser on user_movie_winner_loser_all (user_id, loser_id);
create index idx_user_movie_winner_loser_all_winner on user_movie_winner_loser_all (winner_id);
create index idx_user_movie_winner_loser_all_pair_user on user_movie_winner_loser_all (winner_id, loser_id, user_id);

insert into user_movie_winner_loser (user_id, winner_id, loser_id)
select pair_challenge.user_id,
    case when pair_challenge.movie1_wins then pair_challenge.movie1_id else pair_challenge.movie2_id end,
    case when pair_challenge.movie1_wins then pair_challenge.movie2_id else pair_challenge.movie1_id end
from user_movie_pair_challenge pair_challenge
where not exists (
    select 1
    from user_movie_winner_loser winner_loser
    where winner_loser.user_id = pair_challenge.user_id
        and winner_loser.winner_id = case when pair_challenge.movie1_wins then pair_challenge.movie1_id else pair_challenge.movie2_id end
        and winner_loser.loser_id = case when pair_challenge.movie1_wins then pair_challenge.movie2_id else pair_challenge.movie1_id end
);

insert into user_movie_winner_loser_all (user_id, winner_id, loser_id)
select winner_loser.user_id, winner_loser.winner_id, winner_loser.loser_id
from user_movie_winner_loser winner_loser
where not exists (
    select 1
    from user_movie_winner_loser_all existing
    where existing.user_id = winner_loser.user_id
        and existing.winner_id = winner_loser.winner_id
        and existing.loser_id = winner_loser.loser_id
);
