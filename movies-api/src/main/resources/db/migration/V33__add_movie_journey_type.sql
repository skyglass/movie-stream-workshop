alter table movie_journey add column type integer not null default 0;
alter table movie_journey add constraint movie_journey_type_check check (type in (0, 1, 2, 3, 4));
