-- Mirrors is_my_favorite_movies_public (V23): lets a user publish a read-only, shareable link to their own
-- Recommended Movies page ("Share" -> /my-recommended-movies/{username}), same public/private toggle mechanism.
alter table user_settings add column is_my_recommended_movies_public boolean not null default false;
