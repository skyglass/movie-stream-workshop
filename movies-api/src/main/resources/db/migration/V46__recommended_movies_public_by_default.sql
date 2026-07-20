-- "Make Your Recommended Movies Private" is unchecked by default (unlike favorites, which start private):
-- Recommended Movies starts public for every user. A separate migration rather than editing V45 in place, since
-- V45 may already be applied in existing environments (editing an applied migration breaks Flyway's checksum
-- validation on next startup).
alter table user_settings alter column is_my_recommended_movies_public set default true;
update user_settings set is_my_recommended_movies_public = true;
