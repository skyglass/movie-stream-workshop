create table movie_course (
    id bigserial primary key,
    title varchar(200) not null,
    description text not null default '',
    creator_id varchar(255) not null,
    created_at timestamp with time zone not null default current_timestamp
);

create table movie_course_movie (
    course_id bigint not null references movie_course(id) on delete cascade,
    movie_id varchar(255) not null references movies(imdb_id) on delete cascade,
    description text not null default '',
    watch_order integer not null,
    linked_course_id bigint references movie_course(id) on delete set null,
    primary key (course_id, movie_id),
    constraint uq_movie_course_watch_order unique (course_id, watch_order),
    constraint chk_movie_course_watch_order_positive check (watch_order > 0)
);

create table user_movie_course (
    user_id varchar(255) not null,
    course_id bigint not null references movie_course(id) on delete cascade,
    applied_at timestamp with time zone not null default current_timestamp,
    primary key (user_id, course_id)
);

create index idx_movie_course_creator on movie_course(creator_id);
create index idx_movie_course_movie_link on movie_course_movie(linked_course_id);
create index idx_user_movie_course_course on user_movie_course(course_id);
