alter table movie_guide add column status integer not null default 0;
alter table movie_guide add constraint movie_guide_status_check check (status in (0, 1));

-- "Guide" or "Personality" -- was previously only implied by which root category (Guides/Personalities) a guide's
-- anchor category lived under; persisted directly now so it doesn't require walking the category tree to know.
alter table movie_guide add column type varchar(20) not null default 'Guide';
alter table movie_guide add constraint movie_guide_type_check check (type in ('Guide', 'Personality'));
