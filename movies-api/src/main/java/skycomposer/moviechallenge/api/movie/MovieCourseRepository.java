package skycomposer.moviechallenge.api.movie;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import skycomposer.moviechallenge.api.movie.dto.AddCourseMovieRequest;
import skycomposer.moviechallenge.api.movie.dto.CreateMovieCourseRequest;
import skycomposer.moviechallenge.api.movie.dto.MovieCourseDto;
import skycomposer.moviechallenge.api.movie.model.MovieJourneyType;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

@RequiredArgsConstructor
@Repository
public class MovieCourseRepository {
    private final JdbcClient jdbc;

    public List<MovieCourseDto> findAll(String user) {
        return jdbc.sql("""
                select c.id, c.header, c.title, c.description, c.type, c.journey_creator,
                       exists(select 1 from user_movie_journey uc where uc.movie_journey_id=c.id and uc.user_id=:user) applied,
                       count(distinct cm.movie_id) movie_count,
                       coalesce(avg(ur.rating), 0) average_rating
                from movie_journey c
                left join movie_journey_movie cm on cm.journey_id=c.id
                left join user_movie_rating ur on ur.user_id=:user and ur.movie_id=cm.movie_id
                group by c.id
                order by average_rating desc, lower(c.title), c.id
                """).param("user", user).query((rs, n) -> new MovieCourseDto(
                rs.getLong("id"), rs.getString("header"), rs.getString("title"), rs.getString("description"),
                journeyType(rs.getInt("type")), journeyType(rs.getInt("type")).getDescription(), rs.getString("journey_creator"),
                Objects.equals(user, rs.getString("journey_creator")), rs.getBoolean("applied"), false,
                rs.getBigDecimal("average_rating"), rs.getInt("movie_count"), List.of(), List.of())).list();
    }

    public MovieCourseDto find(long id, String user) {
        MovieCourseDto course = jdbc.sql("""
                select c.*, exists(select 1 from user_movie_journey uc where uc.movie_journey_id=c.id and uc.user_id=:user) applied
                from movie_journey c where c.id=:id
                """).param("id", id).param("user", user).query((rs, n) -> new MovieCourseDto(
                rs.getLong("id"), rs.getString("header"), rs.getString("title"), rs.getString("description"),
                journeyType(rs.getInt("type")), journeyType(rs.getInt("type")).getDescription(), rs.getString("journey_creator"),
                Objects.equals(user, rs.getString("journey_creator")), rs.getBoolean("applied"), false, null, 0, List.of(), List.of()))
                .optional().orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Movie Journey not found"));
        List<MovieCourseDto.CourseMovieDto> movies = jdbc.sql("""
                select m.imdb_id, m.title, cm.header, cm.description, m.release_year, m.director, m.writer, m.genre, m.poster,
                       cm.watch_order, cm.linked_journey_id, linked.title linked_course_title,
                       coalesce(r.positive=false, false) disliked, coalesce(r.positive=true, false) liked,
                       ur.rank_position, ur.rating
                from movie_journey_movie cm join movies m on m.imdb_id=cm.movie_id
                left join movie_journey linked on linked.id=cm.linked_journey_id
                left join movie_recommendations r on r.movie_id=m.imdb_id and r.user_id=:user
                left join user_movie_rating ur on ur.movie_id=m.imdb_id and ur.user_id=:user
                where cm.journey_id=:id order by cm.watch_order
                """).param("id", id).param("user", user).query((rs, n) -> new MovieCourseDto.CourseMovieDto(
                rs.getString("imdb_id"), rs.getString("title"), rs.getString("header"), rs.getString("description"),
                rs.getString("release_year"), rs.getString("director"), rs.getString("writer"),
                rs.getString("genre"), rs.getString("poster"), rs.getInt("watch_order"),
                (Long) rs.getObject("linked_journey_id"), rs.getString("linked_course_title"),
                rs.getBoolean("liked"), rs.getBoolean("disliked"), (Integer) rs.getObject("rank_position"),
                rs.getBigDecimal("rating"))).list();
        boolean expert = course.applied() && !movies.isEmpty() && movies.stream().allMatch(m -> m.liked() || m.disliked());
        List<MovieCourseDto.CourseSuggestionDto> suggestions = expert ? jdbc.sql("""
                select distinct linked.id, linked.title from movie_journey_movie cm
                join movie_journey linked on linked.id=cm.linked_journey_id
                where cm.journey_id=:id and not exists (
                    select 1 from user_movie_journey uc where uc.movie_journey_id=linked.id and uc.user_id=:user)
                order by linked.title
                """).param("id", id).param("user", user).query((rs, n) ->
                new MovieCourseDto.CourseSuggestionDto(rs.getLong("id"), rs.getString("title"))).list() : List.of();
        return new MovieCourseDto(course.id(), course.header(), course.title(), course.description(), course.type(),
                course.typeDescription(), course.creator(), course.owner(),
                course.applied(), expert, course.averageRating(), movies.size(), movies, suggestions);
    }

    @Transactional
    public MovieCourseDto create(CreateMovieCourseRequest request, String user) {
        long id = jdbc.sql("insert into movie_journey(header, title, description, type, journey_creator) values (:header,:title,:description,:type,:user) returning id")
                .param("header", request.header().trim()).param("title", request.title().trim())
                .param("description", text(request.description())).param("type", request.type().getCode()).param("user", user)
                .query(Long.class).single();
        return find(id, user);
    }

    public void requireOwner(long id, String user) {
        String owner = jdbc.sql("select journey_creator from movie_journey where id=:id").param("id", id).query(String.class)
                .optional().orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Movie Journey not found"));
        if (!owner.equals(user)) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the journey creator can edit it");
    }

    public MovieCourseDto update(long id, CreateMovieCourseRequest request, String user) {
        requireOwner(id, user);
        jdbc.sql("update movie_journey set header=:header, title=:title, description=:description, type=:type where id=:id")
                .param("header", request.header().trim()).param("title", request.title().trim())
                .param("description", text(request.description())).param("type", request.type().getCode()).param("id", id).update();
        return find(id, user);
    }

    @Transactional
    public void delete(long id, String user) {
        requireOwner(id, user);
        jdbc.sql("delete from movie_journey where id=:id").param("id", id).update();
    }

    public MovieCourseDto apply(long id, String user) {
        MovieCourseDto course = find(id, user);
        if (course.owner()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Journey creators cannot start their own journey");
        }
        jdbc.sql("insert into user_movie_journey(user_id,movie_journey_id) values (:user,:id) on conflict do nothing")
                .param("user", user).param("id", id).update();
        return find(id, user);
    }

    @Transactional
    public MovieCourseDto addMovie(long id, AddCourseMovieRequest request, String user) {
        requireOwner(id, user);
        validateLinkedCourse(request.linkedCourseId());
        jdbc.sql("select id from movie_journey where id=:id for update").param("id", id).query(Long.class).single();
        int nextWatchOrder = jdbc.sql("select coalesce(max(watch_order), 0) + 1 from movie_journey_movie where journey_id=:id")
                .param("id", id).query(Integer.class).single();
        try {
            jdbc.sql("""
                    insert into movie_journey_movie(journey_id,movie_id,header,description,watch_order,linked_journey_id)
                    values (:id,:movie,:header,:description,:watchOrder,:linkedCourse)
                    """).param("id", id).param("movie", request.movieId()).param("header", text(request.header()))
                    .param("description", text(request.description()))
                    .param("watchOrder", nextWatchOrder).param("linkedCourse", request.linkedCourseId()).update();
        } catch (DuplicateKeyException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Movie or watch order is already in this journey");
        }
        return find(id, user);
    }

    @Transactional
    public MovieCourseDto updateMovie(long id, String movieId, AddCourseMovieRequest request, String user) {
        requireOwner(id, user);
        int changed = jdbc.sql("""
                update movie_journey_movie set header=:header, description=:description
                where journey_id=:id and movie_id=:movie
                """).param("header", text(request.header())).param("description", text(request.description()))
                .param("id", id).param("movie", movieId).update();
        if (changed == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Journey movie not found");
        return find(id, user);
    }

    @Transactional
    public MovieCourseDto removeMovie(long id, String movieId, String user) {
        requireOwner(id, user);
        Integer removedOrder = jdbc.sql("select watch_order from movie_journey_movie where journey_id=:id and movie_id=:movie")
                .param("id", id).param("movie", movieId).query(Integer.class).optional().orElse(null);
        jdbc.sql("delete from movie_journey_movie where journey_id=:id and movie_id=:movie")
                .param("id", id).param("movie", movieId).update();
        if (removedOrder != null) {
            jdbc.sql("update movie_journey_movie set watch_order=watch_order-1 where journey_id=:id and watch_order>:removedOrder")
                    .param("id", id).param("removedOrder", removedOrder).update();
        }
        return find(id, user);
    }

    private void validateLinkedCourse(Long id) {
        if (id != null && !jdbc.sql("select exists(select 1 from movie_journey where id=:id)").param("id", id).query(Boolean.class).single())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Linked Movie Journey not found");
    }
    private String text(String value) { return value == null ? "" : value.trim(); }
    private MovieJourneyType journeyType(int code) { return MovieJourneyType.fromCode(code); }
}
