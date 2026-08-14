-- Training session exercises: the connective tissue between training_sessions (V37) and
-- exercises (V38) that both of those migrations' own comments flagged as deliberately unbuilt -
-- V38 says explicitly "a real per-session exercise/sets/reps table is a bigger, separate feature
-- this pass doesn't take on"; this is that feature, the ninth module this session.
--
-- Child-entity-of-parent pattern, the same shape quote_line_items (V5) and sequence_steps (V32)
-- use: a real foreign key to the parent (training_session_id) with on delete cascade, no
-- permission of its own - add/update/remove is gated entirely on the parent training session's
-- own TRAINING_SESSION:UPDATE permission, exactly how QuoteService gates line item mutations on
-- QUOTE:UPDATE. No permissions insert in this migration for that reason.
--
-- exercise_id is nullable and carries no cascade, mirroring quote_line_items.product_id exactly:
-- a coach can log a movement that isn't in the org's exercise catalog yet, and even when it is
-- referenced, exercises are soft-deleted (never hard-deleted) so no on-delete behavior is needed.
-- exercise_name is stamped once at creation - copied from the referenced Exercise's name when
-- exercise_id is set, or typed freehand otherwise - and never resynced if that catalog entry is
-- later renamed, the same "snapshot, don't drift" restraint booking_slots.end_at/contracts.
-- signed_at/client_goals.achieved_at already established, applied here to a child-entity field
-- instead of a parent-entity one.
--
-- reps_completed is a freeform varchar rather than a rigid integer - reps routinely vary set to
-- set in real coaching ("12,10,8"), and a fixed-count-per-set column would lose that detail.
-- sequence_order plays the same ordering role as sequence_steps.step_order. No status column -
-- like quote_line_items/sequence_steps, this is an append-only log row of something already
-- performed, not a resource that moves through states.

create table training_session_exercises (
    id                  uuid primary key default gen_random_uuid(),
    created_at          timestamptz not null default now(),
    updated_at          timestamptz not null default now(),
    created_by          uuid,
    updated_by          uuid,
    version             bigint not null default 0,
    training_session_id uuid not null references training_sessions (id) on delete cascade,
    exercise_id         uuid references exercises (id),
    exercise_name       varchar(200) not null,
    sequence_order      integer not null default 0,
    sets_completed      integer not null default 1,
    reps_completed      varchar(50) not null,
    weight_value        numeric(6,2),
    weight_unit         varchar(10),
    notes               varchar(500)
);

create index idx_training_session_exercises_session_id on training_session_exercises (training_session_id);
