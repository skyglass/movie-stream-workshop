package skycomposer.moviechallenge.api.movie;

import skycomposer.moviechallenge.api.movie.dto.MovieChallengeDto;
import skycomposer.moviechallenge.api.movie.dto.MovieChallengeDto.MovieChallengeMovieDto;
import skycomposer.moviechallenge.api.movie.dto.MovieRatingDto;
import skycomposer.moviechallenge.api.movie.dto.SuggestedMovieChallengeDto;
import skycomposer.moviechallenge.api.movie.dto.SuggestedMovieChallengeDto.SuggestedMovieChallengeMovieDto;
import skycomposer.moviechallenge.api.movie.dto.SuggestedMovieChallengePageDto;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@RequiredArgsConstructor
@Repository
public class MovieChallengeRepository {

    private static final int EXPLORATION_DIRECT_COMPARISONS = 4;
    private static final int CLOSE_RANK_WINDOW_MINIMUM = 3;
    private static final int CLOSE_RANK_WINDOW_RECOMMENDATION_DIVISOR = 33;
    private static final double CLOSE_SCORE_DISTANCE = 1.0;
    private static final double MINIMAL_REFINEMENT_PAIR_INFORMATION = 0.30;
    private static final int SUGGESTED_MAX_DISPLAYED_WIN_PERCENT = 70;
    // Suggested challenge probabilities are displayed with Math.round, so keep the raw cutoff
    // below the half-percent boundary to avoid showing 70%+ pairs in the UI.
    private static final double SUGGESTED_MAX_WIN_PROBABILITY = (SUGGESTED_MAX_DISPLAYED_WIN_PERCENT - 0.5) / 100;
    private static final double SUGGESTED_MAX_MU_DISTANCE = Math.log(SUGGESTED_MAX_WIN_PROBABILITY
            / (1 - SUGGESTED_MAX_WIN_PROBABILITY));
    private static final int BRADLEY_TERRY_ITERATIONS = 50;
    private static final double BRADLEY_TERRY_PRIOR_PRECISION = 1.0;
    private static final double BRADLEY_TERRY_MAX_UPDATE = 0.75;
    private static final double SCORE_ERROR_80_PER_SIGMA = 2.25 * 1.28155;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public Optional<MovieChallengeDto> findNextChallenge(String username) {
        Optional<MovieChallengeDto> explorationChallenge = findExplorationChallenge(username);
        return explorationChallenge.or(() -> findRefinementChallenge(username));
    }

    public SuggestedMovieChallengePageDto findSuggestedChallenges(String username, Pageable pageable) {
        return findSuggestedChallenges(username, pageable, false);
    }

    public SuggestedMovieChallengePageDto findSuggestedChallenges(String username,
                                                                  Pageable pageable,
                                                                  boolean higherRankedFirst) {
        return findSuggestedChallenges(username, pageable, higherRankedFirst, false);
    }

    public SuggestedMovieChallengePageDto findSuggestedChallenges(String username,
                                                                  Pageable pageable,
                                                                  boolean higherRankedFirst,
                                                                  boolean boostHigherRanks) {
        return findSuggestedChallenges(username, pageable, higherRankedFirst, boostHigherRanks, false);
    }

    public SuggestedMovieChallengePageDto findSuggestedChallenges(String username,
                                                                  Pageable pageable,
                                                                  boolean higherRankedFirst,
                                                                  boolean boostHigherRanks,
                                                                  boolean moreInterestingFirst) {
        SuggestedChallengeOrdering ordering = higherRankedFirst
                ? SuggestedChallengeOrdering.HIGHER_RANKED_FIRST
                : boostHigherRanks
                    ? SuggestedChallengeOrdering.BOOST_HIGHER_RANKS
                    : moreInterestingFirst
                        ? SuggestedChallengeOrdering.MORE_INTERESTING_FIRST
                        : SuggestedChallengeOrdering.PAIR_INFORMATION_FIRST;
        if (ordering.preferRankedRefinement()) {
            Map<String, Object> refinementParams = refinementParams(username);
            long refinementCount = countChallenges(refinementChallengeBaseSql(ChallengeFilterMode.SUGGESTED_LIST),
                    refinementParams);
            if (refinementCount > 0) {
                return querySuggestedChallenges(
                        pagedSql(refinementChallengeBaseSql(ChallengeFilterMode.SUGGESTED_LIST),
                                suggestedRefinementChallengeOrderSql(ordering)),
                        pageParams(refinementParams, pageable),
                        refinementCount);
            }

            Map<String, Object> explorationParams = explorationParams(username);
            long explorationCount = countChallenges(explorationChallengeBaseSql(ChallengeFilterMode.SUGGESTED_LIST),
                    explorationParams);
            return querySuggestedChallenges(
                    pagedSql(explorationChallengeBaseSql(ChallengeFilterMode.SUGGESTED_LIST),
                            suggestedExplorationChallengeOrderSql(ordering)),
                    pageParams(explorationParams, pageable),
                    explorationCount);
        }

        Map<String, Object> explorationParams = explorationParams(username);
        long explorationCount = countChallenges(explorationChallengeBaseSql(ChallengeFilterMode.SUGGESTED_LIST),
                explorationParams);
        if (explorationCount > 0) {
            return querySuggestedChallenges(
                    pagedSql(explorationChallengeBaseSql(ChallengeFilterMode.SUGGESTED_LIST),
                            suggestedExplorationChallengeOrderSql(ordering)),
                    pageParams(explorationParams, pageable),
                    explorationCount);
        }

        Map<String, Object> refinementParams = refinementParams(username);
        long refinementCount = countChallenges(refinementChallengeBaseSql(ChallengeFilterMode.SUGGESTED_LIST),
                refinementParams);
        return querySuggestedChallenges(
                pagedSql(refinementChallengeBaseSql(ChallengeFilterMode.SUGGESTED_LIST),
                        suggestedRefinementChallengeOrderSql(ordering)),
                pageParams(refinementParams, pageable),
                refinementCount);
    }

    private Optional<MovieChallengeDto> findExplorationChallenge(String username) {
        return queryChallenge(
                singleSql(explorationChallengeBaseSql(ChallengeFilterMode.NEXT_CHALLENGE), explorationChallengeOrderSql()),
                explorationParams(username));
    }

    private String explorationChallengeBaseSql(ChallengeFilterMode filterMode) {
        String pairInformationSql = """
                (candidate_movie.sigma + partner_movie.sigma)
                    / (cast(4 as numeric(12, 6)) + abs(candidate_movie.mu - partner_movie.mu))
                """;
        String candidatePairPriorityOrder = filterMode.applySuggestedPairLimit()
                ? """
                    case
                        when candidate_movie.mu is null
                            or partner_movie.mu is null
                            or abs(candidate_movie.mu - partner_movie.mu) < :suggestedMaxMuDistance then 0
                        else 1
                    end,
                    abs(candidate_movie.sort_rank_position - partner_movie.sort_rank_position),
                    partner_movie.direct_comparisons,
                    partner_movie.sort_rank_position,
                    partner_movie.movie_id
                """
                : """
                    abs(candidate_movie.sort_rank_position - partner_movie.sort_rank_position),
                    partner_movie.direct_comparisons,
                    partner_movie.sort_rank_position,
                    partner_movie.movie_id
                """;
        String suggestedListFilters = filterMode.applySuggestedPairLimit()
                ? """
                    and eligible_pair.candidate_direct_comparisons = eligible_pair.min_candidate_direct_comparisons
                    and eligible_pair.candidate_pair_priority = 1
                    and (
                        eligible_pair.movie1_mu is null
                        or eligible_pair.movie2_mu is null
                        or abs(eligible_pair.movie1_mu - eligible_pair.movie2_mu) < :suggestedMaxMuDistance
                    )
                """
                : "";
        String pairConfidenceSql = pairInformationConfidencePercentSql();
        return """
                with recommended_movie as (
                    select recommendation.user_id,
                        recommendation.movie_id,
                        coalesce(movie_rank.direct_comparisons, 0) as direct_comparisons,
                        coalesce(movie_rank.rank_position, 0) as sort_rank_position,
                        movie_rank.rank_position,
                        movie_rank.mu,
                        movie_rank.sigma
                    from movie_recommendations recommendation
                    left join user_movie_rank movie_rank
                        on movie_rank.user_id = recommendation.user_id
                        and movie_rank.movie_id = recommendation.movie_id
                    where recommendation.user_id = :username
                        and recommendation.positive = true
                ),
                selected_pair as (
                    select candidate_movie.user_id,
                        case
                            when candidate_movie.direct_comparisons
                                < partner_movie.direct_comparisons then candidate_movie.movie_id
                            when partner_movie.direct_comparisons
                                < candidate_movie.direct_comparisons then partner_movie.movie_id
                            when candidate_movie.sort_rank_position
                                <= partner_movie.sort_rank_position then candidate_movie.movie_id
                            else partner_movie.movie_id
                        end as movie1_id,
                        case
                            when candidate_movie.direct_comparisons
                                < partner_movie.direct_comparisons then partner_movie.movie_id
                            when partner_movie.direct_comparisons
                                < candidate_movie.direct_comparisons then candidate_movie.movie_id
                            when candidate_movie.sort_rank_position
                                <= partner_movie.sort_rank_position then partner_movie.movie_id
                            else candidate_movie.movie_id
                        end as movie2_id,
                        case
                            when candidate_movie.direct_comparisons
                                < partner_movie.direct_comparisons then candidate_movie.rank_position
                            when partner_movie.direct_comparisons
                                < candidate_movie.direct_comparisons then partner_movie.rank_position
                            when candidate_movie.sort_rank_position
                                <= partner_movie.sort_rank_position then candidate_movie.rank_position
                            else partner_movie.rank_position
                        end as movie1_rank_position,
                        case
                            when candidate_movie.direct_comparisons
                                < partner_movie.direct_comparisons then partner_movie.rank_position
                            when partner_movie.direct_comparisons
                                < candidate_movie.direct_comparisons then candidate_movie.rank_position
                            when candidate_movie.sort_rank_position
                                <= partner_movie.sort_rank_position then partner_movie.rank_position
                            else candidate_movie.rank_position
                        end as movie2_rank_position,
                        case
                            when candidate_movie.direct_comparisons
                                < partner_movie.direct_comparisons then candidate_movie.mu
                            when partner_movie.direct_comparisons
                                < candidate_movie.direct_comparisons then partner_movie.mu
                            when candidate_movie.sort_rank_position
                                <= partner_movie.sort_rank_position then candidate_movie.mu
                            else partner_movie.mu
                        end as movie1_mu,
                        case
                            when candidate_movie.direct_comparisons
                                < partner_movie.direct_comparisons then partner_movie.mu
                            when partner_movie.direct_comparisons
                                < candidate_movie.direct_comparisons then candidate_movie.mu
                            when candidate_movie.sort_rank_position
                                <= partner_movie.sort_rank_position then partner_movie.mu
                            else candidate_movie.mu
                        end as movie2_mu,
                        least(candidate_movie.direct_comparisons, partner_movie.direct_comparisons)
                            as lower_direct_comparisons,
                        greatest(candidate_movie.direct_comparisons, partner_movie.direct_comparisons)
                            as higher_direct_comparisons,
                        abs(candidate_movie.sort_rank_position - partner_movie.sort_rank_position) as rank_distance,
                        candidate_movie.direct_comparisons as candidate_direct_comparisons,
                        min(candidate_movie.direct_comparisons) over (
                            partition by candidate_movie.user_id
                        ) as min_candidate_direct_comparisons,
                        %s as pair_information,
                        row_number() over (
                            partition by candidate_movie.user_id, candidate_movie.movie_id
                            order by %s
                        ) as candidate_pair_priority
                    from recommended_movie candidate_movie
                    join recommended_movie partner_movie
                        on partner_movie.user_id = candidate_movie.user_id
                        and partner_movie.movie_id <> candidate_movie.movie_id
                    where candidate_movie.direct_comparisons < :explorationDirectComparisons
                        and (
                            candidate_movie.movie_id < partner_movie.movie_id
                            or partner_movie.direct_comparisons >= :explorationDirectComparisons
                        )
                        and not exists (
                            select 1
                            from user_movie_challenge_vote vote
                            where vote.user_id = candidate_movie.user_id
                                and vote.winner_id = candidate_movie.movie_id
                                and vote.loser_id = partner_movie.movie_id
                        )
                        and not exists (
                            select 1
                            from user_movie_challenge_vote vote
                            where vote.user_id = candidate_movie.user_id
                                and vote.winner_id = partner_movie.movie_id
                                and vote.loser_id = candidate_movie.movie_id
                        )
                )
                select selected_pair.user_id,
                    selected_pair.movie1_id,
                    movie1.title as movie1_title,
                    movie1.poster as movie1_poster,
                    movie1.release_year as movie1_year,
                    movie1.director as movie1_director,
                    selected_pair.movie1_rank_position,
                    selected_pair.movie1_mu,
                    movie1_rating.rating as movie1_rating,
                    selected_pair.movie2_id,
                    movie2.title as movie2_title,
                    movie2.poster as movie2_poster,
                    movie2.release_year as movie2_year,
                    movie2.director as movie2_director,
                    selected_pair.movie2_rank_position,
                    selected_pair.movie2_mu,
                    movie2_rating.rating as movie2_rating,
                    %s as pair_confidence_percent,
                    selected_pair.lower_direct_comparisons,
                    selected_pair.higher_direct_comparisons,
                    selected_pair.rank_distance
                from (
                    select eligible_pair.*,
                        min(eligible_pair.pair_information) over () as min_pair_information,
                        max(eligible_pair.pair_information) over () as max_pair_information
                    from selected_pair eligible_pair
                    where eligible_pair.lower_direct_comparisons < :explorationDirectComparisons
                        %s
                ) selected_pair
                join movies movie1
                    on movie1.imdb_id = selected_pair.movie1_id
                join movies movie2
                    on movie2.imdb_id = selected_pair.movie2_id
                left join user_movie_rating movie1_rating
                    on movie1_rating.user_id = selected_pair.user_id
                    and movie1_rating.movie_id = selected_pair.movie1_id
                left join user_movie_rating movie2_rating
                    on movie2_rating.user_id = selected_pair.user_id
                    and movie2_rating.movie_id = selected_pair.movie2_id
                """.formatted(pairInformationSql,
                candidatePairPriorityOrder,
                pairConfidenceSql,
                suggestedListFilters);
    }

    private String explorationChallengeOrderSql() {
        return """
                order by lower_direct_comparisons,
                    rank_distance,
                    higher_direct_comparisons,
                    movie1_rank_position,
                    movie2_rank_position,
                    movie1_id,
                    movie2_id
                """;
    }

    private String suggestedExplorationChallengeOrderSql(SuggestedChallengeOrdering ordering) {
        if (ordering.prioritizeHigherRanks()) {
            return """
                    order by case
                            when movie1_rank_position is null then 1
                            else 0
                        end,
                        movie1_rank_position,
                        rank_distance,
                        pair_confidence_percent,
                        higher_direct_comparisons,
                        coalesce(abs(movie1_mu - movie2_mu), 0),
                        lower_direct_comparisons,
                        movie1_id,
                        movie2_id
                    """;
        }
        String interestDirection = ordering.moreInterestingFirst() ? "desc" : "asc";
        return """
                order by pair_confidence_percent %s,
                    case
                        when movie1_rank_position is null then 0
                        else movie1_rank_position
                    end,
                    rank_distance,
                    lower_direct_comparisons,
                    higher_direct_comparisons,
                    coalesce(abs(movie1_mu - movie2_mu), 0),
                    movie1_id,
                    movie2_id
                """.formatted(interestDirection);
    }

    private Map<String, Object> explorationParams(String username) {
        return Map.of(
                "username", username,
                "explorationDirectComparisons", EXPLORATION_DIRECT_COMPARISONS,
                "suggestedMaxMuDistance", SUGGESTED_MAX_MU_DISTANCE);
    }

    private Optional<MovieChallengeDto> findRefinementChallenge(String username) {
        return queryChallenge(
                singleSql(refinementChallengeBaseSql(ChallengeFilterMode.NEXT_CHALLENGE), refinementChallengeOrderSql()),
                refinementParams(username));
    }

    private String refinementChallengeBaseSql(ChallengeFilterMode filterMode) {
        String pairInformationSql = """
                (first_rank.sigma + second_rank.sigma)
                    / (cast(4 as numeric(12, 6))
                        + abs(first_rank.mu - second_rank.mu))
                """;
        String candidatePairPriorityOrder = filterMode.applySuggestedPairLimit()
                ? """
                    case
                        when abs(first_rank.mu - second_rank.mu) < :suggestedMaxMuDistance then 0
                        else 1
                    end,
                    second_rank.rank_position - first_rank.rank_position,
                    %s desc,
                    greatest(first_rank.direct_comparisons, second_rank.direct_comparisons),
                    least(first_rank.direct_comparisons, second_rank.direct_comparisons),
                    second_rank.movie_id
                """.formatted(pairInformationSql)
                : """
                    %s desc,
                    second_rank.rank_position - first_rank.rank_position,
                    greatest(first_rank.direct_comparisons, second_rank.direct_comparisons),
                    least(first_rank.direct_comparisons, second_rank.direct_comparisons),
                    second_rank.movie_id
                """.formatted(pairInformationSql);
        String closeRankJoinFilter = filterMode.applyRefinementStopFilters()
                ? "and second_rank.rank_position <= first_rank.rank_position + :closeRankWindow"
                : "";
        String stopFilters = filterMode.applyRefinementStopFilters()
                ? """
                    and eligible_pair.rank_distance <= :closeRankWindow
                    and eligible_pair.score_distance <= :closeScoreDistance
                    and eligible_pair.pair_information >= :minimalRefinementPairInformation
                """
                : "";
        String suggestedListFilters = filterMode.applySuggestedPairLimit()
                ? """
                    and eligible_pair.candidate_pair_priority = 1
                    and abs(eligible_pair.movie1_mu - eligible_pair.movie2_mu) < :suggestedMaxMuDistance
                """
                : "";
        String pairInformationPrioritySql = pairInformationSortingPrioritySql();
        return """
                with ranked_recommendation as (
                    select movie_rank.user_id,
                        movie_rank.movie_id,
                        movie_rank.rank_position,
                        case
                            when max(movie_rank.rank_position) over (partition by movie_rank.user_id) <= 1 then 1
                            else 1 + cast(floor(
                                cast(movie_rank.rank_position - 1 as numeric(12, 6)) * 9
                                    / cast(max(movie_rank.rank_position) over (partition by movie_rank.user_id) - 1
                                        as numeric(12, 6))
                            ) as integer)
                        end as rank_bucket,
                        movie_rank.score,
                        movie_rank.direct_comparisons,
                        movie_rank.mu,
                        movie_rank.sigma
                    from user_movie_rank movie_rank
                    join movie_recommendations recommendation
                        on recommendation.user_id = movie_rank.user_id
                        and recommendation.movie_id = movie_rank.movie_id
                        and recommendation.positive = true
                    where movie_rank.user_id = :username
                ),
                selected_pair as (
                    select first_rank.user_id,
                        case
                            when first_rank.direct_comparisons
                                < second_rank.direct_comparisons then first_rank.movie_id
                            when second_rank.direct_comparisons
                                < first_rank.direct_comparisons then second_rank.movie_id
                            when first_rank.rank_position
                                <= second_rank.rank_position then first_rank.movie_id
                            else second_rank.movie_id
                        end as movie1_id,
                        case
                            when first_rank.direct_comparisons
                                < second_rank.direct_comparisons then second_rank.movie_id
                            when second_rank.direct_comparisons
                                < first_rank.direct_comparisons then first_rank.movie_id
                            when first_rank.rank_position
                                <= second_rank.rank_position then second_rank.movie_id
                            else first_rank.movie_id
                        end as movie2_id,
                        case
                            when first_rank.direct_comparisons
                                < second_rank.direct_comparisons then first_rank.rank_position
                            when second_rank.direct_comparisons
                                < first_rank.direct_comparisons then second_rank.rank_position
                            when first_rank.rank_position
                                <= second_rank.rank_position then first_rank.rank_position
                            else second_rank.rank_position
                        end as movie1_rank_position,
                        case
                            when first_rank.direct_comparisons
                                < second_rank.direct_comparisons then second_rank.rank_position
                            when second_rank.direct_comparisons
                                < first_rank.direct_comparisons then first_rank.rank_position
                            when first_rank.rank_position
                                <= second_rank.rank_position then second_rank.rank_position
                            else first_rank.rank_position
                        end as movie2_rank_position,
                        case
                            when first_rank.direct_comparisons
                                < second_rank.direct_comparisons then first_rank.mu
                            when second_rank.direct_comparisons
                                < first_rank.direct_comparisons then second_rank.mu
                            when first_rank.rank_position
                                <= second_rank.rank_position then first_rank.mu
                            else second_rank.mu
                        end as movie1_mu,
                        case
                            when first_rank.direct_comparisons
                                < second_rank.direct_comparisons then second_rank.mu
                            when second_rank.direct_comparisons
                                < first_rank.direct_comparisons then first_rank.mu
                            when first_rank.rank_position
                                <= second_rank.rank_position then second_rank.mu
                            else first_rank.mu
                        end as movie2_mu,
                        least(first_rank.direct_comparisons, second_rank.direct_comparisons)
                            as lower_direct_comparisons,
                        greatest(first_rank.direct_comparisons, second_rank.direct_comparisons)
                            as higher_direct_comparisons,
                        least(first_rank.rank_bucket, second_rank.rank_bucket)
                            as highest_rank_bucket,
                        second_rank.rank_position - first_rank.rank_position as rank_distance,
                        abs(first_rank.score - second_rank.score) as score_distance,
                        %s as pair_information,
                        row_number() over (
                            partition by first_rank.user_id, first_rank.movie_id
                            order by %s
                        ) as candidate_pair_priority
                    from ranked_recommendation first_rank
                    join ranked_recommendation second_rank
                        on second_rank.user_id = first_rank.user_id
                        and second_rank.rank_position > first_rank.rank_position
                        %s
                    where not exists (
                            select 1
                            from user_movie_challenge_vote vote
                            where vote.user_id = first_rank.user_id
                                and vote.winner_id = first_rank.movie_id
                                and vote.loser_id = second_rank.movie_id
                        )
                        and not exists (
                            select 1
                            from user_movie_challenge_vote vote
                            where vote.user_id = first_rank.user_id
                                and vote.winner_id = second_rank.movie_id
                                and vote.loser_id = first_rank.movie_id
                        )
                )
                select selected_pair.user_id,
                    selected_pair.movie1_id,
                    movie1.title as movie1_title,
                    movie1.poster as movie1_poster,
                    movie1.release_year as movie1_year,
                    movie1.director as movie1_director,
                    selected_pair.movie1_rank_position,
                    selected_pair.movie1_mu,
                    movie1_rating.rating as movie1_rating,
                    selected_pair.movie2_id,
                    movie2.title as movie2_title,
                    movie2.poster as movie2_poster,
                    movie2.release_year as movie2_year,
                    movie2.director as movie2_director,
                    selected_pair.movie2_rank_position,
                    selected_pair.movie2_mu,
                    movie2_rating.rating as movie2_rating,
                    selected_pair.lower_direct_comparisons,
                    selected_pair.higher_direct_comparisons,
                    selected_pair.highest_rank_bucket,
                    selected_pair.rank_distance,
                    selected_pair.pair_information,
                    %s as pair_information_priority
                from (
                    select eligible_pair.*,
                        min(eligible_pair.pair_information) over () as min_pair_information,
                        max(eligible_pair.pair_information) over () as max_pair_information
                    from selected_pair eligible_pair
                    where eligible_pair.lower_direct_comparisons >= :explorationDirectComparisons
                        %s
                        %s
                ) selected_pair
                join movies movie1
                    on movie1.imdb_id = selected_pair.movie1_id
                join movies movie2
                    on movie2.imdb_id = selected_pair.movie2_id
                left join user_movie_rating movie1_rating
                    on movie1_rating.user_id = selected_pair.user_id
                    and movie1_rating.movie_id = selected_pair.movie1_id
                left join user_movie_rating movie2_rating
                    on movie2_rating.user_id = selected_pair.user_id
                    and movie2_rating.movie_id = selected_pair.movie2_id
                """.formatted(pairInformationSql,
                candidatePairPriorityOrder,
                closeRankJoinFilter,
                pairInformationPrioritySql,
                stopFilters,
                suggestedListFilters);
    }

    private String refinementChallengeOrderSql() {
        return """
                order by pair_information desc,
                    rank_distance,
                    higher_direct_comparisons,
                    lower_direct_comparisons,
                    movie1_rank_position,
                    movie2_rank_position,
                    movie1_id,
                    movie2_id
                """;
    }

    private String suggestedRefinementChallengeOrderSql(SuggestedChallengeOrdering ordering) {
        if (ordering.prioritizeHigherRanks()) {
            String primaryRankOrder = ordering.useRawBestRank()
                    ? "least(movie1_rank_position, movie2_rank_position)"
                    : "highest_rank_bucket";
            return """
                    order by %s,
                        rank_distance,
                        pair_information desc,
                        movie2_rank_position,
                        higher_direct_comparisons,
                        lower_direct_comparisons,
                        movie1_id,
                        movie2_id
                    """.formatted(primaryRankOrder);
        }
        String interestDirection = ordering.moreInterestingFirst() ? "desc" : "asc";
        return """
                order by pair_information_priority %s,
                    movie1_rank_position,
                    rank_distance,
                    higher_direct_comparisons,
                    lower_direct_comparisons,
                    movie2_rank_position,
                    movie1_id,
                    movie2_id
                """.formatted(interestDirection);
    }

    private String pairInformationConfidencePercentSql() {
        return """
                case
                    when selected_pair.pair_information is null
                        or selected_pair.min_pair_information is null
                        or selected_pair.max_pair_information is null then 0
                    when selected_pair.max_pair_information = selected_pair.min_pair_information then 100
                    else greatest(0, least(100, cast(round(
                        (selected_pair.max_pair_information - selected_pair.pair_information)
                        / nullif(selected_pair.max_pair_information - selected_pair.min_pair_information, 0)
                        * 100
                    ) as integer)))
                end
                """;
    }

    private String pairInformationSortingPrioritySql() {
        return """
                case
                    when selected_pair.pair_information is null
                        or selected_pair.min_pair_information is null
                        or selected_pair.max_pair_information is null then 0
                    when selected_pair.max_pair_information = selected_pair.min_pair_information then 1
                    when round(
                            (selected_pair.max_pair_information - selected_pair.pair_information)
                            / nullif(selected_pair.max_pair_information - selected_pair.min_pair_information, 0)
                            * 100
                        ) > 50 then 1
                    else 0
                end
                """;
    }

    private Map<String, Object> refinementParams(String username) {
        return Map.of(
                "username", username,
                "explorationDirectComparisons", EXPLORATION_DIRECT_COMPARISONS,
                "closeRankWindow", closeRankWindow(username),
                "closeScoreDistance", CLOSE_SCORE_DISTANCE,
                "minimalRefinementPairInformation", MINIMAL_REFINEMENT_PAIR_INFORMATION,
                "suggestedMaxMuDistance", SUGGESTED_MAX_MU_DISTANCE);
    }

    private int closeRankWindow(String username) {
        Integer recommendationCount = jdbcTemplate.queryForObject("""
                select count(1)
                from movie_recommendations
                where user_id = :username
                    and positive = true
                """, Map.of("username", username), Integer.class);
        int roundedWindow = Math.round((float) Optional.ofNullable(recommendationCount).orElse(0)
                / CLOSE_RANK_WINDOW_RECOMMENDATION_DIVISOR);
        return Math.max(CLOSE_RANK_WINDOW_MINIMUM, roundedWindow);
    }

    private enum ChallengeFilterMode {
        NEXT_CHALLENGE(true, false),
        SUGGESTED_LIST(false, true);

        private final boolean refinementStopFilters;
        private final boolean suggestedPairLimit;

        ChallengeFilterMode(boolean refinementStopFilters, boolean suggestedPairLimit) {
            this.refinementStopFilters = refinementStopFilters;
            this.suggestedPairLimit = suggestedPairLimit;
        }

        private boolean applyRefinementStopFilters() {
            return refinementStopFilters;
        }

        private boolean applySuggestedPairLimit() {
            return suggestedPairLimit;
        }
    }

    private enum SuggestedChallengeOrdering {
        PAIR_INFORMATION_FIRST(false, false, false, false),
        MORE_INTERESTING_FIRST(false, false, false, true),
        HIGHER_RANKED_FIRST(true, true, false, false),
        BOOST_HIGHER_RANKS(true, true, true, false);

        private final boolean prioritizeHigherRanks;
        private final boolean preferRankedRefinement;
        private final boolean useRawBestRank;
        private final boolean moreInterestingFirst;

        SuggestedChallengeOrdering(boolean prioritizeHigherRanks, boolean preferRankedRefinement,
                                   boolean useRawBestRank, boolean moreInterestingFirst) {
            this.prioritizeHigherRanks = prioritizeHigherRanks;
            this.preferRankedRefinement = preferRankedRefinement;
            this.useRawBestRank = useRawBestRank;
            this.moreInterestingFirst = moreInterestingFirst;
        }

        private boolean prioritizeHigherRanks() {
            return prioritizeHigherRanks;
        }

        private boolean preferRankedRefinement() {
            return preferRankedRefinement;
        }

        private boolean useRawBestRank() {
            return useRawBestRank;
        }

        private boolean moreInterestingFirst() {
            return moreInterestingFirst;
        }
    }

    private Optional<MovieChallengeDto> queryChallenge(String sql, Map<String, Object> params) {
        return jdbcTemplate.query(sql, params, rs -> {
            if (!rs.next()) {
                return Optional.empty();
            }
            return Optional.of(new MovieChallengeDto(
                    new MovieChallengeMovieDto(
                            rs.getString("movie1_id"),
                            rs.getString("movie1_title"),
                            rs.getString("movie1_poster")),
                    new MovieChallengeMovieDto(
                            rs.getString("movie2_id"),
                            rs.getString("movie2_title"),
                            rs.getString("movie2_poster"))));
        });
    }

    private SuggestedMovieChallengePageDto querySuggestedChallenges(String sql,
                                                                    Map<String, Object> params,
                                                                    long totalCount) {
        List<SuggestedMovieChallengeDto> challenges = jdbcTemplate.query(sql, params, (rs, rowNum) -> {
            BigDecimal movie1Mu = rs.getBigDecimal("movie1_mu");
            BigDecimal movie2Mu = rs.getBigDecimal("movie2_mu");
            int movie1WinProbability = winProbabilityPercent(movie1Mu, movie2Mu);
            int movie2WinProbability = movie1Mu == null || movie2Mu == null ? 50 : 100 - movie1WinProbability;
            return new SuggestedMovieChallengeDto(
                    new SuggestedMovieChallengeMovieDto(
                            rs.getString("movie1_id"),
                            rs.getString("movie1_title"),
                            rs.getString("movie1_poster"),
                            rs.getString("movie1_year"),
                            rs.getString("movie1_director"),
                            movie1WinProbability,
                            nullableInteger(rs, "movie1_rank_position"),
                            rs.getBigDecimal("movie1_rating")),
                    new SuggestedMovieChallengeMovieDto(
                            rs.getString("movie2_id"),
                            rs.getString("movie2_title"),
                            rs.getString("movie2_poster"),
                            rs.getString("movie2_year"),
                            rs.getString("movie2_director"),
                            movie2WinProbability,
                            nullableInteger(rs, "movie2_rank_position"),
                            rs.getBigDecimal("movie2_rating")));
        });
        return new SuggestedMovieChallengePageDto(challenges, totalCount);
    }

    private long countChallenges(String baseSql, Map<String, Object> params) {
        Long count = jdbcTemplate.queryForObject(
                "select count(1) from (" + baseSql + ") suggested_challenge",
                params,
                Long.class);
        return Optional.ofNullable(count).orElse(0L);
    }

    private Map<String, Object> pageParams(Map<String, Object> params, Pageable pageable) {
        Map<String, Object> pageParams = new HashMap<>(params);
        pageParams.put("limit", pageable.getPageSize());
        pageParams.put("offset", pageable.getOffset());
        return pageParams;
    }

    private String singleSql(String baseSql, String orderSql) {
        return baseSql + "\n" + orderSql + "\nlimit 1";
    }

    private String pagedSql(String baseSql, String orderSql) {
        return baseSql + "\n" + orderSql + "\nlimit :limit offset :offset";
    }

    private int winProbabilityPercent(BigDecimal movieMu, BigDecimal opponentMu) {
        if (movieMu == null || opponentMu == null) {
            return 50;
        }
        int percent = (int) Math.round(winProbability(movieMu.doubleValue(), opponentMu.doubleValue()) * 100);
        return Math.max(0, Math.min(100, percent));
    }

    private Integer nullableInteger(ResultSet rs, String columnName) throws SQLException {
        int value = rs.getInt(columnName);
        return rs.wasNull() ? null : value;
    }

    public boolean hasAvailableChallenge(String username) {
        return findNextChallenge(username).isPresent();
    }

    public boolean canRecordWinnerLoser(String username, String winnerId, String loserId) {
        String sql = """
                select case when :winnerId <> :loserId
                    and exists (
                        select 1
                        from movie_recommendations
                        where user_id = :username
                            and movie_id = :winnerId
                            and positive = true
                    )
                    and exists (
                        select 1
                        from movie_recommendations
                        where user_id = :username
                            and movie_id = :loserId
                            and positive = true
                    )
                    and not exists (
                        select 1
                        from user_movie_challenge_vote vote
                        where vote.user_id = :username
                            and vote.winner_id = :winnerId
                            and vote.loser_id = :loserId
                    )
                    and not exists (
                        select 1
                        from user_movie_challenge_vote vote
                        where vote.user_id = :username
                            and vote.winner_id = :loserId
                            and vote.loser_id = :winnerId
                    )
                then true else false end
                """;
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject(sql, params(username, winnerId, loserId), Boolean.class));
    }

    public void insertDirectWinnerLoser(String username, String winnerId, String loserId) {
        String sql = """
                insert into user_movie_challenge_vote (user_id, winner_id, loser_id)
                select :username, :winnerId, :loserId
                where :winnerId <> :loserId
                    and not exists (
                        select 1
                        from user_movie_challenge_vote existing
                        where existing.user_id = :username
                            and existing.winner_id = :winnerId
                            and existing.loser_id = :loserId
                    )
                    and not exists (
                        select 1
                        from user_movie_challenge_vote existing
                        where existing.user_id = :username
                            and existing.winner_id = :loserId
                            and existing.loser_id = :winnerId
                    )
                """;
        int inserted = jdbcTemplate.update(sql, params(username, winnerId, loserId));
        if (inserted > 0) {
            incrementChallengeCount(username, winnerId);
            incrementChallengeCount(username, loserId);
        }
    }

    public void deleteDirectVotesInvolvingMovie(String username, String movieId) {
        jdbcTemplate.update("""
                delete from user_movie_challenge_vote
                where user_id = :username
                    and (winner_id = :movieId or loser_id = :movieId)
                """, params(username, movieId));
    }

    public void rebuildUserMovieChallengeCounts(String username) {
        jdbcTemplate.update("""
                delete from user_movie_challenge
                where user_id = :username
                """, Map.of("username", username));
        jdbcTemplate.update("""
                insert into user_movie_challenge (user_id, movie_id, challenge_count)
                select user_id, movie_id, count(1) as challenge_count
                from (
                    select user_id, winner_id as movie_id
                    from user_movie_challenge_vote
                    where user_id = :username
                    union all
                    select user_id, loser_id as movie_id
                    from user_movie_challenge_vote
                    where user_id = :username
                ) direct_challenge
                group by user_id, movie_id
                """, Map.of("username", username));
    }

    private void incrementChallengeCount(String username, String movieId) {
        int updated = jdbcTemplate.update("""
                update user_movie_challenge
                set challenge_count = challenge_count + 1
                where user_id = :username
                    and movie_id = :movieId
                """, params(username, movieId));
        if (updated == 0) {
            jdbcTemplate.update("""
                    insert into user_movie_challenge (user_id, movie_id, challenge_count)
                    select :username, :movieId, 1
                    where not exists (
                        select 1
                        from user_movie_challenge existing
                        where existing.user_id = :username
                            and existing.movie_id = :movieId
                    )
                    """, params(username, movieId));
        }
    }

    public void rebuildUserMovieRanks(String username) {
        jdbcTemplate.update("delete from user_movie_rank where user_id = :username", Map.of("username", username));

        List<ChallengeVote> votes = directChallengeVotes(username);
        if (votes.isEmpty()) {
            return;
        }

        Map<String, BradleyTerryMovie> movies = bradleyTerryMovies(votes);
        fitBradleyTerryModel(votes, movies);
        List<RankedMovie> rankedMovies = rankedMovies(votes, movies);

        String sql = """
                insert into user_movie_rank (
                    user_id,
                    movie_id,
                    rank_position,
                    score,
                    direct_comparisons,
                    mu,
                    sigma,
                    score_error_80
                )
                values (
                    :username,
                    :movieId,
                    :rankPosition,
                    :score,
                    :directComparisons,
                    :mu,
                    :sigma,
                    :scoreError80
                )
                """;
        @SuppressWarnings("unchecked")
        Map<String, ?>[] batch = rankedMovies.stream()
                .map(movie -> Map.of(
                        "username", username,
                        "movieId", movie.movieId(),
                        "rankPosition", movie.rankPosition(),
                        "score", movie.score(),
                        "directComparisons", movie.directComparisons(),
                        "mu", movie.mu(),
                        "sigma", movie.sigma(),
                        "scoreError80", movie.scoreError80()))
                .toArray(Map[]::new);
        jdbcTemplate.batchUpdate(sql, batch);
    }

    public int voteCount(String username, String movieId) {
        String sql = """
                select count(1)
                from user_movie_challenge_vote
                where user_id = :username
                    and winner_id = :movieId
                """;
        return jdbcTemplate.queryForObject(sql, Map.of("username", username, "movieId", movieId), Integer.class);
    }

    public int challengeCount(String username, String movieId) {
        String sql = """
                select coalesce(max(direct_comparisons), 0)
                from user_movie_rank
                where user_id = :username
                    and movie_id = :movieId
                """;
        return jdbcTemplate.queryForObject(sql, Map.of("username", username, "movieId", movieId), Integer.class);
    }

    public boolean transitiveWinnerLoserExists(String username, String winnerId, String loserId) {
        String sql = """
                select case when exists (
                    select 1
                    from user_movie_rank winner_rank
                    join user_movie_rank loser_rank
                        on loser_rank.user_id = winner_rank.user_id
                    where winner_rank.user_id = :username
                        and winner_rank.movie_id = :winnerId
                        and loser_rank.movie_id = :loserId
                        and winner_rank.rank_position < loser_rank.rank_position
                ) then true else false end
                """;
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject(sql, params(username, winnerId, loserId), Boolean.class));
    }

    public boolean directWinnerLoserExists(String username, String winnerId, String loserId) {
        String sql = """
                select case when exists (
                    select 1
                    from user_movie_challenge_vote
                    where user_id = :username
                        and winner_id = :winnerId
                        and loser_id = :loserId
                ) then true else false end
                """;
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject(sql, params(username, winnerId, loserId), Boolean.class));
    }

    public Optional<MovieRatingDto> movieRating(String username, String movieId) {
        return Optional.ofNullable(movieRatings(username, List.of(movieId)).get(movieId));
    }

    public Map<String, MovieRatingDto> movieRatings(String username, Collection<String> movieIds) {
        if (movieIds.isEmpty()) {
            return Map.of();
        }

        String sql = """
                select movie_id, rank_position, rating
                from user_movie_rating
                where user_id = :username
                    and movie_id in (:movieIds)
                """;
        Map<String, Object> params = Map.of("username", username, "movieIds", movieIds);
        Map<String, MovieRatingDto> ratings = new HashMap<>();
        jdbcTemplate.query(sql, params, (rs, rowNum) -> Map.entry(
                        rs.getString("movie_id"),
                        new MovieRatingDto(rs.getInt("rank_position"), rs.getBigDecimal("rating"))))
                .forEach(rating -> ratings.put(rating.getKey(), rating.getValue()));
        return ratings;
    }

    // "Users Rank" is calculated across the whole catalog before being narrowed to the current page. Its ordering
    // uses the exact Bayesian-popularity formula used by the Home page, while its displayed rating is normalized
    // from that resulting rank: #1 is always 10.00 and the final rated movie is 1.00. The raw Bayesian score is a
    // useful sort signal but not a rank-derived 1..10 rating (and can legitimately put #1 at e.g. 6.6), so it must
    // not be exposed as Users Rating. A movie nobody has rated remains null for both fields.
    public Map<String, MovieRatingDto> usersPopularityRatings(Collection<String> movieIds) {
        if (movieIds.isEmpty()) {
            return Map.of();
        }

        String sql = """
                with user_rating_average as (
                    select rating.user_id, avg(rating.rating) as user_average
                    from user_movie_rating rating group by rating.user_id
                ),
                catalog_prior as (
                    select avg(user_rating_average.user_average) as catalog_average
                    from user_rating_average
                ),
                movie_rating_stats as (
                    select rating.movie_id, avg(rating.rating) as average_rating,
                        count(distinct rating.user_id) as voter_count
                    from user_movie_rating rating group by rating.movie_id
                ),
                ranked as (
                    select m.imdb_id as movie_id,
                        case when movie_rating_stats.voter_count is null then null else row_number() over (
                            order by case when movie_rating_stats.voter_count is null then 1 else 0 end asc,
                                catalog_prior.catalog_average
                                    + (cast(movie_rating_stats.voter_count as numeric(12,6))
                                        / cast(movie_rating_stats.voter_count + :priorWeight as numeric(12,6)))
                                    * (movie_rating_stats.average_rating - catalog_prior.catalog_average) desc,
                                movie_rating_stats.voter_count desc,
                                movie_rating_stats.average_rating desc,
                                regexp_replace(lower(m.title), '^(the|a)[[:space:]]+', '') asc,
                                lower(m.title) asc,
                                m.imdb_id asc
                        ) end as users_rank_position
                    from movies m
                    left join movie_rating_stats on movie_rating_stats.movie_id = m.imdb_id
                    cross join catalog_prior
                ),
                rated as (
                    select movie_id, users_rank_position,
                        case
                            when users_rank_position is null then null
                            when max(users_rank_position) over () = 1 then cast(10.00 as numeric(4,2))
                            else cast(round(
                                cast(10 as numeric(12,6))
                                    - cast(9 as numeric(12,6))
                                        * cast(users_rank_position - 1 as numeric(12,6))
                                        / cast(max(users_rank_position) over () - 1 as numeric(12,6)),
                                2) as numeric(4,2))
                        end as users_rating
                    from ranked
                )
                select movie_id, users_rank_position, users_rating
                from rated
                where movie_id in (:movieIds)
                """;
        Map<String, Object> params = Map.of("priorWeight", MovieService.HOMEPAGE_RATING_PRIOR_WEIGHT, "movieIds", movieIds);
        Map<String, MovieRatingDto> ratings = new HashMap<>();
        jdbcTemplate.query(sql, params, (rs, rowNum) -> {
                    int rankPosition = rs.getInt("users_rank_position");
                    return Map.entry(rs.getString("movie_id"),
                            new MovieRatingDto(rs.wasNull() ? null : rankPosition, rs.getBigDecimal("users_rating")));
                })
                .forEach(rating -> ratings.put(rating.getKey(), rating.getValue()));
        return ratings;
    }

    private List<ChallengeVote> directChallengeVotes(String username) {
        return jdbcTemplate.query("""
                select winner_id, loser_id
                from user_movie_challenge_vote
                where user_id = :username
                """, Map.of("username", username), (rs, rowNum) -> new ChallengeVote(
                rs.getString("winner_id"),
                rs.getString("loser_id")));
    }

    private Map<String, BradleyTerryMovie> bradleyTerryMovies(List<ChallengeVote> votes) {
        Map<String, BradleyTerryMovie> movies = new HashMap<>();
        for (ChallengeVote vote : votes) {
            BradleyTerryMovie winner = movies.computeIfAbsent(vote.winnerId(), BradleyTerryMovie::new);
            BradleyTerryMovie loser = movies.computeIfAbsent(vote.loserId(), BradleyTerryMovie::new);
            winner.wins++;
            winner.directComparisons++;
            loser.directComparisons++;
        }
        return movies;
    }

    private void fitBradleyTerryModel(List<ChallengeVote> votes, Map<String, BradleyTerryMovie> movies) {
        for (int iteration = 0; iteration < BRADLEY_TERRY_ITERATIONS; iteration++) {
            movies.values().forEach(movie -> {
                movie.gradient = -BRADLEY_TERRY_PRIOR_PRECISION * movie.mu;
                movie.curvature = BRADLEY_TERRY_PRIOR_PRECISION;
            });

            for (ChallengeVote vote : votes) {
                BradleyTerryMovie winner = movies.get(vote.winnerId());
                BradleyTerryMovie loser = movies.get(vote.loserId());
                double probability = winProbability(winner.mu, loser.mu);
                double residual = 1 - probability;
                double information = probability * (1 - probability);

                winner.gradient += residual;
                loser.gradient -= residual;
                winner.curvature += information;
                loser.curvature += information;
            }

            movies.values().forEach(movie -> {
                double update = movie.gradient / movie.curvature;
                update = Math.max(-BRADLEY_TERRY_MAX_UPDATE, Math.min(BRADLEY_TERRY_MAX_UPDATE, update));
                movie.mu += update;
            });
            centerBradleyTerryMu(movies.values());
        }
    }

    private List<RankedMovie> rankedMovies(List<ChallengeVote> votes, Map<String, BradleyTerryMovie> movies) {
        recomputeBradleyTerryPrecision(votes, movies);
        List<BradleyTerryMovie> sortedMovies = new ArrayList<>(movies.values());
        sortedMovies.forEach(movie -> movie.score = expectedScore(movie, sortedMovies));
        sortedMovies.sort(Comparator
                .comparingDouble((BradleyTerryMovie movie) -> movie.score).reversed()
                .thenComparing((BradleyTerryMovie movie) -> movie.wins, Comparator.reverseOrder())
                .thenComparing((BradleyTerryMovie movie) -> movie.directComparisons, Comparator.reverseOrder())
                .thenComparing(movie -> movie.movieId));

        List<RankedMovie> rankedMovies = new ArrayList<>();
        for (int index = 0; index < sortedMovies.size(); index++) {
            BradleyTerryMovie movie = sortedMovies.get(index);
            double sigma = 1 / Math.sqrt(movie.precision);
            double scoreError80 = Math.min(9, SCORE_ERROR_80_PER_SIGMA * sigma);
            rankedMovies.add(new RankedMovie(
                    movie.movieId,
                    index + 1,
                    decimal(movie.score),
                    movie.directComparisons,
                    decimal(movie.mu),
                    decimal(sigma),
                    decimal(scoreError80)));
        }
        return rankedMovies;
    }

    private void recomputeBradleyTerryPrecision(List<ChallengeVote> votes, Map<String, BradleyTerryMovie> movies) {
        movies.values().forEach(movie -> movie.precision = BRADLEY_TERRY_PRIOR_PRECISION);
        for (ChallengeVote vote : votes) {
            BradleyTerryMovie winner = movies.get(vote.winnerId());
            BradleyTerryMovie loser = movies.get(vote.loserId());
            double probability = winProbability(winner.mu, loser.mu);
            double information = probability * (1 - probability);
            winner.precision += information;
            loser.precision += information;
        }
    }

    private double expectedScore(BradleyTerryMovie movie, List<BradleyTerryMovie> movies) {
        if (movies.size() == 1) {
            return 10;
        }
        double expectedWinShare = 0;
        for (BradleyTerryMovie otherMovie : movies) {
            if (!otherMovie.movieId.equals(movie.movieId)) {
                expectedWinShare += winProbability(movie.mu, otherMovie.mu);
            }
        }
        return 1 + 9 * expectedWinShare / (movies.size() - 1);
    }

    private double winProbability(double winnerMu, double loserMu) {
        double difference = Math.max(-30, Math.min(30, winnerMu - loserMu));
        return 1 / (1 + Math.exp(-difference));
    }

    private void centerBradleyTerryMu(Collection<BradleyTerryMovie> movies) {
        double average = movies.stream()
                .mapToDouble(movie -> movie.mu)
                .average()
                .orElse(0);
        movies.forEach(movie -> movie.mu -= average);
    }

    private BigDecimal decimal(double value) {
        return BigDecimal.valueOf(value).setScale(6, RoundingMode.HALF_UP);
    }

    private Map<String, Object> params(String username, String winnerId, String loserId) {
        return Map.of("username", username, "winnerId", winnerId, "loserId", loserId);
    }

    private Map<String, Object> params(String username, String movieId) {
        return Map.of("username", username, "movieId", movieId);
    }

    private record ChallengeVote(String winnerId, String loserId) {
    }

    private record RankedMovie(String movieId,
                               int rankPosition,
                               BigDecimal score,
                               int directComparisons,
                               BigDecimal mu,
                               BigDecimal sigma,
                               BigDecimal scoreError80) {
    }

    private static class BradleyTerryMovie {
        private final String movieId;
        private int wins;
        private int directComparisons;
        private double mu;
        private double gradient;
        private double curvature;
        private double precision;
        private double score;

        private BradleyTerryMovie(String movieId) {
            this.movieId = movieId;
        }
    }
}
