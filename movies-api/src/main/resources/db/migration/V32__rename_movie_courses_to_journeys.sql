alter table movie_course rename to movie_journey;
alter table movie_course_movie rename to movie_journey_movie;
alter table user_movie_course rename to user_movie_journey;

alter table movie_journey rename column course_creator to journey_creator;
alter table movie_journey_movie rename column course_id to journey_id;
alter table movie_journey_movie rename column linked_course_id to linked_journey_id;
alter table user_movie_journey rename column movie_course_id to movie_journey_id;
