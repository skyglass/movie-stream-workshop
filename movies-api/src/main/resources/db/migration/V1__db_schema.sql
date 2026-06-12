create table movies (
    imdb_id varchar(32) primary key,
    title varchar(255) not null,
    director varchar(255) not null,
    release_year varchar(32) not null,
    poster varchar(2048)
);

create table movie_comments (
    id bigserial primary key,
    movie_imdb_id varchar(32) not null references movies (imdb_id) on delete cascade,
    username varchar(255) not null,
    text varchar(4000) not null,
    timestamp timestamptz not null
);

create index idx_movie_comments_movie_imdb_id on movie_comments (movie_imdb_id);
create index idx_movie_comments_timestamp on movie_comments (timestamp desc);

create table users (
    username varchar(255) primary key,
    email varchar(320) not null,
    avatar varchar(255) not null
);

insert into users (username, email, avatar) values
    ('admin', 'admin@example.com', 'admin'),
    ('user', 'user@example.com', 'user');
