-- Guide/Personality names must be globally unique (case-insensitive) so "My Watchlists" name-conflict handling
-- has a consistent counterpart on movie_guide. Fails loudly if existing data already has duplicate names --
-- that would need a manual rename before this migration can apply.
create unique index uq_movie_guide_name on movie_guide (lower(name));
