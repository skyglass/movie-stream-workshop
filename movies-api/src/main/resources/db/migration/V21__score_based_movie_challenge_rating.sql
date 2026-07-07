drop view if exists user_movie_rating;

create view user_movie_rating as
select
    t.user_id,
    t.movie_id,
    t.rank_position,
    t.score,
    t.direct_comparisons,
    t.confidence,
    case
        when t.min_score = t.max_score then cast(10.00 as numeric(4,2))
        else cast(
                round(
                        1
                            + 9 * (
                            cast(t.score - t.min_score as numeric(12,6))
                                / cast(t.max_score - t.min_score as numeric(12,6))
                            ),
                        2
                ) as numeric(4,2)
             )
        end as rating
from (
         select
             user_id,
             movie_id,
             rank_position,
             score,
             direct_comparisons,
             confidence,
             min(score) over (partition by user_id) as min_score,
             max(score) over (partition by user_id) as max_score
         from user_movie_rank
     ) t;