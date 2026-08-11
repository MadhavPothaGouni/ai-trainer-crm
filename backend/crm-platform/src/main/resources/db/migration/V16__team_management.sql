-- Closes the gap ScopeAuthorizationService's javadoc has documented since
-- the CRM domain first shipped: "TEAM/DEPARTMENT scope is real but
-- currently unreachable in practice - there's no Team-management API yet
-- and nothing ever sets User#getTeamId(), so every user's team is null
-- today." The `teams` table and `users.team_id` FK have existed since
-- V1__init_schema.sql purely so ScopeAuthorizationService had something to
-- resolve against; this migration is what finally makes them reachable
-- from the API - TeamController (CRUD) and a new PATCH .../users/{id}/team
-- endpoint (assignment) - see organization/service/TeamService.java and
-- UserService#updateTeam.
--
-- TEAM was never seeded as a Permission.Resource in V2 (it's the only
-- entity in the whole schema that predates its own permission catalog
-- entry), so this adds it now: CREATE/READ/UPDATE/DELETE/MANAGE at
-- ORGANIZATION scope only - the same shape as USER/ROLE/ORGANIZATION in
-- V2's "platform administration" block, and for the same reason: there's
-- no such thing as a team-scoped permission to manage teams themselves,
-- only who can administer them org-wide. MANAGE is reserved for future
-- bulk-membership operations; today's TeamController only implements
-- CREATE/READ/UPDATE/DELETE, mirroring how WORKFLOW/REPORT/DASHBOARD
-- seeded MANAGE back in V2 well before anything used it.
insert into permissions (resource, action, scope, description)
values
    ('TEAM', 'CREATE', 'ORGANIZATION', 'Team: Create'),
    ('TEAM', 'READ', 'ORGANIZATION', 'Team: Read'),
    ('TEAM', 'UPDATE', 'ORGANIZATION', 'Team: Update'),
    ('TEAM', 'DELETE', 'ORGANIZATION', 'Team: Delete'),
    ('TEAM', 'MANAGE', 'ORGANIZATION', 'Team: Manage');

-- `teams` never got a deleted_at column back in V1 because nothing ever
-- deleted one (there was no delete endpoint to call). Adding it now rather
-- than hard-deleting keeps `users.team_id`'s FK trivially satisfiable even
-- after a team is "removed" - a soft-deleted team row still physically
-- exists, so members who were on it don't get silently orphaned or block
-- the delete with a FK violation the way a hard delete would the moment
-- any user still referenced it.
alter table teams add column deleted_at timestamptz;
