package skycomposer.moviechallenge.api.movie;

import skycomposer.moviechallenge.api.movie.model.Movie;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface MovieRepository extends JpaRepository<Movie, String> {

    @Query(value = """
            with user_rating_average as (
                select rating.user_id,
                    avg(rating.rating) as user_average
                from user_movie_rating rating
                group by rating.user_id
            ),
            catalog_prior as (
                select avg(user_rating_average.user_average) as catalog_average
                from user_rating_average
            ),
            movie_rating_stats as (
                select rating.movie_id,
                    avg(rating.rating) as average_rating,
                    count(distinct rating.user_id) as voter_count
                from user_movie_rating rating
                group by rating.movie_id
            )
            select m.*
            from movies m
            left join movie_rating_stats
                on movie_rating_stats.movie_id = m.imdb_id
            cross join catalog_prior
            where (:filter is null
                or trim(:filter) = ''
                or lower(m.title) like concat('%', lower(:filter), '%')
                or lower(m.director) like concat('%', lower(:filter), '%')
                or lower(coalesce(m.writer, '')) like concat('%', lower(:filter), '%'))
                and (:year is null or m.release_year = :year)
                and (:selectedCategoryCount=0 or not exists (select 1 from category s where s.id in (:selectedCategories) and not exists (select 1 from category_parent_child_all c join movie_category mc on mc.category_id=c.descendant_id where c.ancestor_id=s.id and mc.movie_id=m.imdb_id)))
                and (:onlyNotRecommended = false or not exists (select 1 from movie_recommendations mr where mr.user_id = :username and mr.movie_id = m.imdb_id))
            order by case when movie_rating_stats.voter_count is null then 1 else 0 end asc,
                catalog_prior.catalog_average
                    + (cast(movie_rating_stats.voter_count as numeric(12, 6))
                        / cast(movie_rating_stats.voter_count + :priorWeight as numeric(12, 6)))
                    * (movie_rating_stats.average_rating - catalog_prior.catalog_average) desc,
                movie_rating_stats.voter_count desc,
                movie_rating_stats.average_rating desc,
                regexp_replace(lower(m.title), '^(the|a)[[:space:]]+', '') asc,
                lower(m.title) asc,
                m.imdb_id asc
            """, countQuery = """
            select count(1)
            from movies m
            where :priorWeight > 0
                and (:filter is null
                or trim(:filter) = ''
                or lower(m.title) like concat('%', lower(:filter), '%')
                or lower(m.director) like concat('%', lower(:filter), '%')
                or lower(coalesce(m.writer, '')) like concat('%', lower(:filter), '%'))
                and (:year is null or m.release_year = :year)
                and (:selectedCategoryCount=0 or not exists (select 1 from category s where s.id in (:selectedCategories) and not exists (select 1 from category_parent_child_all c join movie_category mc on mc.category_id=c.descendant_id where c.ancestor_id=s.id and mc.movie_id=m.imdb_id)))
                and (:onlyNotRecommended = false or not exists (select 1 from movie_recommendations mr where mr.user_id = :username and mr.movie_id = m.imdb_id))
            """, nativeQuery = true)
    Page<Movie> findAllByUsersFavoritePopularity(@Param("filter") String filter,
                                                 @Param("year") String year,
                                                 @Param("selectedCategoryCount") int selectedCategoryCount,
                                                 @Param("selectedCategories") List<Long> selectedCategories,
                                                 @Param("priorWeight") int priorWeight,
                                                 @Param("username") String username,
                                                 @Param("onlyNotRecommended") boolean onlyNotRecommended,
                                                 Pageable pageable);

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
                and (:year is null or m.release_year = :year)
                and (:selectedCategoryCount=0 or not exists (select 1 from category s where s.id in (:selectedCategories) and not exists (select 1 from category_parent_child_all c join movie_category mc on mc.category_id=c.descendant_id where c.ancestor_id=s.id and mc.movie_id=m.imdb_id)))
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
                and (:year is null or m.release_year = :year)
                and (:selectedCategoryCount=0 or not exists (select 1 from category s where s.id in (:selectedCategories) and not exists (select 1 from category_parent_child_all c join movie_category mc on mc.category_id=c.descendant_id where c.ancestor_id=s.id and mc.movie_id=m.imdb_id)))
            """, nativeQuery = true)
    Page<Movie> findFavoriteMoviesByUsername(@Param("username") String username,
                                             @Param("filter") String filter,
                                             @Param("year") String year,
                                             @Param("selectedCategoryCount") int selectedCategoryCount,
                                             @Param("selectedCategories") List<Long> selectedCategories,
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
                and (:year is null or m.release_year = :year)
                and (:selectedCategoryCount=0 or not exists (select 1 from category s where s.id in (:selectedCategories) and not exists (select 1 from category_parent_child_all c join movie_category mc on mc.category_id=c.descendant_id where c.ancestor_id=s.id and mc.movie_id=m.imdb_id)))
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
                    and (:year is null or m.release_year = :year)
                    and (:selectedCategoryCount=0 or not exists (select 1 from category s where s.id in (:selectedCategories) and not exists (select 1 from category_parent_child_all c join movie_category mc on mc.category_id=c.descendant_id where c.ancestor_id=s.id and mc.movie_id=m.imdb_id)))
                group by rating.movie_id
            ) users_favorite_movies
            """, nativeQuery = true)
    Page<Movie> findUsersFavoriteMovies(@Param("filter") String filter, @Param("year") String year,
                                        @Param("selectedCategoryCount") int selectedCategoryCount,
                                        @Param("selectedCategories") List<Long> selectedCategories, Pageable pageable);

    @Query(value = """
            with catalog_prior as (
                select avg(rating) as catalog_average
                from user_movie_rating
            ),
            rating_similarity as (
                select other_rating.user_id,
                    corr(current_rating.rating, other_rating.rating) as rating_similarity,
                    count(1) as shared_rating_count
                from user_movie_rating current_rating
                join user_movie_rating other_rating
                    on other_rating.movie_id = current_rating.movie_id
                    and other_rating.user_id <> current_rating.user_id
                where current_rating.user_id = :username
                group by other_rating.user_id
                having count(1) >= 2
            ),
            weighted_similar_users as (
                select rating_similarity.user_id,
                    coalesce(rating_similarity.rating_similarity
                            * cast(rating_similarity.shared_rating_count as numeric(12, 6))
                            / cast(rating_similarity.shared_rating_count + 8 as numeric(12, 6)),
                        cast(0 as numeric(12, 6))) as similarity
                from rating_similarity
            ),
            similar_users as (
                select user_id, similarity
                from weighted_similar_users
                where similarity > 0
            ),
            recommended_movies as (
                select candidate_rating.movie_id,
                    (catalog_prior.catalog_average
                        + sum(candidate_rating.rating * similar_users.similarity))
                        / (cast(1 as numeric(12, 6))
                            + sum(similar_users.similarity)) as recommended_score,
                    sum(similar_users.similarity) as similarity_weight,
                    count(distinct candidate_rating.user_id) as similar_user_count
                from user_movie_rating candidate_rating
                join similar_users
                    on similar_users.user_id = candidate_rating.user_id
                cross join catalog_prior
                where not exists (
                        select 1
                        from movie_recommendations recommendation
                        where recommendation.user_id = :username
                            and recommendation.movie_id = candidate_rating.movie_id
                    )
                group by candidate_rating.movie_id, catalog_prior.catalog_average
                having sum(similar_users.similarity) > 0
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
                and (:year is null or m.release_year = :year)
                and (:selectedCategoryCount=0 or not exists (select 1 from category s where s.id in (:selectedCategories) and not exists (select 1 from category_parent_child_all c join movie_category mc on mc.category_id=c.descendant_id where c.ancestor_id=s.id and mc.movie_id=m.imdb_id)))
            order by recommended_movies.recommended_score desc,
                recommended_movies.similarity_weight desc,
                recommended_movies.similar_user_count desc,
                m.title asc,
                m.imdb_id asc
            """, countQuery = """
            with rating_similarity as (
                select other_rating.user_id,
                    corr(current_rating.rating, other_rating.rating) as rating_similarity,
                    count(1) as shared_rating_count
                from user_movie_rating current_rating
                join user_movie_rating other_rating
                    on other_rating.movie_id = current_rating.movie_id
                    and other_rating.user_id <> current_rating.user_id
                where current_rating.user_id = :username
                group by other_rating.user_id
                having count(1) >= 2
            ),
            weighted_similar_users as (
                select rating_similarity.user_id,
                    coalesce(rating_similarity.rating_similarity
                            * cast(rating_similarity.shared_rating_count as numeric(12, 6))
                            / cast(rating_similarity.shared_rating_count + 8 as numeric(12, 6)),
                        cast(0 as numeric(12, 6))) as similarity
                from rating_similarity
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
                and (:year is null or m.release_year = :year)
                and (:selectedCategoryCount=0 or not exists (select 1 from category s where s.id in (:selectedCategories) and not exists (select 1 from category_parent_child_all c join movie_category mc on mc.category_id=c.descendant_id where c.ancestor_id=s.id and mc.movie_id=m.imdb_id)))
            """, nativeQuery = true)
    Page<Movie> findUsersRecommendedMoviesByUsername(@Param("username") String username,
                                                     @Param("filter") String filter,
                                                     @Param("year") String year,
                                                     @Param("selectedCategoryCount") int selectedCategoryCount,
                                                     @Param("selectedCategories") List<Long> selectedCategories,
                                                     Pageable pageable);
}
