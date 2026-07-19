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
                and (:selectedCategoryCount=0 or exists (select 1 from category s where s.id in (:selectedCategories) and exists (select 1 from category_parent_child_all c join movie_category mc on mc.category_id=c.descendant_id where c.ancestor_id=s.id and mc.movie_id=m.imdb_id)))
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
                and (:selectedCategoryCount=0 or exists (select 1 from category s where s.id in (:selectedCategories) and exists (select 1 from category_parent_child_all c join movie_category mc on mc.category_id=c.descendant_id where c.ancestor_id=s.id and mc.movie_id=m.imdb_id)))
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
                and (:selectedCategoryCount=0 or exists (select 1 from category s where s.id in (:selectedCategories) and exists (select 1 from category_parent_child_all c join movie_category mc on mc.category_id=c.descendant_id where c.ancestor_id=s.id and mc.movie_id=m.imdb_id)))
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
                and (:selectedCategoryCount=0 or exists (select 1 from category s where s.id in (:selectedCategories) and exists (select 1 from category_parent_child_all c join movie_category mc on mc.category_id=c.descendant_id where c.ancestor_id=s.id and mc.movie_id=m.imdb_id)))
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
                and (:selectedCategoryCount=0 or exists (select 1 from category s where s.id in (:selectedCategories) and exists (select 1 from category_parent_child_all c join movie_category mc on mc.category_id=c.descendant_id where c.ancestor_id=s.id and mc.movie_id=m.imdb_id)))
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
                    and (:selectedCategoryCount=0 or exists (select 1 from category s where s.id in (:selectedCategories) and exists (select 1 from category_parent_child_all c join movie_category mc on mc.category_id=c.descendant_id where c.ancestor_id=s.id and mc.movie_id=m.imdb_id)))
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
                and (:selectedCategoryCount=0 or exists (select 1 from category s where s.id in (:selectedCategories) and exists (select 1 from category_parent_child_all c join movie_category mc on mc.category_id=c.descendant_id where c.ancestor_id=s.id and mc.movie_id=m.imdb_id)))
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
                and (:selectedCategoryCount=0 or exists (select 1 from category s where s.id in (:selectedCategories) and exists (select 1 from category_parent_child_all c join movie_category mc on mc.category_id=c.descendant_id where c.ancestor_id=s.id and mc.movie_id=m.imdb_id)))
            """, nativeQuery = true)
    Page<Movie> findUsersRecommendedMoviesByUsername(@Param("username") String username,
                                                     @Param("filter") String filter,
                                                     @Param("year") String year,
                                                     @Param("selectedCategoryCount") int selectedCategoryCount,
                                                     @Param("selectedCategories") List<Long> selectedCategories,
                                                     Pageable pageable);

    // Category-taste recommender: works from a rated-movies pool and the categories those movies belong to (no
    // cross-user similarity math, unlike findUsersRecommendedMoviesByUsername above). :seedMovieId is null for the
    // favorites-wide "Recommend Similar Movies" page (scored categories = every category touched by any movie in
    // the pool) or a single movie's imdbId for the Movie Details single-movie variant (scored categories = only
    // that movie's own categories; its own rating is excluded from the pool so it never pollutes the very
    // averages used to score it).
    //
    // :username selects whose ratings make up that pool: a specific viewer's own ratings when signed in, or --
    // since :username is null is never true for a real username in SQL -- EVERY user's ratings when the viewer is
    // anonymous (the single-movie variant is reachable without an account). This falls back cleanly to a
    // catalog-wide "what do people generally rate highly in this category" signal instead of going empty, and the
    // per-viewer "already recommended" exclusion below degrades the same way: recommendation.user_id = null is
    // never true either, so nothing gets excluded for an anonymous viewer (there's no personal judgement to
    // exclude by), while a signed-in viewer's own liked/disliked movies are still excluded as before.
    //
    // Each scored category's average is Bayesian-shrunk toward the pool's own overall rating baseline by
    // :priorWeight / (:priorWeight + ratedCount) -- same shrinkage shape as findAllByUsersFavoritePopularity's
    // homepage ranking and findUsersRecommendedMoviesByUsername's similarity weighting above, so a category
    // with only 1-2 rated movies never outranks one with dozens just because its lone rating happened to be a 10.
    // A candidate's final score is the SUM of every scored category it belongs to, rewarding movies that overlap
    // multiple things the pool rates highly.
    //
    // candidate_score alone frequently ties: two candidates that both match only "Genre: Drama" and nothing else
    // score identically, since the score depends solely on which categories a candidate belongs to, never on the
    // candidate's own reception -- a movie nobody has ever rated scores exactly the same as one forty people
    // loved. Without a further tiebreaker that collapses to alphabetical title/imdbId, which looks arbitrary.
    // candidate_popularity/shrunk_candidate_popularity break that tie with a real signal: the candidate's own
    // catalog-wide average rating (everyone who's rated it, regardless of who's asking), Bayesian-shrunk the same
    // way so one lucky 10 from a single voter doesn't outrank dozens of solid ratings. A candidate nobody has
    // rated at all falls back to the catalog average via coalesce (voter_count=0 would shrink to exactly that
    // anyway), rather than being penalized to the bottom.
    @Query(value = """
            with user_ratings as (
                select rating.movie_id, rating.rating
                from user_movie_rating rating
                where (:username is null or rating.user_id = :username)
                    and (:seedMovieId is null or rating.movie_id <> :seedMovieId)
            ),
            baseline as (
                select avg(rating) as baseline_average
                from user_ratings
            ),
            catalog_prior as (
                select avg(rating) as catalog_average
                from user_movie_rating
            ),
            seed_categories as (
                select distinct mc.category_id
                from movie_category mc
                where (:seedMovieId is not null and mc.movie_id = :seedMovieId)
                    or (:seedMovieId is null and exists (select 1 from user_ratings ur where ur.movie_id = mc.movie_id))
            ),
            category_scores as (
                select mc.category_id,
                    count(1) as rated_count,
                    avg(ur.rating) as category_average
                from movie_category mc
                join user_ratings ur on ur.movie_id = mc.movie_id
                where mc.category_id in (select category_id from seed_categories)
                group by mc.category_id
            ),
            shrunk_category_scores as (
                select category_scores.category_id,
                    baseline.baseline_average
                        + (cast(category_scores.rated_count as numeric(12, 6))
                            / cast(category_scores.rated_count + :priorWeight as numeric(12, 6)))
                        * (category_scores.category_average - baseline.baseline_average) as score
                from category_scores
                cross join baseline
            ),
            candidates as (
                select mc.movie_id,
                    sum(shrunk_category_scores.score) as candidate_score,
                    count(distinct mc.category_id) as matched_category_count
                from movie_category mc
                join shrunk_category_scores on shrunk_category_scores.category_id = mc.category_id
                where (:seedMovieId is null or mc.movie_id <> :seedMovieId)
                    and not exists (
                        select 1
                        from movie_recommendations recommendation
                        where recommendation.user_id = :username
                            and recommendation.movie_id = mc.movie_id
                    )
                group by mc.movie_id
            ),
            candidate_popularity as (
                select rating.movie_id,
                    count(distinct rating.user_id) as voter_count,
                    avg(rating.rating) as average_rating
                from user_movie_rating rating
                where rating.movie_id in (select movie_id from candidates)
                group by rating.movie_id
            ),
            shrunk_candidate_popularity as (
                select candidate_popularity.movie_id,
                    catalog_prior.catalog_average
                        + (cast(candidate_popularity.voter_count as numeric(12, 6))
                            / cast(candidate_popularity.voter_count + :priorWeight as numeric(12, 6)))
                        * (candidate_popularity.average_rating - catalog_prior.catalog_average) as popularity_score
                from candidate_popularity
                cross join catalog_prior
            )
            select m.*
            from candidates
            join movies m
                on m.imdb_id = candidates.movie_id
            left join shrunk_candidate_popularity
                on shrunk_candidate_popularity.movie_id = candidates.movie_id
            cross join catalog_prior
            where (:filter is null
                or trim(:filter) = ''
                or lower(m.title) like concat('%', lower(:filter), '%')
                or lower(m.director) like concat('%', lower(:filter), '%')
                or lower(coalesce(m.writer, '')) like concat('%', lower(:filter), '%'))
                and (:year is null or m.release_year = :year)
                and (:selectedCategoryCount=0 or exists (select 1 from category s where s.id in (:selectedCategories) and exists (select 1 from category_parent_child_all c join movie_category mc on mc.category_id=c.descendant_id where c.ancestor_id=s.id and mc.movie_id=m.imdb_id)))
            order by candidates.candidate_score desc,
                candidates.matched_category_count desc,
                coalesce(shrunk_candidate_popularity.popularity_score, catalog_prior.catalog_average) desc,
                m.title asc,
                m.imdb_id asc
            """, countQuery = """
            with user_ratings as (
                select rating.movie_id, rating.rating
                from user_movie_rating rating
                where (:username is null or rating.user_id = :username)
                    and (:seedMovieId is null or rating.movie_id <> :seedMovieId)
            ),
            baseline as (
                select avg(rating) as baseline_average
                from user_ratings
            ),
            seed_categories as (
                select distinct mc.category_id
                from movie_category mc
                where (:seedMovieId is not null and mc.movie_id = :seedMovieId)
                    or (:seedMovieId is null and exists (select 1 from user_ratings ur where ur.movie_id = mc.movie_id))
            ),
            category_scores as (
                select mc.category_id,
                    count(1) as rated_count,
                    avg(ur.rating) as category_average
                from movie_category mc
                join user_ratings ur on ur.movie_id = mc.movie_id
                where mc.category_id in (select category_id from seed_categories)
                group by mc.category_id
            ),
            shrunk_category_scores as (
                select category_scores.category_id,
                    baseline.baseline_average
                        + (cast(category_scores.rated_count as numeric(12, 6))
                            / cast(category_scores.rated_count + :priorWeight as numeric(12, 6)))
                        * (category_scores.category_average - baseline.baseline_average) as score
                from category_scores
                cross join baseline
            ),
            candidates as (
                select mc.movie_id
                from movie_category mc
                join shrunk_category_scores on shrunk_category_scores.category_id = mc.category_id
                where (:seedMovieId is null or mc.movie_id <> :seedMovieId)
                    and not exists (
                        select 1
                        from movie_recommendations recommendation
                        where recommendation.user_id = :username
                            and recommendation.movie_id = mc.movie_id
                    )
                group by mc.movie_id
            )
            select count(1)
            from candidates
            join movies m
                on m.imdb_id = candidates.movie_id
            where (:filter is null
                or trim(:filter) = ''
                or lower(m.title) like concat('%', lower(:filter), '%')
                or lower(m.director) like concat('%', lower(:filter), '%')
                or lower(coalesce(m.writer, '')) like concat('%', lower(:filter), '%'))
                and (:year is null or m.release_year = :year)
                and (:selectedCategoryCount=0 or exists (select 1 from category s where s.id in (:selectedCategories) and exists (select 1 from category_parent_child_all c join movie_category mc on mc.category_id=c.descendant_id where c.ancestor_id=s.id and mc.movie_id=m.imdb_id)))
            """, nativeQuery = true)
    Page<Movie> findCategorySimilarMovies(@Param("username") String username,
                                          @Param("seedMovieId") String seedMovieId,
                                          @Param("filter") String filter,
                                          @Param("year") String year,
                                          @Param("selectedCategoryCount") int selectedCategoryCount,
                                          @Param("selectedCategories") List<Long> selectedCategories,
                                          @Param("priorWeight") int priorWeight,
                                          Pageable pageable);

    // Movies already assigned to a Movie Guide's own category tree, excluding any that only appear there via a
    // subscribed/referenced category (movie_guide_default_category) -- used by the guide-creation wizard's Step 2
    // so the curator only ever sees movies they explicitly added, not ones that arrive "for free" via subscription.
    @Query(value = """
            select m.*
            from movies m
            where exists (select 1 from category_parent_child_all c join movie_category mc on mc.category_id=c.descendant_id
                    where c.ancestor_id=:guideCategoryId and mc.movie_id=m.imdb_id)
                and (:excludedCategoryCount=0 or not exists (select 1 from category s where s.id in (:excludedCategories)
                    and exists (select 1 from category_parent_child_all c join movie_category mc on mc.category_id=c.descendant_id
                        where c.ancestor_id=s.id and mc.movie_id=m.imdb_id)))
                and (:filter is null
                    or trim(:filter) = ''
                    or lower(m.title) like concat('%', lower(:filter), '%')
                    or lower(m.director) like concat('%', lower(:filter), '%')
                    or lower(coalesce(m.writer, '')) like concat('%', lower(:filter), '%'))
                and (:year is null or m.release_year = :year)
            order by regexp_replace(lower(m.title), '^(the|a)[[:space:]]+', '') asc, lower(m.title) asc, m.imdb_id asc
            """, countQuery = """
            select count(1)
            from movies m
            where exists (select 1 from category_parent_child_all c join movie_category mc on mc.category_id=c.descendant_id
                    where c.ancestor_id=:guideCategoryId and mc.movie_id=m.imdb_id)
                and (:excludedCategoryCount=0 or not exists (select 1 from category s where s.id in (:excludedCategories)
                    and exists (select 1 from category_parent_child_all c join movie_category mc on mc.category_id=c.descendant_id
                        where c.ancestor_id=s.id and mc.movie_id=m.imdb_id)))
                and (:filter is null
                    or trim(:filter) = ''
                    or lower(m.title) like concat('%', lower(:filter), '%')
                    or lower(m.director) like concat('%', lower(:filter), '%')
                    or lower(coalesce(m.writer, '')) like concat('%', lower(:filter), '%'))
                and (:year is null or m.release_year = :year)
            """, nativeQuery = true)
    Page<Movie> findGuideMovies(@Param("guideCategoryId") long guideCategoryId,
                                @Param("excludedCategoryCount") int excludedCategoryCount,
                                @Param("excludedCategories") List<Long> excludedCategories,
                                @Param("filter") String filter,
                                @Param("year") String year,
                                Pageable pageable);
}
