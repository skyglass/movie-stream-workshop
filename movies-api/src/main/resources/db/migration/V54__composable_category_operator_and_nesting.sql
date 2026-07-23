-- AND (existing "composition") gets a sibling OR operator -- "Subscribe Categories" is being rewritten onto this
-- same mechanism instead of a DAG copy-link (see V56), and both operators now allow nesting: a component may
-- itself be a composition/subscription category, not just a plain one.
-- Stored as the ordinal of skycomposer.moviechallenge.api.movie.model.Operator (OR=0, AND=1), same convention as
-- movie_guide.type/MovieGuideType -- default 1 (AND) matches every pre-existing composition_category row.
alter table composition_category add column operator smallint not null default 1 check (operator in (0, 1));

-- Nesting is now allowed -- drop the trigger that rejected a composable category as a component.
drop trigger composition_component_must_be_normal on composition_category_component;

-- Deletion becomes cascading instead of blocked: removing a category removes it as a component from anything
-- that included it (this FK, changed from restrict to cascade), and the trigger below then removes any
-- composition/subscription category that the removal just left with zero components -- which itself cascades
-- through the same FKs, so a chain of dependents resolves to a fixed point automatically, with no application code.
-- The FK was declared inline in V49 (never explicitly named), so its auto-generated name is looked up rather than
-- guessed -- Postgres truncates long auto-generated names to 63 bytes in a way that's not worth hand-predicting.
do $$
declare
    fkey_name text;
begin
    select conname into fkey_name
    from pg_constraint
    where conrelid = 'composition_category_component'::regclass
      and contype = 'f'
      and confrelid = 'category'::regclass
      and conkey = (select array_agg(attnum) from pg_attribute
                    where attrelid = 'composition_category_component'::regclass and attname = 'component_category_id');

    execute format('alter table composition_category_component drop constraint %I', fkey_name);
    execute format('alter table composition_category_component add constraint %I foreign key (component_category_id) references category(id) on delete cascade', fkey_name);
end $$;

create function cascade_delete_empty_compositions() returns trigger language plpgsql as $$
declare
    empty_id bigint;
begin
    for empty_id in
        select distinct d.composition_category_id
        from deleted_components d
        where not exists (
            select 1 from composition_category_component remaining
            where remaining.composition_category_id = d.composition_category_id
        )
    loop
        delete from category where id = empty_id;
    end loop;
    return null;
end;
$$;

create trigger cascade_delete_empty_compositions_trigger
after delete on composition_category_component
referencing old table as deleted_components
for each statement
execute function cascade_delete_empty_compositions();

-- category_movie_match generalizes from a single-pass AND relational-division into a fixed-point computation:
-- components can now themselves be composable, so a composition's match status may depend on another
-- composition's match status, resolved level by level until nothing new is found. This can't be expressed as a
-- plain `WITH RECURSIVE` view because Postgres forbids GROUP BY/aggregates in a term that references the
-- recursive self-reference, and the AND branch needs exactly that (count of distinct matched components); a
-- PL/pgSQL function with an explicit iterate-to-fixed-point loop sidesteps the restriction cleanly.
drop view category_movie_match;

create function compute_category_movie_match() returns table(category_id bigint, movie_id varchar) language plpgsql as $$
declare
    inserted_count int;
    iteration int := 0;
begin
    drop table if exists pg_temp.category_movie_match_resolved;
    create temporary table category_movie_match_resolved (category_id bigint, movie_id varchar(32));

    -- Base case: non-composable categories, expanded through the plain descendant closure -- unchanged from the
    -- pre-nesting definition (a component is satisfied by the category itself or any of its plain descendants).
    insert into category_movie_match_resolved (category_id, movie_id)
    select cpc.ancestor_id, mc.movie_id
    from category_parent_child_all cpc
    join movie_category mc on mc.category_id = cpc.descendant_id
    where not exists (select 1 from composition_category cc where cc.category_id = cpc.ancestor_id);

    -- Repeatedly resolve composable categories whose components are already (possibly transitively) resolved,
    -- until a pass adds nothing new. Bounded at 50 levels purely as a safety net -- real nesting depth is always
    -- shallow, and a genuine cycle is impossible (rejected at write time).
    loop
        iteration := iteration + 1;
        exit when iteration > 50;

        insert into category_movie_match_resolved (category_id, movie_id)
        select matched.category_id, matched.movie_id
        from (
            select cc.category_id, r.movie_id
            from composition_category cc
            join composition_category_component comp on comp.composition_category_id = cc.category_id
            join category_movie_match_resolved r on r.category_id = comp.component_category_id
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
            join category_movie_match_resolved r on r.category_id = comp.component_category_id
            where cc.operator = 0 -- Operator.OR
        ) matched
        where not exists (
            select 1 from category_movie_match_resolved existing
            where existing.category_id = matched.category_id and existing.movie_id = matched.movie_id
        );

        get diagnostics inserted_count = row_count;
        exit when inserted_count = 0;
    end loop;

    return query select r.category_id, r.movie_id from category_movie_match_resolved r;

    drop table category_movie_match_resolved;
    return;
end;
$$;

create view category_movie_match as select * from compute_category_movie_match();
