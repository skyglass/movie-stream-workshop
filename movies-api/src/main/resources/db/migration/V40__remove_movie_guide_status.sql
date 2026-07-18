-- The creation wizard's STARTED/COMPLETED status no longer has a UI concept to drive: guide creation now goes
-- straight to the normal guide page (no more resumable wizard steps), so this column and its check constraint
-- are dead weight.
alter table movie_guide drop constraint movie_guide_status_check;
alter table movie_guide drop column status;
