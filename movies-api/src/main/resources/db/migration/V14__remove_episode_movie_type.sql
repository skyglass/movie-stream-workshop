update movies
set type = 0
where type not in (0, 1);

alter table movies drop constraint if exists movies_type_check;

alter table movies add constraint movies_type_check check (type in (0, 1));
