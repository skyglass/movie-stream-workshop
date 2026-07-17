-- Removes the "Year" category and every sub-category under it (e.g. Year.1999), along with every movie's
-- assignment to them. A yearly breakdown grew to 100+ sub-categories and wasn't useful for browsing/filtering,
-- so it's dropped in favor of the remaining Directors / Writers / Genres roots. category_parent_child,
-- category_parent_child_all and movie_category all cascade on category deletion (see V34__movie_categories.sql),
-- so deleting the matched category rows is sufficient to clean up every reference.
delete from category
where id in (
    select pca.descendant_id
    from category_parent_child_all pca
    join category c on c.id = pca.ancestor_id
    join category_parent_child pc on pc.child_id = c.id and pc.parent_id = c.id
    where c.name = 'Year'
);

-- Stops auto-recreating "Year" on future movie insert/update; previously kept it in sync with release_year.
create or replace function sync_movie_categories() returns trigger
    language plpgsql
as $$
declare
    v_directors_id bigint;
    v_writers_id bigint;
    v_genres_id bigint;
begin
    v_directors_id := get_or_create_category(null, 'Directors', '🎬');
    v_writers_id := get_or_create_category(null, 'Writers', '✍️');
    v_genres_id := get_or_create_category(null, 'Genres', '🎭');

    if tg_op = 'INSERT' then
        perform sync_movie_category_field(new.imdb_id, v_directors_id, null, new.director, true, true);
        perform sync_movie_category_field(new.imdb_id, v_writers_id, null, new.writer, true, true);
        perform sync_movie_category_field(new.imdb_id, v_genres_id, null, new.genre, false, true);
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
    end if;

    return new;
end;
$$;

drop trigger if exists trg_sync_movie_categories on movies;

create trigger trg_sync_movie_categories
    after insert or update of director, writer, genre on movies
    for each row
    execute function sync_movie_categories();
