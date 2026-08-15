-- Refund Record: a refund issued against a Payment. Co-located in the payment package (not a new
-- refundrecord package) so RefundRecordService can call PaymentService's package-private
-- findOrThrow when validating a refund's parent payment - same precedent RoomBookingService
-- established for Room.
--
-- Unlike Payment itself (a shared-org resource with no ownerId), RefundRecord is owner-scoped -
-- full OWN/TEAM/DEPARTMENT/ORGANIZATION ladder, since a specific staff member processes each
-- refund and that's worth tracking/restricting per RBAC, same shape every other occurrence entity
-- in this platform uses. RefundRecordService#assertRefundNotExceedingPayment is the one piece of
-- real business logic: the sum of a payment's existing non-deleted refunds plus the new/edited
-- amount must never exceed the payment's own amount - modeled after
-- PaymentService#sumActiveAmountByInvoiceId's ledger-recompute approach. status is a free
-- REQUESTED/APPROVED/PROCESSED state machine; processed_at is stamped once, the first time status
-- moves to PROCESSED.

insert into permissions (resource, action, scope, description)
select resource, action, scope, initcap(replace(resource, '_', ' ')) || ': ' || initcap(action) || ' (' || initcap(scope) || ' scope)'
from (values ('REFUND_RECORD')) as r(resource)
cross join (values ('CREATE'), ('READ'), ('UPDATE'), ('DELETE')) as a(action)
cross join (values ('OWN'), ('TEAM'), ('DEPARTMENT'), ('ORGANIZATION')) as s(scope);

create table refund_records (
    id                  uuid primary key default gen_random_uuid(),
    created_at          timestamptz not null default now(),
    updated_at          timestamptz not null default now(),
    created_by          uuid,
    updated_by          uuid,
    version             bigint not null default 0,
    organization_id     uuid not null references organizations (id),
    payment_id          uuid not null references payments (id),
    owner_id            uuid not null references users (id),
    amount              numeric(14, 2) not null,
    reason              varchar(30) not null default 'OTHER',
    status              varchar(20) not null default 'REQUESTED',
    processed_at        timestamptz,
    notes               varchar(2000),
    deleted_at          timestamptz
);

create index idx_refund_records_organization_id on refund_records (organization_id);
create index idx_refund_records_owner_id on refund_records (organization_id, owner_id);
create index idx_refund_records_payment_id on refund_records (payment_id);
