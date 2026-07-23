-- Bugfix: V54/V57's base case joined through category_parent_child_all from the movie's actual category UP to
-- every ancestor, marking every ancestor (e.g. "Genres", "Drama") as "matched" just because some descendant had
-- the movie. That's wrong -- a plain category's own membership in category_movie_match must mean the movie is
-- DIRECTLY assigned to it (movie_category has that exact row), exactly like the pre-nesting V49 definition. The
-- "a component is satisfied by a movie filed anywhere in its own descendant subtree" semantic is real, but it
-- only applies when a category is used AS a composition/subscription component -- not as a blanket propagation
-- onto every plain category's own checked-state (which is what broke "Edit Categories": Genres/Directors/Writers
-- showed checked, and unchecking them was a no-op since there was never a real movie_category row to remove).
--
-- Fixed by starting the fixed-point from direct assignments only, then expanding each COMPONENT specifically (via
-- the descendant closure) when checking whether it's satisfied. Composable categories can never have native tree
-- children (unchanged invariant), so that same closure walk from a composable component reaches only itself --
-- which means checking the expanded set against resolved_rows (already accumulated so far) uniformly covers both
-- "plain component satisfied by itself or a descendant" and "nested composable component already resolved true",
-- with no separate branch needed.
create or replace function compute_category_movie_match() returns table(category_id bigint, movie_id varchar) language plpgsql as $$
declare
    resolved category_movie_pair[] := '{}';
    new_pairs category_movie_pair[];
    iteration int := 0;
begin
    select coalesce(array_agg(row(mc.category_id, mc.movie_id)::category_movie_pair), '{}')
    into resolved
    from movie_category mc
    where not exists (select 1 from composition_category cc where cc.category_id = mc.category_id);

    loop
        iteration := iteration + 1;
        exit when iteration > 50;

        with resolved_rows as (
            select (p).category_id, (p).movie_id from unnest(resolved) as p
        ),
        satisfied as (
            select comp.composition_category_id, comp.component_category_id, r.movie_id
            from composition_category_component comp
            join category_parent_child_all descendant on descendant.ancestor_id = comp.component_category_id
            join resolved_rows r on r.category_id = descendant.descendant_id
        ),
        matched as (
            select cc.category_id, s.movie_id
            from composition_category cc
            join satisfied s on s.composition_category_id = cc.category_id
            where cc.operator = 1 -- Operator.AND
            group by cc.category_id, s.movie_id
            having count(distinct s.component_category_id) = (
                select count(*) from composition_category_component req
                where req.composition_category_id = cc.category_id
            )

            union

            select cc.category_id, s.movie_id
            from composition_category cc
            join satisfied s on s.composition_category_id = cc.category_id
            where cc.operator = 0 -- Operator.OR
        )
        select coalesce(array_agg(row(m.category_id, m.movie_id)::category_movie_pair), '{}')
        into new_pairs
        from matched m
        where not exists (
            select 1 from resolved_rows existing
            where existing.category_id = m.category_id and existing.movie_id = m.movie_id
        );

        exit when array_length(new_pairs, 1) is null;
        resolved := resolved || new_pairs;
    end loop;

    return query select (p).category_id, (p).movie_id from unnest(resolved) as p;
end;
$$;

-- Private mirror: same fix for the base case, plus the same "closure-expand, don't exact-match" treatment for
-- the public-component branch (a public component should likewise be satisfied by a movie filed in one of ITS
-- descendants, not only the exact category) -- checked against the already-fixed public category_movie_match
-- view, which now also correctly covers nested public composables via the same reasoning.
create or replace function compute_private_category_movie_match() returns table(private_category_id bigint, movie_id varchar) language plpgsql as $$
declare
    resolved private_category_movie_pair[] := '{}';
    new_pairs private_category_movie_pair[];
    iteration int := 0;
begin
    select coalesce(array_agg(row(mc.private_category_id, mc.movie_id)::private_category_movie_pair), '{}')
    into resolved
    from movie_private_category mc
    where not exists (select 1 from private_composition_category cc where cc.private_category_id = mc.private_category_id);

    loop
        iteration := iteration + 1;
        exit when iteration > 50;

        with resolved_rows as (
            select (p).private_category_id, (p).movie_id from unnest(resolved) as p
        ),
        satisfied as (
            select comp.composition_category_id, comp.component_category_id as component_key, r.movie_id
            from private_composition_category_component comp
            join private_category_parent_child_all descendant on descendant.ancestor_id = comp.component_category_id
            join resolved_rows r on r.private_category_id = descendant.descendant_id
            where comp.component_category_id is not null

            union all

            select comp.composition_category_id, -comp.public_component_category_id as component_key, p.movie_id
            from private_composition_category_component comp
            join category_parent_child_all descendant on descendant.ancestor_id = comp.public_component_category_id
            join category_movie_match p on p.category_id = descendant.descendant_id
            where comp.public_component_category_id is not null
        ),
        matched as (
            select cc.private_category_id, s.movie_id
            from private_composition_category cc
            join satisfied s on s.composition_category_id = cc.private_category_id
            where cc.operator = 1 -- Operator.AND
            group by cc.private_category_id, s.movie_id
            having count(distinct s.component_key) = (
                select count(*) from private_composition_category_component req
                where req.composition_category_id = cc.private_category_id
            )

            union

            select cc.private_category_id, s.movie_id
            from private_composition_category cc
            join satisfied s on s.composition_category_id = cc.private_category_id
            where cc.operator = 0 -- Operator.OR
        )
        select coalesce(array_agg(row(m.private_category_id, m.movie_id)::private_category_movie_pair), '{}')
        into new_pairs
        from matched m
        where not exists (
            select 1 from resolved_rows existing
            where existing.private_category_id = m.private_category_id and existing.movie_id = m.movie_id
        );

        exit when array_length(new_pairs, 1) is null;
        resolved := resolved || new_pairs;
    end loop;

    return query select (p).private_category_id, (p).movie_id from unnest(resolved) as p;
end;
$$;
