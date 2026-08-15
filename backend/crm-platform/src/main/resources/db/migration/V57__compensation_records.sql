-- Compensation Record: one staff member's pay for one pay period - hours worked, hourly rate,
-- commission, bonus, rolled up into a total. Distinct from CommissionRecord (V29, sales-rep
-- commission on closed-won deals specifically) - this is the broader payroll record a pay period
-- produces, of which a rep's commission might be one line item folded in.
--
-- Owner-scoped, full OWN/TEAM/DEPARTMENT/ORGANIZATION ladder. staff_user_id is who's being paid;
-- owner_id (who entered/manages the record - e.g. an office manager running payroll) is what
-- ScopeAuthorizationService checks, same split every contact_id-vs-owner_id occurrence entity in
-- this platform uses, just against a User instead of a Contact. status is a free state machine
-- (DRAFT/APPROVED/PAID). paid_at is stamped once, the first time status moves to PAID, and never
-- overwritten - same "stamp once" rule every other lifecycle timestamp in this platform follows.
-- total_amount is computed server-side (hours_worked * hourly_rate + commission_amount +
-- bonus_amount) rather than trusted from the client, so it can't drift from its inputs.

insert into permissions (resource, action, scope, description)
select resource, action, scope, initcap(replace(resource, '_', ' ')) || ': ' || initcap(action) || ' (' || initcap(scope) || ' scope)'
from (values ('COMPENSATION_RECORD')) as r(resource)
cross join (values ('CREATE'), ('READ'), ('UPDATE'), ('DELETE')) as a(action)
cross join (values ('OWN'), ('TEAM'), ('DEPARTMENT'), ('ORGANIZATION')) as s(scope);

create table compensation_records (
    id                  uuid primary key default gen_random_uuid(),
    created_at          timestamptz not null default now(),
    updated_at          timestamptz not null default now(),
    created_by          uuid,
    updated_by          uuid,
    version             bigint not null default 0,
    organization_id     uuid not null references organizations (id),
    staff_user_id       uuid not null references users (id),
    owner_id            uuid not null references users (id),
    pay_period_start    date not null,
    pay_period_end      date not null,
    hours_worked        numeric(8, 2) not null default 0,
    hourly_rate         numeric(10, 2) not null default 0,
    commission_amount   numeric(12, 2) not null default 0,
    bonus_amount        numeric(12, 2) not null default 0,
    total_amount        numeric(12, 2) not null default 0,
    status              varchar(20) not null default 'DRAFT',
    paid_at             timestamptz,
    notes               varchar(2000),
    deleted_at          timestamptz
);

create index idx_compensation_records_organization_id on compensation_records (organization_id);
create index idx_compensation_records_owner_id on compensation_records (organization_id, owner_id);
create index idx_compensation_records_staff_user_id on compensation_records (staff_user_id);
