drop view if exists user_movie_rating;

alter table user_movie_rank add column mu numeric(12, 6) not null default 0;
alter table user_movie_rank add column sigma numeric(12, 6) not null default 2;
alter table user_movie_rank add column score_error_80 numeric(12, 6) not null default 5.77;

update user_movie_rank
set mu = score,
    sigma = cast(2 / sqrt(cast(greatest(direct_comparisons, 1) as numeric(12, 6))) as numeric(12, 6)),
    score_error_80 = cast(
        least(
            cast(9 as numeric(12, 6)),
            cast(5.77 as numeric(12, 6)) / sqrt(cast(greatest(direct_comparisons, 1) as numeric(12, 6)))
        ) as numeric(12, 6)
    );

alter table user_movie_rank
    add constraint chk_user_movie_rank_sigma_positive check (sigma > 0);

alter table user_movie_rank
    add constraint chk_user_movie_rank_score_error_80_non_negative check (score_error_80 >= 0);

create view user_movie_rating as
select
    t.user_id,
    t.movie_id,
    t.rank_position,
    t.score,
    t.direct_comparisons,
    t.mu,
    t.sigma,
    t.score_error_80,
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
             mu,
             sigma,
             score_error_80,
             min(score) over (partition by user_id) as min_score,
             max(score) over (partition by user_id) as max_score
         from user_movie_rank
     ) t;
