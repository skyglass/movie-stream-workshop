create unique index idx_users_email_unique on users (email);

create table movie_recommendations (
    user_id varchar(255) not null references users (username) on delete cascade,
    movie_id varchar(32) not null references movies (imdb_id) on delete cascade,
    primary key (user_id, movie_id)
);

create index idx_movie_recommendations_movie_id on movie_recommendations (movie_id);
