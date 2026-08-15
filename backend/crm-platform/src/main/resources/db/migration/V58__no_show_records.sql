-- No-Show Record: a client missed a scheduled class/session without cancelling - tracks whether
-- a no-show fee applies and whether it was waived. related_type is a plain enum (CLASS_SESSION/
-- TRAINING_SESSION/OTHER) rather than a polymorphic FK - this platform already has that exact
-- "which kind of thing" split modeled as an enum elsewhere (e.g. Attachment#relatedType), and a
-- no-show doesn't need to navigate back to the specific session it belongs to, just to be able to
-- say what kind of booking was missed.
--
-- Owner-scoped, full OWN/TEAM/DEPARTMENT/ORGANIZATION ladder. contact_id is the client who missed
-- the booking (never the authorization subject - owner_id is, same split every contact_id-vs-
-- owner_id occurrence entity in this platform uses). fee_amount is nullable (not every no-show
-- carries a fee); waived and waived_at are a stamp-once pair set only via the dedicated
-- POST .../waive action (see NoShowRecordService#waive) - there's no PATCH .../status endpoint on
-- this entity because "waived" is the only piece of lifecycle state a no-show record has, and a
-- dedicated business-rule-checked action (mirrors GiftCardService#redeem, ReferralService#issueReward)
-- fits it better than a free status enum would.

insert into permissions (resource, action, scope, description)
select resource, action, scope, initcap(replace(resource, '_', ' ')) || ': ' || initcap(action) || ' (' || initcap(scope) || ' scope)'
from (values ('NO_SHOW_RECORD')) as r(resource)
cross join (values ('CREATE'), ('READ'), ('UPDATE'), ('DELETE')) as a(action)
cross join (values ('OWN'), ('TEAM'), ('DEPARTMENT'), ('ORGANIZATION')) as s(scope);

create table no_show_records (
    id                  uuid primary key default gen_random_uuid(),
    created_at          timestamptz not null default now(),
    updated_at          timestamptz not null default now(),
    created_by          uuid,
    updated_by          uuid,
    version             bigint not null default 0,
    organization_id     uuid not null references organizations (id),
    contact_id          uuid not null references contacts (id),
    owner_id            uuid not null references users (id),
    occurred_at         timestamptz not null,
    related_type        varchar(20) not null default 'OTHER',
    fee_amount          numeric(10, 2),
    waived              boolean not null default false,
    waived_at           timestamptz,
    notes               varchar(2000),
    deleted_at          timestamptz
);

create index idx_no_show_records_organization_id on no_show_records (organization_id);
create index idx_no_show_records_owner_id on no_show_records (organization_id, owner_id);
create index idx_no_show_records_contact_id on no_show_records (contact_id);
