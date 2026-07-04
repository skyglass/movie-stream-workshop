create table user_movie_winner_loser (
    user_id varchar(255) not null references users (username) on delete cascade,
    winner_id varchar(32) not null references movies (imdb_id) on delete cascade,
    loser_id varchar(32) not null references movies (imdb_id) on delete cascade,
    primary key (user_id, winner_id, loser_id),
    constraint chk_user_movie_winner_loser_different check (winner_id <> loser_id)
);

create index idx_user_movie_winner_loser_loser on user_movie_winner_loser (user_id, loser_id);
create index idx_user_movie_winner_loser_winner on user_movie_winner_loser (winner_id);
create index idx_user_movie_winner_loser_pair_user on user_movie_winner_loser (winner_id, loser_id, user_id);

insert into user_movie_winner_loser (user_id, winner_id, loser_id)
select closure_result.user_id, closure_result.winner_id, closure_result.loser_id
from user_movie_winner_loser_all closure_result
where not exists (
    select 1
    from user_movie_winner_loser_all first_leg
    join user_movie_winner_loser_all second_leg
        on second_leg.user_id = first_leg.user_id
        and second_leg.winner_id = first_leg.loser_id
    where first_leg.user_id = closure_result.user_id
        and first_leg.winner_id = closure_result.winner_id
        and second_leg.loser_id = closure_result.loser_id
);
