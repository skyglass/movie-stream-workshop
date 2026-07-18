-- Converts movie_guide.type from a free-form varchar into a persisted ordinal enum (0 = Guide, 1 = Personality),
-- matching MovieGuideType.java -- never reorder or remove existing values there, only ever append new ones at
-- the end, since the ordinal stored here is what's actually persisted.
alter table movie_guide drop constraint movie_guide_type_check;
alter table movie_guide alter column type drop default;
alter table movie_guide alter column type type smallint using (case type when 'Guide' then 0 when 'Personality' then 1 end);
alter table movie_guide alter column type set default 0;
alter table movie_guide add constraint movie_guide_type_check check (type in (0, 1));
