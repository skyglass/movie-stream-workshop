-- Movie Journeys (originally "Movie Courses", renamed in V32) is being retired in favor of Movie Guides, Movie
-- Personalities, and Private Watchlists. Children first (both hold FKs into movie_journey), then the anchor
-- table itself -- their own PKs/FKs/indexes/constraints are dropped automatically along with each table.
drop table if exists movie_journey_movie;
drop table if exists user_movie_journey;
drop table if exists movie_journey;
