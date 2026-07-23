-- V54/V55's fixed-point loop used a scratch `CREATE TEMPORARY TABLE` per invocation to work around Postgres
-- forbidding GROUP BY/aggregates in a WITH RECURSIVE recursive term. That's DDL, and Postgres refuses to run DDL
-- (including CREATE/DROP TEMP TABLE) inside a read-only transaction -- which every plain movie-browsing query is
-- (@Transactional(readOnly = true)), since category_movie_match is just an ordinary view joined into those reads.
-- Fixed by replacing the temp table with a plain PL/pgSQL array variable (of a small composite row type) carried
-- across loop iterations -- array assignment is a local variable operation, not DDL, so it works in any
-- transaction. The fixed-point logic itself (base case, then repeatedly resolving AND/OR until a pass adds
-- nothing new, bounded at 50 iterations) is unchanged from V54/V55.
create type category_movie_pair as (category_id bigint, movie_id varchar(32));
create type private_category_movie_pair as (private_category_id bigint, movie_id varchar(32));

create or replace function compute_category_movie_match() returns table(category_id bigint, movie_id varchar) language plpgsql as $$
declare
    resolved category_movie_pair[] := '{}';
    new_pairs category_movie_pair[];
    iteration int := 0;
begin
    select coalesce(array_agg(row(cpc.ancestor_id, mc.movie_id)::category_movie_pair), '{}')
    into resolved
    from category_parent_child_all cpc
    join movie_category mc on mc.category_id = cpc.descendant_id
    where not exists (select 1 from composition_category cc where cc.category_id = cpc.ancestor_id);

    loop
        iteration := iteration + 1;
        exit when iteration > 50;

        with resolved_rows as (
            select (p).category_id, (p).movie_id from unnest(resolved) as p
        ),
        matched as (
            select cc.category_id, r.movie_id
            from composition_category cc
            join composition_category_component comp on comp.composition_category_id = cc.category_id
            join resolved_rows r on r.category_id = comp.component_category_id
            where cc.operator = 1 -- Operator.AND
            group by cc.category_id, r.movie_id
            having count(distinct comp.component_category_id) = (
                select count(*) from composition_category_component req
                where req.composition_category_id = cc.category_id
            )

            union

            select cc.category_id, r.movie_id
            from composition_category cc
            join composition_category_component comp on comp.composition_category_id = cc.category_id
            join resolved_rows r on r.category_id = comp.component_category_id
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

create or replace function compute_private_category_movie_match() returns table(private_category_id bigint, movie_id varchar) language plpgsql as $$
declare
    resolved private_category_movie_pair[] := '{}';
    new_pairs private_category_movie_pair[];
    iteration int := 0;
begin
    select coalesce(array_agg(row(cpc.ancestor_id, mc.movie_id)::private_category_movie_pair), '{}')
    into resolved
    from private_category_parent_child_all cpc
    join movie_private_category mc on mc.private_category_id = cpc.descendant_id
    where not exists (select 1 from private_composition_category cc where cc.private_category_id = cpc.ancestor_id);

    loop
        iteration := iteration + 1;
        exit when iteration > 50;

        with resolved_rows as (
            select (p).private_category_id, (p).movie_id from unnest(resolved) as p
        ),
        satisfied as (
            select comp.composition_category_id, comp.component_category_id as private_component,
                   comp.public_component_category_id as public_component, r.movie_id
            from private_composition_category_component comp
            join resolved_rows r on r.private_category_id = comp.component_category_id
            where comp.component_category_id is not null

            union all

            select comp.composition_category_id, comp.component_category_id, comp.public_component_category_id, p.movie_id
            from private_composition_category_component comp
            join category_movie_match p on p.category_id = comp.public_component_category_id
            where comp.public_component_category_id is not null
        ),
        matched as (
            select cc.private_category_id, s.movie_id
            from private_composition_category cc
            join satisfied s on s.composition_category_id = cc.private_category_id
            where cc.operator = 1 -- Operator.AND
            group by cc.private_category_id, s.movie_id
            having count(distinct coalesce(s.private_component, -s.public_component)) = (
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
