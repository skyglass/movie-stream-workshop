alter table movies add column type integer not null default 0;

alter table movies add constraint movies_type_check check (type in (0, 1, 2));
