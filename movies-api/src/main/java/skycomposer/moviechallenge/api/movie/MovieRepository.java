package skycomposer.moviechallenge.api.movie;

import skycomposer.moviechallenge.api.movie.model.Movie;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface MovieRepository extends JpaRepository<Movie, String> {

    @Query(value = """
            select m.*
            from movies m
            left join user_movie_rating rating
                on rating.movie_id = m.imdb_id
            where (:filter is null
                or trim(:filter) = ''
                or lower(m.title) like concat('%', lower(:filter), '%')
                or lower(m.director) like concat('%', lower(:filter), '%')
                or lower(coalesce(m.writer, '')) like concat('%', lower(:filter), '%'))
            group by m.imdb_id, m.title, m.director, m.writer, m.release_year, m.poster, m.genre, m.country, m.type
            order by coalesce(avg(rating.rating), 0) desc,
                count(distinct rating.user_id) desc,
                regexp_replace(lower(m.title), '^(the|a)[[:space:]]+', '') asc,
                lower(m.title) asc,
                m.imdb_id asc
            """, countQuery = """
            select count(1)
            from movies m
            where (:filter is null
                or trim(:filter) = ''
                or lower(m.title) like concat('%', lower(:filter), '%')
                or lower(m.director) like concat('%', lower(:filter), '%')
                or lower(coalesce(m.writer, '')) like concat('%', lower(:filter), '%'))
            """, nativeQuery = true)
    Page<Movie> findAllByUsersFavoritePopularity(@Param("filter") String filter, Pageable pageable);

    @Query(value = """
            select m.*
            from movies m
            join movie_recommendations recommendation
                on recommendation.movie_id = m.imdb_id
                and recommendation.user_id = :username
                and recommendation.positive = true
            join user_movie_rating rating
                on rating.movie_id = m.imdb_id
                and rating.user_id = recommendation.user_id
            join user_movie_challenge challenge
                on challenge.movie_id = m.imdb_id
                and challenge.user_id = recommendation.user_id
            where (:filter is null
                or trim(:filter) = ''
                or lower(m.title) like concat('%', lower(:filter), '%')
                or lower(m.director) like concat('%', lower(:filter), '%')
                or lower(coalesce(m.writer, '')) like concat('%', lower(:filter), '%'))
            order by rating.rank_position asc,
                rating.score desc,
                m.title asc,
                m.imdb_id asc
            """, countQuery = """
            select count(1)
            from movie_recommendations recommendation
            join movies m
                on m.imdb_id = recommendation.movie_id
            join user_movie_rating rating
                on rating.movie_id = recommendation.movie_id
                and rating.user_id = recommendation.user_id
            join user_movie_challenge challenge
                on challenge.movie_id = recommendation.movie_id
                and challenge.user_id = recommendation.user_id
            where recommendation.user_id = :username
                and recommendation.positive = true
                and (:filter is null
                    or trim(:filter) = ''
                    or lower(m.title) like concat('%', lower(:filter), '%')
                    or lower(m.director) like concat('%', lower(:filter), '%')
                    or lower(coalesce(m.writer, '')) like concat('%', lower(:filter), '%'))
            """, nativeQuery = true)
    Page<Movie> findFavoriteMoviesByUsername(@Param("username") String username,
                                             @Param("filter") String filter,
                                             Pageable pageable);

    @Query(value = """
            select m.*
            from movies m
            join user_movie_rating rating
                on rating.movie_id = m.imdb_id
            where (:filter is null
                or trim(:filter) = ''
                or lower(m.title) like concat('%', lower(:filter), '%')
                or lower(m.director) like concat('%', lower(:filter), '%')
                or lower(coalesce(m.writer, '')) like concat('%', lower(:filter), '%'))
            group by m.imdb_id, m.title, m.director, m.writer, m.release_year, m.poster, m.genre, m.country, m.type
            order by avg(rating.rating) desc,
                count(distinct rating.user_id) desc,
                m.title asc,
                m.imdb_id asc
            """, countQuery = """
            select count(1)
            from (
                select rating.movie_id
                from user_movie_rating rating
                join movies m
                    on m.imdb_id = rating.movie_id
                where (:filter is null
                    or trim(:filter) = ''
                    or lower(m.title) like concat('%', lower(:filter), '%')
                    or lower(m.director) like concat('%', lower(:filter), '%')
                    or lower(coalesce(m.writer, '')) like concat('%', lower(:filter), '%'))
                group by rating.movie_id
            ) users_favorite_movies
            """, nativeQuery = true)
    Page<Movie> findUsersFavoriteMovies(@Param("filter") String filter, Pageable pageable);

    @Query(value = """
            with rating_similarity as (
                select other_rating.user_id,
                    avg(cast(1 as numeric(12, 6))
                        - (abs(current_rating.rating - other_rating.rating) / cast(10 as numeric(12, 6)))) as rating_similarity,
                    least(cast(1 as numeric(12, 6)),
                        cast(count(1) as numeric(12, 6)) / cast(8 as numeric(12, 6))) as rating_confidence
                from user_movie_rating current_rating
                join user_movie_rating other_rating
                    on other_rating.movie_id = current_rating.movie_id
                    and other_rating.user_id <> current_rating.user_id
                where current_rating.user_id = :username
                group by other_rating.user_id
            ),
            direct_vote_similarity as (
                select other_vote.user_id,
                    avg(case
                        when other_vote.winner_id = current_vote.winner_id
                            and other_vote.loser_id = current_vote.loser_id
                        then cast(1 as numeric(12, 6))
                        else cast(0 as numeric(12, 6))
                    end) as direct_agreement,
                    least(cast(1 as numeric(12, 6)),
                        cast(count(1) as numeric(12, 6)) / cast(8 as numeric(12, 6))) as direct_confidence
                from user_movie_challenge_vote current_vote
                join user_movie_challenge_vote other_vote
                    on other_vote.user_id <> current_vote.user_id
                    and (
                        (other_vote.winner_id = current_vote.winner_id
                            and other_vote.loser_id = current_vote.loser_id)
                        or (other_vote.winner_id = current_vote.loser_id
                            and other_vote.loser_id = current_vote.winner_id)
                    )
                where current_vote.user_id = :username
                group by other_vote.user_id
            ),
            similar_user_ids as (
                select user_id from rating_similarity
                union
                select user_id from direct_vote_similarity
            ),
            weighted_similar_users as (
                select similar_user_ids.user_id,
                    (coalesce(rating_similarity.rating_similarity * rating_similarity.rating_confidence,
                        cast(0 as numeric(12, 6))) * cast(0.70 as numeric(12, 6))
                    + coalesce(direct_vote_similarity.direct_agreement * direct_vote_similarity.direct_confidence,
                        cast(0 as numeric(12, 6))) * cast(0.30 as numeric(12, 6))) as similarity
                from similar_user_ids
                left join rating_similarity
                    on rating_similarity.user_id = similar_user_ids.user_id
                left join direct_vote_similarity
                    on direct_vote_similarity.user_id = similar_user_ids.user_id
            ),
            similar_users as (
                select user_id, similarity
                from weighted_similar_users
                where similarity > 0
            ),
            recommended_movies as (
                select candidate_rating.movie_id,
                    sum(candidate_rating.rating * similar_users.similarity)
                        / sum(similar_users.similarity) as recommended_score,
                    sum(similar_users.similarity) as similarity_weight,
                    count(distinct candidate_rating.user_id) as similar_user_count
                from user_movie_rating candidate_rating
                join similar_users
                    on similar_users.user_id = candidate_rating.user_id
                where not exists (
                        select 1
                        from movie_recommendations recommendation
                        where recommendation.user_id = :username
                            and recommendation.movie_id = candidate_rating.movie_id
                    )
                group by candidate_rating.movie_id
                having sum(similar_users.similarity) > 0
                    and sum(candidate_rating.rating * similar_users.similarity)
                        / sum(similar_users.similarity) > 0
            )
            select m.*
            from recommended_movies
            join movies m
                on m.imdb_id = recommended_movies.movie_id
            where (:filter is null
                or trim(:filter) = ''
                or lower(m.title) like concat('%', lower(:filter), '%')
                or lower(m.director) like concat('%', lower(:filter), '%')
                or lower(coalesce(m.writer, '')) like concat('%', lower(:filter), '%'))
            order by recommended_movies.recommended_score desc,
                recommended_movies.similarity_weight desc,
                recommended_movies.similar_user_count desc,
                m.title asc,
                m.imdb_id asc
            """, countQuery = """
            with rating_similarity as (
                select other_rating.user_id,
                    avg(cast(1 as numeric(12, 6))
                        - (abs(current_rating.rating - other_rating.rating) / cast(10 as numeric(12, 6)))) as rating_similarity,
                    least(cast(1 as numeric(12, 6)),
                        cast(count(1) as numeric(12, 6)) / cast(8 as numeric(12, 6))) as rating_confidence
                from user_movie_rating current_rating
                join user_movie_rating other_rating
                    on other_rating.movie_id = current_rating.movie_id
                    and other_rating.user_id <> current_rating.user_id
                where current_rating.user_id = :username
                group by other_rating.user_id
            ),
            direct_vote_similarity as (
                select other_vote.user_id,
                    avg(case
                        when other_vote.winner_id = current_vote.winner_id
                            and other_vote.loser_id = current_vote.loser_id
                        then cast(1 as numeric(12, 6))
                        else cast(0 as numeric(12, 6))
                    end) as direct_agreement,
                    least(cast(1 as numeric(12, 6)),
                        cast(count(1) as numeric(12, 6)) / cast(8 as numeric(12, 6))) as direct_confidence
                from user_movie_challenge_vote current_vote
                join user_movie_challenge_vote other_vote
                    on other_vote.user_id <> current_vote.user_id
                    and (
                        (other_vote.winner_id = current_vote.winner_id
                            and other_vote.loser_id = current_vote.loser_id)
                        or (other_vote.winner_id = current_vote.loser_id
                            and other_vote.loser_id = current_vote.winner_id)
                    )
                where current_vote.user_id = :username
                group by other_vote.user_id
            ),
            similar_user_ids as (
                select user_id from rating_similarity
                union
                select user_id from direct_vote_similarity
            ),
            weighted_similar_users as (
                select similar_user_ids.user_id,
                    (coalesce(rating_similarity.rating_similarity * rating_similarity.rating_confidence,
                        cast(0 as numeric(12, 6))) * cast(0.70 as numeric(12, 6))
                    + coalesce(direct_vote_similarity.direct_agreement * direct_vote_similarity.direct_confidence,
                        cast(0 as numeric(12, 6))) * cast(0.30 as numeric(12, 6))) as similarity
                from similar_user_ids
                left join rating_similarity
                    on rating_similarity.user_id = similar_user_ids.user_id
                left join direct_vote_similarity
                    on direct_vote_similarity.user_id = similar_user_ids.user_id
            ),
            similar_users as (
                select user_id, similarity
                from weighted_similar_users
                where similarity > 0
            ),
            recommended_movies as (
                select candidate_rating.movie_id
                from user_movie_rating candidate_rating
                join similar_users
                    on similar_users.user_id = candidate_rating.user_id
                where not exists (
                        select 1
                        from movie_recommendations recommendation
                        where recommendation.user_id = :username
                            and recommendation.movie_id = candidate_rating.movie_id
                    )
                group by candidate_rating.movie_id
                having sum(similar_users.similarity) > 0
                    and sum(candidate_rating.rating * similar_users.similarity)
                        / sum(similar_users.similarity) > 0
            )
            select count(1)
            from recommended_movies
            join movies m
                on m.imdb_id = recommended_movies.movie_id
            where (:filter is null
                or trim(:filter) = ''
                or lower(m.title) like concat('%', lower(:filter), '%')
                or lower(m.director) like concat('%', lower(:filter), '%')
                or lower(coalesce(m.writer, '')) like concat('%', lower(:filter), '%')
            )
            """, nativeQuery = true)
    Page<Movie> findUsersRecommendedMoviesByUsername(@Param("username") String username,
                                                     @Param("filter") String filter,
                                                     Pageable pageable);
}
