delete from user_movie_rank;

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
            order by (
                cast(1 as numeric(12, 6))
                    + cast(9 as numeric(12, 6))
                        * (cast(wins + 2 as numeric(12, 6))
                            / cast(wins + losses + 4 as numeric(12, 6)))
                        * least(cast(1 as numeric(12, 6)),
                            cast(wins + losses as numeric(12, 6)) / cast(8 as numeric(12, 6)))
            ) desc,
            wins desc,
            (wins + losses) desc,
            movie_id asc
        ) as rank_position,
        cast(
            cast(1 as numeric(12, 6))
                + cast(9 as numeric(12, 6))
                    * (cast(wins + 2 as numeric(12, 6))
                        / cast(wins + losses + 4 as numeric(12, 6)))
                    * least(cast(1 as numeric(12, 6)),
                        cast(wins + losses as numeric(12, 6)) / cast(8 as numeric(12, 6)))
            as numeric(12, 6)
        ) as score,
        wins + losses as direct_comparisons,
        least(cast(1 as numeric(12, 6)),
            cast(wins + losses as numeric(12, 6)) / cast(8 as numeric(12, 6))) as confidence
    from movie_stats
)
select user_id, movie_id, rank_position, score, direct_comparisons, confidence
from ranked_movies;

alter table user_movie_rank
    add constraint chk_user_movie_rank_score_range check (score >= 1 and score <= 10);
