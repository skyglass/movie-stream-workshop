-- Reverts V52: nested watchlist subscriptions are being redesigned as OR-composition categories (real tree
-- nodes with a real single parent, same as AND-composition categories already are), so this bolted-on nesting
-- column on the flat pointer table is no longer needed.
drop index if exists idx_movie_watchlist_default_category_parent;

alter table movie_watchlist_default_category
    drop column parent_private_category_id;
