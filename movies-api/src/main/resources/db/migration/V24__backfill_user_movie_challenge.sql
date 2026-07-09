delete from user_movie_challenge;

insert into user_movie_challenge (user_id, movie_id, challenge_count)
select challenge_vote.user_id,
    challenge_vote.movie_id,
    count(1) as challenge_count
from (
    select user_id, winner_id as movie_id
    from user_movie_challenge_vote
    union all
    select user_id, loser_id as movie_id
    from user_movie_challenge_vote
) challenge_vote
group by challenge_vote.user_id, challenge_vote.movie_id;
