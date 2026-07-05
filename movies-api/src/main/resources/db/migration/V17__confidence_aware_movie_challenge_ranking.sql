create table user_movie_challenge_vote (
    user_id varchar(255) not null references users (username) on delete cascade,
    winner_id varchar(32) not null references movies (imdb_id) on delete cascade,
    loser_id varchar(32) not null references movies (imdb_id) on delete cascade,
    primary key (user_id, winner_id, loser_id),
    constraint chk_user_movie_challenge_vote_different check (winner_id <> loser_id)
);

create index idx_user_movie_challenge_vote_winner on user_movie_challenge_vote (user_id, winner_id);
create index idx_user_movie_challenge_vote_loser on user_movie_challenge_vote (user_id, loser_id);
create index idx_user_movie_challenge_vote_pair_user on user_movie_challenge_vote (winner_id, loser_id, user_id);

create table user_movie_rank (
    user_id varchar(255) not null references users (username) on delete cascade,
    movie_id varchar(32) not null references movies (imdb_id) on delete cascade,
    rank_position integer not null,
    score numeric(12, 6) not null,
    direct_comparisons integer not null default 0,
    confidence numeric(12, 6) not null default 0,
    primary key (user_id, movie_id),
    constraint uq_user_movie_rank_position unique (user_id, rank_position),
    constraint chk_user_movie_rank_position_positive check (rank_position > 0),
    constraint chk_user_movie_rank_direct_comparisons_non_negative check (direct_comparisons >= 0),
    constraint chk_user_movie_rank_confidence_range check (confidence >= 0 and confidence <= 1)
);

create index idx_user_movie_rank_user_position on user_movie_rank (user_id, rank_position);
create index idx_user_movie_rank_movie on user_movie_rank (movie_id);
create index idx_user_movie_rank_user_score on user_movie_rank (user_id, score desc);

insert into user_movie_challenge_vote (user_id, winner_id, loser_id)
select direct_result.user_id, direct_result.winner_id, direct_result.loser_id
from user_movie_winner_loser direct_result;

insert into user_movie_rank (user_id, movie_id, rank_position, score, direct_comparisons, confidence)
with movie_stats as (
    select user_id,
        movie_id,
        sum(wins) as wins,
        sum(losses) as losses
    from (
        select user_id, winner_id as movie_id, 1 as wins, 0 as losses
        from user_movie_challenge_vote
        union all
        select user_id, loser_id as movie_id, 0 as wins, 1 as losses
        from user_movie_challenge_vote
    ) vote_result
    group by user_id, movie_id
),
ranked_movies as (
    select user_id,
        movie_id,
        row_number() over (
            partition by user_id
            order by (wins - losses) desc, wins desc, (wins + losses) desc, movie_id asc
        ) as rank_position,
        cast(wins - losses as numeric(12, 6)) as score,
        wins + losses as direct_comparisons,
        least(cast(1 as numeric(12, 6)), cast(wins + losses as numeric(12, 6)) / cast(4 as numeric(12, 6))) as confidence
    from movie_stats
)
select user_id, movie_id, rank_position, score, direct_comparisons, confidence
from ranked_movies;

create view user_movie_rating as
select user_id,
    movie_id,
    rank_position,
    score,
    direct_comparisons,
    confidence,
    case
        when count(1) over (partition by user_id) = 1 then cast(10 as numeric(4, 2))
        else cast(round(
            cast(10 as numeric(12, 6))
                - (cast(9 as numeric(12, 6)) * cast(rank_position - 1 as numeric(12, 6)))
                    / cast(count(1) over (partition by user_id) - 1 as numeric(12, 6)),
            2
        ) as numeric(4, 2))
    end as rating
from user_movie_rank;
