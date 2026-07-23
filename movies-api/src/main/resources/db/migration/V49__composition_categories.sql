create table composition_category (
    category_id bigint primary key references category(id) on delete cascade
);

create table composition_category_component (
    composition_category_id bigint not null references composition_category(category_id) on delete cascade,
    component_category_id bigint not null references category(id) on delete restrict,
    constraint pk_composition_category_component primary key (composition_category_id, component_category_id),
    constraint chk_composition_category_component_not_self check (composition_category_id <> component_category_id)
);

create index idx_composition_category_component_component
    on composition_category_component(component_category_id);

-- The service validates these rules too; these triggers keep direct SQL imports from creating an ambiguous tree.
create function enforce_composition_category_rules() returns trigger language plpgsql as $$
begin
    if tg_table_name = 'composition_category' then
        if exists (select 1 from category_parent_child where parent_id = new.category_id and child_id <> new.category_id)
           or exists (select 1 from movie_category where category_id = new.category_id) then
            raise exception 'a composition category must be a leaf with no direct movie assignments';
        end if;
    elsif tg_table_name = 'category_parent_child' then
        if new.parent_id <> new.child_id
           and exists (select 1 from composition_category where category_id = new.parent_id) then
            raise exception 'a composition category cannot have children';
        end if;
    elsif tg_table_name = 'composition_category_component' then
        if exists (select 1 from composition_category where category_id = new.component_category_id) then
            raise exception 'a composition category cannot be a component';
        end if;
    elsif tg_table_name = 'movie_category' then
        if exists (select 1 from composition_category where category_id = new.category_id) then
            raise exception 'movies cannot be assigned directly to a composition category';
        end if;
    end if;
    return new;
end;
$$;

create trigger composition_category_must_be_leaf
before insert on composition_category
for each row execute function enforce_composition_category_rules();

create trigger composition_category_cannot_have_children
before insert or update on category_parent_child
for each row execute function enforce_composition_category_rules();

create trigger composition_component_must_be_normal
before insert or update on composition_category_component
for each row execute function enforce_composition_category_rules();

create trigger composition_category_cannot_receive_movies
before insert or update on movie_category
for each row execute function enforce_composition_category_rules();

-- One row means “this movie satisfies this category”. Normal categories retain their direct movie assignments;
-- compositions use relational division: every required component must match, including descendants of a component.
create view category_movie_match as
select mc.category_id, mc.movie_id
from movie_category mc
where not exists (select 1 from composition_category cc where cc.category_id = mc.category_id)

union all

select cc.category_id, mc.movie_id
from composition_category cc
join composition_category_component component
  on component.composition_category_id = cc.category_id
join category_parent_child_all component_descendant
  on component_descendant.ancestor_id = component.component_category_id
join movie_category mc
  on mc.category_id = component_descendant.descendant_id
group by cc.category_id, mc.movie_id
having count(distinct component.component_category_id) = (
    select count(*)
    from composition_category_component required
    where required.composition_category_id = cc.category_id
);
