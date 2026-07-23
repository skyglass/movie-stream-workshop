-- Converts every existing "subscribe" pointer (the DAG copy-link for guides, the flat pointer for watchlists)
-- into a one-component OR-composition category, then drops the old subscribe infrastructure entirely. No
-- production data is at stake (workshop project) -- this is a clean one-shot transform, not a placeholder.

-- Guides/Personalities: each movie_guide_default_category reference row becomes a new OR-category, named after
-- the category it originally pointed at, parented under the guide's own root, with that original category as its
-- one component. The old copy-edge (guide root -> referenced category) is removed since the new OR-category now
-- occupies that spot in the tree instead.
do $$
declare
    row record;
    new_id bigint;
begin
    for row in
        select d.movie_guide_id, d.category_id as referenced_id, g.category_id as guide_root_id,
               c.name, c.description, c.icon
        from movie_guide_default_category d
        join movie_guide g on g.id = d.movie_guide_id
        join category c on c.id = d.category_id
        where d.referenced_category_id is not null
    loop
        insert into category(name, description, icon) values (row.name, row.description, row.icon) returning id into new_id;
        insert into category_parent_child(parent_id, child_id) values (row.guide_root_id, new_id);
        insert into composition_category(category_id, operator) values (new_id, 0); -- Operator.OR
        insert into composition_category_component(composition_category_id, component_category_id) values (new_id, row.referenced_id);

        delete from category_parent_child where parent_id = row.guide_root_id and child_id = row.referenced_id;
    end loop;
end $$;

-- Watchlists: each movie_watchlist_default_category row becomes a new private OR-category, parented under the
-- watchlist's own root, with the original PUBLIC category as its one (public) component.
do $$
declare
    row record;
    new_id bigint;
begin
    for row in
        select d.watchlist_id, d.category_id as referenced_id, w.category_id as watchlist_root_id,
               c.name, c.description, c.icon
        from movie_watchlist_default_category d
        join user_movie_watchlist w on w.id = d.watchlist_id
        join category c on c.id = d.category_id
    loop
        insert into private_category(name, description, icon) values (row.name, row.description, row.icon) returning id into new_id;
        insert into private_category_parent_child(parent_id, child_id) values (row.watchlist_root_id, new_id);
        insert into private_composition_category(private_category_id, operator) values (new_id, 0); -- Operator.OR
        insert into private_composition_category_component(composition_category_id, public_component_category_id) values (new_id, row.referenced_id);
    end loop;
end $$;

drop table movie_guide_default_category;
drop table movie_watchlist_default_category;

-- Both tables' rebuild-closure calls run in Java right after this migration's transaction commits (Flyway itself
-- doesn't invoke application code), so do it here too, exactly matching CategoryService.rebuildClosure()/
-- PrivateCategoryService.rebuildClosure()'s own logic -- the category_parent_child edits above need the closure
-- tables to reflect them before anything queries category_parent_child_all.
delete from category_parent_child_all;
insert into category_parent_child_all(ancestor_id, descendant_id) select id, id from category;
insert into category_parent_child_all(ancestor_id, descendant_id)
    select parent_id, child_id from category_parent_child where parent_id <> child_id;

do $$
declare
    inserted_count int;
begin
    loop
        insert into category_parent_child_all(ancestor_id, descendant_id)
        select distinct ancestor.ancestor_id, descendant.descendant_id
        from category_parent_child_all ancestor
        join category_parent_child_all descendant on descendant.ancestor_id = ancestor.descendant_id
        where ancestor.ancestor_id <> descendant.descendant_id
          and not exists (select 1 from category_parent_child_all existing
              where existing.ancestor_id = ancestor.ancestor_id and existing.descendant_id = descendant.descendant_id);
        get diagnostics inserted_count = row_count;
        exit when inserted_count = 0;
    end loop;
end $$;

-- Same rebuild for the private closure -- the new watchlist OR-categories added private_category_parent_child rows too.
delete from private_category_parent_child_all;
insert into private_category_parent_child_all(ancestor_id, descendant_id) select id, id from private_category;
insert into private_category_parent_child_all(ancestor_id, descendant_id)
    select parent_id, child_id from private_category_parent_child where parent_id <> child_id;

do $$
declare
    inserted_count int;
begin
    loop
        insert into private_category_parent_child_all(ancestor_id, descendant_id)
        select distinct ancestor.ancestor_id, descendant.descendant_id
        from private_category_parent_child_all ancestor
        join private_category_parent_child_all descendant on descendant.ancestor_id = ancestor.descendant_id
        where ancestor.ancestor_id <> descendant.descendant_id
          and not exists (select 1 from private_category_parent_child_all existing
              where existing.ancestor_id = ancestor.ancestor_id and existing.descendant_id = descendant.descendant_id);
        get diagnostics inserted_count = row_count;
        exit when inserted_count = 0;
    end loop;
end $$;
