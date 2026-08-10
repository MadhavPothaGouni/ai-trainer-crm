-- Seeds the full (resource, action, scope) permission catalog referenced
-- by role.entity.Permission's enums. This is platform data, not
-- tenant data - it ships with the app and is never edited through the API
-- (see Permission's javadoc). RoleService.createDefaultRolesForOrganization
-- reads this table (via PermissionRepository.findAll()) every time a new
-- organization is created, so it must be populated before any org can sign
-- up - hence this runs immediately after V1, before the app ever accepts
-- a registration.
--
-- Not every (resource, action, scope) combination is meaningful (e.g.
-- ORGANIZATION:CREATE doesn't need a scope narrower than ORGANIZATION
-- itself), so this is a deliberate curated list per resource rather than
-- a full cartesian product of the three enums.

-- Core CRM resources: full CRUD + export/import/assign across all four scopes.
insert into permissions (resource, action, scope, description)
select resource, action, scope, initcap(replace(resource, '_', ' ')) || ': ' || initcap(action) || ' (' || initcap(scope) || ' scope)'
from (values ('LEAD'), ('CONTACT'), ('ACCOUNT'), ('OPPORTUNITY'), ('ACTIVITY'), ('QUOTE'), ('TICKET')) as r(resource)
cross join (values ('CREATE'), ('READ'), ('UPDATE'), ('DELETE'), ('EXPORT'), ('IMPORT'), ('ASSIGN')) as a(action)
cross join (values ('OWN'), ('TEAM'), ('DEPARTMENT'), ('ORGANIZATION')) as s(scope);

-- Product/order/invoice/payment: CRUD + approve, no per-record ownership scope narrower
-- than TEAM in practice (these are shared catalog/finance records, not individually-owned).
insert into permissions (resource, action, scope, description)
select resource, action, scope, initcap(replace(resource, '_', ' ')) || ': ' || initcap(action) || ' (' || initcap(scope) || ' scope)'
from (values ('PRODUCT'), ('ORDER'), ('INVOICE'), ('PAYMENT')) as r(resource)
cross join (values ('CREATE'), ('READ'), ('UPDATE'), ('DELETE'), ('APPROVE')) as a(action)
cross join (values ('TEAM'), ('DEPARTMENT'), ('ORGANIZATION')) as s(scope);

-- Marketing campaigns and the knowledge base: CRUD + export at team/department/org scope.
insert into permissions (resource, action, scope, description)
select resource, action, scope, initcap(replace(resource, '_', ' ')) || ': ' || initcap(action) || ' (' || initcap(scope) || ' scope)'
from (values ('CAMPAIGN'), ('KNOWLEDGE_ARTICLE')) as r(resource)
cross join (values ('CREATE'), ('READ'), ('UPDATE'), ('DELETE'), ('EXPORT')) as a(action)
cross join (values ('TEAM'), ('DEPARTMENT'), ('ORGANIZATION')) as s(scope);

-- Workflow automation and reporting/dashboards: CRUD + manage, org-wide concerns by nature.
insert into permissions (resource, action, scope, description)
select resource, action, scope, initcap(replace(resource, '_', ' ')) || ': ' || initcap(action) || ' (' || initcap(scope) || ' scope)'
from (values ('WORKFLOW'), ('REPORT'), ('DASHBOARD')) as r(resource)
cross join (values ('CREATE'), ('READ'), ('UPDATE'), ('DELETE'), ('MANAGE')) as a(action)
cross join (values ('OWN'), ('TEAM'), ('ORGANIZATION')) as s(scope);

-- Platform administration: user/role/organization/integration/api-key/custom-field/
-- custom-object management, and read-only audit log access. These are
-- inherently organization-wide - there's no such thing as a "team-scoped"
-- user management permission - so every row here uses ORGANIZATION scope.
insert into permissions (resource, action, scope, description)
select resource, action, 'ORGANIZATION', initcap(replace(resource, '_', ' ')) || ': ' || initcap(action)
from (values ('USER'), ('ROLE'), ('ORGANIZATION'), ('INTEGRATION'), ('API_KEY'), ('CUSTOM_FIELD'), ('CUSTOM_OBJECT')) as r(resource)
cross join (values ('CREATE'), ('READ'), ('UPDATE'), ('DELETE'), ('MANAGE')) as a(action);

insert into permissions (resource, action, scope, description)
values ('AUDIT_LOG', 'READ', 'ORGANIZATION', 'Audit Log: Read (Organization scope)');
