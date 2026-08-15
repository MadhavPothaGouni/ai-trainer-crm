-- Nutrition Log: one meal a client logged - see NutritionLog's javadoc. Distinct from
-- NutritionPlan (V37, a coach-authored target plan) - this is the client's actual intake, one row
-- per meal. Owner-scoped, no status field - a logged meal is a point-in-time fact, same shape as
-- ProgressPhoto/LoyaltyTransaction. protein_grams/carb_grams/fat_grams are independently optional
-- since not every client logs full macros.

insert into permissions (resource, action, scope, description)
select resource, action, scope, initcap(replace(resource, '_', ' ')) || ': ' || initcap(action) || ' (' || initcap(scope) || ' scope)'
from (values ('NUTRITION_LOG')) as r(resource)
cross join (values ('CREATE'), ('READ'), ('UPDATE'), ('DELETE')) as a(action)
cross join (values ('OWN'), ('TEAM'), ('DEPARTMENT'), ('ORGANIZATION')) as s(scope);

create table nutrition_logs (
    id                  uuid primary key default gen_random_uuid(),
    created_at          timestamptz not null default now(),
    updated_at          timestamptz not null default now(),
    created_by          uuid,
    updated_by          uuid,
    version             bigint not null default 0,
    organization_id     uuid not null references organizations (id),
    contact_id          uuid not null references contacts (id),
    owner_id            uuid not null references users (id),
    logged_at           timestamptz not null default now(),
    meal_type           varchar(20) not null default 'BREAKFAST',
    calories            integer,
    protein_grams       numeric(6, 1),
    carb_grams          numeric(6, 1),
    fat_grams           numeric(6, 1),
    notes               varchar(2000),
    deleted_at          timestamptz
);

create index idx_nutrition_logs_organization_id on nutrition_logs (organization_id);
create index idx_nutrition_logs_owner_id on nutrition_logs (organization_id, owner_id);
create index idx_nutrition_logs_contact_id on nutrition_logs (contact_id);
