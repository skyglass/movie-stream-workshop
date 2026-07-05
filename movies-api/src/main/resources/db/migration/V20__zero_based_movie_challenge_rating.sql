drop view user_movie_rating;

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
                - (cast(10 as numeric(12, 6)) * cast(rank_position - 1 as numeric(12, 6)))
                    / cast(count(1) over (partition by user_id) - 1 as numeric(12, 6)),
            2
        ) as numeric(4, 2))
    end as rating
from user_movie_rank;
