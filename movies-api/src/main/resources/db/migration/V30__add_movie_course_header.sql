alter table movie_course add column header varchar(200);
update movie_course set header = title;
alter table movie_course alter column header set not null;
