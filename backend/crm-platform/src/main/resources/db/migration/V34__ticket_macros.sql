-- Ticket macros (canned responses): reusable text a support rep applies to a Ticket in one
-- click - appends the macro's body to the ticket's description and, optionally, transitions
-- the ticket to a fixed status (e.g. a "Thanks, closing this out" macro that also moves the
-- ticket to RESOLVED).
--
-- Macro mirrors product/'s no-OWN catalog shape exactly: shared organization content, no
-- ownerId, MACRO seeded at TEAM/DEPARTMENT/ORGANIZATION only, MacroService does no
-- ScopeAuthorizationService call for the catalog's own CRUD - same reasoning
-- CourseService/SequenceService's javadoc already gives.
--
-- MacroService#apply is the interesting part, and it's deliberately NOT built the way
-- BookingLinkService#book/#cancel drive CalendarEventService, nor the way
-- TerritoryAssignmentListener/LeadScoringEngine directly inject a foreign Repository and
-- save() the foreign entity themselves (the two established cross-module-mutation patterns
-- in this codebase already). A Ticket's authorization is per-record (OWN/TEAM/DEPARTMENT/
-- ORGANIZATION based on *that ticket's* owner) - re-implementing that check inside
-- MacroService against MACRO's own permission would be a real bug (a rep with only
-- MACRO:READ could then mutate a ticket they can't otherwise touch). So MacroService#apply
-- instead calls straight through TicketService#update/#updateStatus - the same public,
-- already-correctly-authorized methods TicketController itself calls - and lets those own
-- the Ticket-side authorization and audit trail entirely. new_status is applied via
-- TicketService#updateStatus specifically (not by hand-setting Ticket.status) so
-- resolvedAt's stamp/clear logic (see V14's migration comment) stays correct too.

insert into permissions (resource, action, scope, description)
select resource, action, scope, initcap(replace(resource, '_', ' ')) || ': ' || initcap(action) || ' (' || initcap(scope) || ' scope)'
from (values ('MACRO')) as r(resource)
cross join (values ('CREATE'), ('READ'), ('UPDATE'), ('DELETE')) as a(action)
cross join (values ('TEAM'), ('DEPARTMENT'), ('ORGANIZATION')) as s(scope);

create table macros (
    id uuid primary key default gen_random_uuid(),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    created_by uuid,
    updated_by uuid,
    version bigint not null default 0,
    organization_id uuid not null references organizations (id),
    name varchar(200) not null,
    body varchar(2000) not null,
    -- Mirrors tickets.status's own varchar(20) column exactly - no FK possible (Ticket.Status
    -- is a Java enum, not a lookup table), validated in Java against the real enum instead.
    new_status varchar(20),
    active boolean not null default true,
    deleted_at timestamptz
);
create index idx_macros_organization_id on macros (organization_id);
