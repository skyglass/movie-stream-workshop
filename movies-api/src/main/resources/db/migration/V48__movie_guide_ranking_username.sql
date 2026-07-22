-- Links a Movie Personality to its synthetic "ranked as this personality" user, created on the first successful
-- ranking submit and reused thereafter (see MovieGuideService.submitRanking). Nullable: most guides/personalities
-- never have one. The partial unique index is the DB-level backstop against slug collisions between two
-- personalities whose names happen to dash-slugify to the same username.
alter table movie_guide add column ranking_username varchar(255) references users (username);

create unique index uq_movie_guide_ranking_username on movie_guide (ranking_username) where ranking_username is not null;
