-- Lets a watchlist subscription be nested under any private sub-category, not just the watchlist's own flat
-- root, backing the "Subscription Category" checkbox in the "Create Category" editor.
alter table movie_watchlist_default_category
    add column parent_private_category_id bigint references private_category(id) on delete cascade;

-- Every existing subscription today is implicitly root-level -- backfill explicitly so nothing changes visually.
update movie_watchlist_default_category d
    set parent_private_category_id = w.category_id
    from user_movie_watchlist w
    where w.id = d.watchlist_id;

alter table movie_watchlist_default_category
    alter column parent_private_category_id set not null;

create index idx_movie_watchlist_default_category_parent on movie_watchlist_default_category(parent_private_category_id);
