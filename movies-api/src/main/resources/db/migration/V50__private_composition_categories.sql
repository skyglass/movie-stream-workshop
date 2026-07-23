create table private_composition_category (
    private_category_id bigint primary key references private_category(id) on delete cascade
);

create table private_composition_category_component (
    composition_category_id bigint not null references private_composition_category(private_category_id) on delete cascade,
    component_category_id bigint not null references private_category(id) on delete restrict,
    constraint pk_private_composition_category_component primary key (composition_category_id, component_category_id),
    constraint chk_private_composition_category_component_not_self check (composition_category_id <> component_category_id)
);

create index idx_private_composition_category_component_component
    on private_composition_category_component(component_category_id);

-- Mirrors enforce_composition_category_rules() (V49), retargeted at the private_* tables.
create function enforce_private_composition_category_rules() returns trigger language plpgsql as $$
begin
    if tg_table_name = 'private_composition_category' then
        if exists (select 1 from private_category_parent_child where parent_id = new.private_category_id and child_id <> new.private_category_id)
           or exists (select 1 from movie_private_category where private_category_id = new.private_category_id) then
            raise exception 'a composition category must be a leaf with no direct movie assignments';
        end if;
    elsif tg_table_name = 'private_category_parent_child' then
        if new.parent_id <> new.child_id
           and exists (select 1 from private_composition_category where private_category_id = new.parent_id) then
            raise exception 'a composition category cannot have children';
        end if;
    elsif tg_table_name = 'private_composition_category_component' then
        if exists (select 1 from private_composition_category where private_category_id = new.component_category_id) then
            raise exception 'a composition category cannot be a component';
        end if;
    elsif tg_table_name = 'movie_private_category' then
        if exists (select 1 from private_composition_category where private_category_id = new.private_category_id) then
            raise exception 'movies cannot be assigned directly to a composition category';
        end if;
    end if;
    return new;
end;
$$;

create trigger private_composition_category_must_be_leaf
before insert on private_composition_category
for each row execute function enforce_private_composition_category_rules();

create trigger private_composition_category_cannot_have_children
before insert or update on private_category_parent_child
for each row execute function enforce_private_composition_category_rules();

create trigger private_composition_component_must_be_normal
before insert or update on private_composition_category_component
for each row execute function enforce_private_composition_category_rules();

create trigger private_composition_category_cannot_receive_movies
before insert or update on movie_private_category
for each row execute function enforce_private_composition_category_rules();

-- One row means "this movie satisfies this private category". Normal categories retain their direct movie
-- assignments; compositions use relational division: every required component must match, including descendants
-- of a component. Mirrors category_movie_match (V49).
create view private_category_movie_match as
select mc.private_category_id, mc.movie_id
from movie_private_category mc
where not exists (select 1 from private_composition_category cc where cc.private_category_id = mc.private_category_id)

union all

select cc.private_category_id, mc.movie_id
from private_composition_category cc
join private_composition_category_component component
  on component.composition_category_id = cc.private_category_id
join private_category_parent_child_all component_descendant
  on component_descendant.ancestor_id = component.component_category_id
join movie_private_category mc
  on mc.private_category_id = component_descendant.descendant_id
group by cc.private_category_id, mc.movie_id
having count(distinct component.component_category_id) = (
    select count(*)
    from private_composition_category_component required
    where required.composition_category_id = cc.private_category_id
);
