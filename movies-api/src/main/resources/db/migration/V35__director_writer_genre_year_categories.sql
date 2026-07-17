-- Finds an existing child category by name under p_parent_id (or an existing root when p_parent_id is null,
-- matching the self-referencing root convention already used by CategoryService.setParent), otherwise creates
-- it and maintains category_parent_child / category_parent_child_all incrementally.
create or replace function get_or_create_category(p_parent_id bigint, p_name varchar, p_icon varchar default null)
    returns bigint
    language plpgsql
as $$
declare
    v_id bigint;
begin
    -- category.name has no unique constraint, so guard concurrent creators of the same name/parent.
    perform pg_advisory_xact_lock(hashtext(coalesce(p_parent_id::text, 'root') || ':' || lower(p_name)));

    select c.id into v_id
    from category c
    join category_parent_child pc on pc.child_id = c.id
    where pc.parent_id = coalesce(p_parent_id, c.id)
      and lower(c.name) = lower(p_name)
    limit 1;

    if v_id is not null then
        return v_id;
    end if;

    insert into category (name, icon) values (p_name, p_icon) returning id into v_id;

    insert into category_parent_child_all (ancestor_id, descendant_id) values (v_id, v_id);
    insert into category_parent_child (parent_id, child_id) values (coalesce(p_parent_id, v_id), v_id);

    if p_parent_id is not null then
        insert into category_parent_child_all (ancestor_id, descendant_id)
        select ancestor_id, v_id from category_parent_child_all where descendant_id = p_parent_id;
    end if;

    return v_id;
end;
$$;

-- Splits a comma-separated movie field into distinct, trimmed, non-blank tokens. When p_strip_annotations is
-- true, trailing "(...)" annotations (e.g. "Jonathan Nolan (story)") are stripped from each token.
create or replace function movie_field_tokens(p_value varchar, p_strip_annotations boolean default false)
    returns table(token text)
    language sql
    immutable
as $$
    select distinct trimmed
    from (
        select case when p_strip_annotations
                    then trim(regexp_replace(t, '\s*\([^)]*\)\s*$', ''))
                    else trim(t)
               end as trimmed
        from regexp_split_to_table(coalesce(p_value, ''), '\s*,\s*') as t
    ) tokens
    where trimmed <> ''
$$;

-- Syncs a single movie field (director/writer/genre/release_year) against its root category: on update, drops
-- links to categories for tokens that were present in p_old_value but are gone from p_new_value, then links
-- (creating categories as needed) every token found in p_new_value.
create or replace function sync_movie_category_field(
    p_movie_id varchar,
    p_root_id bigint,
    p_old_value varchar,
    p_new_value varchar,
    p_strip_annotations boolean,
    p_is_insert boolean
) returns void
    language plpgsql
as $$
declare
    v_token record;
    v_category_id bigint;
begin
    if not p_is_insert then
        delete from movie_category mc
        using category c
        join category_parent_child pc on pc.child_id = c.id
        where mc.movie_id = p_movie_id
          and mc.category_id = c.id
          and pc.parent_id = p_root_id
          and lower(c.name) in (select lower(token) from movie_field_tokens(p_old_value, p_strip_annotations))
          and lower(c.name) not in (select lower(token) from movie_field_tokens(p_new_value, p_strip_annotations));
    end if;

    for v_token in select token from movie_field_tokens(p_new_value, p_strip_annotations) loop
        v_category_id := get_or_create_category(p_root_id, v_token.token, null);
        insert into movie_category (movie_id, category_id) values (p_movie_id, v_category_id) on conflict do nothing;
    end loop;
end;
$$;

-- One-time backfill of the existing catalog into Directors / Writers / Genres / Year.
do $$
declare
    v_directors_id bigint;
    v_writers_id bigint;
    v_genres_id bigint;
    v_year_id bigint;
    m record;
begin
    v_directors_id := get_or_create_category(null, 'Directors', '🎬');
    v_writers_id := get_or_create_category(null, 'Writers', '✍️');
    v_genres_id := get_or_create_category(null, 'Genres', '🎭');
    v_year_id := get_or_create_category(null, 'Year', '📅');

    for m in select imdb_id, director, writer, genre, release_year from movies loop
        perform sync_movie_category_field(m.imdb_id, v_directors_id, null, m.director, true, true);
        perform sync_movie_category_field(m.imdb_id, v_writers_id, null, m.writer, true, true);
        perform sync_movie_category_field(m.imdb_id, v_genres_id, null, m.genre, false, true);
        perform sync_movie_category_field(m.imdb_id, v_year_id, null, m.release_year, false, true);
    end loop;
end;
$$;

-- Keeps Directors / Writers / Genres / Year in sync on every future movie insert/update. Runs as part of the
-- same transaction as the triggering statement (standard Postgres AFTER ROW trigger semantics), so a failure
-- here rolls back the movie write too.
create or replace function sync_movie_categories() returns trigger
    language plpgsql
as $$
declare
    v_directors_id bigint;
    v_writers_id bigint;
    v_genres_id bigint;
    v_year_id bigint;
begin
    v_directors_id := get_or_create_category(null, 'Directors', '🎬');
    v_writers_id := get_or_create_category(null, 'Writers', '✍️');
    v_genres_id := get_or_create_category(null, 'Genres', '🎭');
    v_year_id := get_or_create_category(null, 'Year', '📅');

    if tg_op = 'INSERT' then
        perform sync_movie_category_field(new.imdb_id, v_directors_id, null, new.director, true, true);
        perform sync_movie_category_field(new.imdb_id, v_writers_id, null, new.writer, true, true);
        perform sync_movie_category_field(new.imdb_id, v_genres_id, null, new.genre, false, true);
        perform sync_movie_category_field(new.imdb_id, v_year_id, null, new.release_year, false, true);
    elsif tg_op = 'UPDATE' then
        if new.director is distinct from old.director then
            perform sync_movie_category_field(new.imdb_id, v_directors_id, old.director, new.director, true, false);
        end if;
        if new.writer is distinct from old.writer then
            perform sync_movie_category_field(new.imdb_id, v_writers_id, old.writer, new.writer, true, false);
        end if;
        if new.genre is distinct from old.genre then
            perform sync_movie_category_field(new.imdb_id, v_genres_id, old.genre, new.genre, false, false);
        end if;
        if new.release_year is distinct from old.release_year then
            perform sync_movie_category_field(new.imdb_id, v_year_id, old.release_year, new.release_year, false, false);
        end if;
    end if;

    return new;
end;
$$;

drop trigger if exists trg_sync_movie_categories on movies;

create trigger trg_sync_movie_categories
    after insert or update of director, writer, genre, release_year on movies
    for each row
    execute function sync_movie_categories();
