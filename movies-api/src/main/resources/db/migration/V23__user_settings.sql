create table user_settings (
    username varchar(255) primary key references users (username) on delete cascade,
    is_my_favorite_movies_public boolean not null default false
);

insert into user_settings (username, is_my_favorite_movies_public)
select username, false
from users;
