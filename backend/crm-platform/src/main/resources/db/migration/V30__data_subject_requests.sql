-- GDPR/CCPA-style data-subject rights: DataSubjectRequest is an audit-log
-- row for exactly two actions an org admin can take against a person's
-- personal data, identified by email address rather than a specific
-- Contact/Lead id (a data subject rarely knows or cares which internal
-- record type holds their information - "erase everything you have on
-- jane@example.com" is the request, not "delete Contact <uuid>"):
--
--   EXPORT  - gather every Contact/Lead row in this organization whose
--             email matches, and return it as a downloadable file. A pure
--             read; nothing is mutated.
--   ERASURE - the same lookup, but scrubs each matched row's PII columns
--             (name/email/phone/etc, replaced with a fixed redacted
--             placeholder) and soft-deletes it if not already deleted.
--
-- ERASURE is a genuinely new pattern in this codebase: every other
-- soft-delete (Account/Contact/Lead/User/Ticket) only ever sets
-- deleted_at and otherwise leaves the row's data untouched, specifically
-- so FK-referencing history (Activities, Opportunities, Orders, the audit
-- trail) keeps working - see Contact.deletedAt's and User.deletedAt's own
-- javadoc for that reasoning. A GDPR erasure request needs the row's
-- *contents* gone, not just hidden, while still not breaking any FK that
-- points at it - so DataSubjectRequestService overwrites the PII columns
-- in place rather than deleting the row outright. This is deliberately
-- NOT a hard delete/cascade: every FK-referencing Activity/Opportunity
-- keeps working exactly as it does for an ordinary soft delete, it just
-- points at a now-anonymized Contact/Lead instead of one with real data.
--
-- Both matched entity types (Contact, Lead) are looked up regardless of
-- their own deleted_at state - an already-soft-deleted Contact still has
-- live PII sitting in the database and is exactly the kind of row a
-- right-to-be-forgotten request needs to reach.
--
-- DATA_SUBJECT_REQUEST is admin config seeded at ORGANIZATION scope only,
-- the same platform-administration shape USER/ROLE/AUDIT_LOG already use
-- (no per-record OWN/TEAM/DEPARTMENT scoping makes sense for a request
-- that spans every Contact/Lead in the org by email, not one record a
-- caller happens to own). It reuses the existing READ/EXPORT/DELETE
-- actions rather than adding new ones: EXPORT for gathering data,
-- DELETE for erasing it, READ for browsing request history - the same
-- "action set matches what's actually possible" minimalism V19/V29's
-- migration comments already document for their own resources.
insert into permissions (resource, action, scope, description)
select 'DATA_SUBJECT_REQUEST', action, 'ORGANIZATION', 'Data Subject Request: ' || initcap(action) || ' (Organization scope)'
from (values ('READ'), ('EXPORT'), ('DELETE')) as a(action);

create table data_subject_requests (
    id                  uuid primary key default gen_random_uuid(),
    created_at          timestamptz not null default now(),
    updated_at          timestamptz not null default now(),
    created_by          uuid,
    updated_by          uuid,
    version             bigint not null default 0,
    organization_id     uuid not null references organizations (id),
    request_type        varchar(20) not null,
    subject_email       varchar(255) not null,
    status              varchar(20) not null,
    initiated_by_user_id uuid not null references users (id),
    contacts_affected   int not null default 0,
    leads_affected      int not null default 0,
    result_note         varchar(500),
    completed_at        timestamptz
);

create index idx_data_subject_requests_organization_id on data_subject_requests (organization_id, created_at desc);
