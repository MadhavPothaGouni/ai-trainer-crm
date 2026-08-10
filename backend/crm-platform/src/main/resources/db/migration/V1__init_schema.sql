-- Foundational schema: organizations/teams (tenancy), users, the RBAC
-- catalog (permissions/roles + join tables), auth tokens, and the
-- append-only audit log. Every later migration builds CRM entities on top
-- of this; nothing here is CRM-specific.
--
-- Column shapes intentionally mirror the JPA entities exactly (see
-- common/entity/BaseEntity for the id/created_at/updated_at/created_by/
-- updated_by/version columns every "BaseEntity" table below repeats) so
-- Hibernate's ddl-auto=validate has nothing to complain about at boot.

-- gen_random_uuid() is a PostgreSQL 13+ built-in (no pgcrypto/uuid-ossp
-- extension required) - Hibernate's @UuidGenerator actually assigns the id
-- before insert in practice, but every table still gets a DB-side default
-- so a row inserted outside the app (a script, a manual fix) never ends up
-- with a null primary key.

-- ---------------------------------------------------------------------
-- Organizations & Teams
-- ---------------------------------------------------------------------

create table organizations (
    id                       uuid primary key default gen_random_uuid(),
    created_at               timestamptz not null default now(),
    updated_at               timestamptz not null default now(),
    created_by               uuid,
    updated_by               uuid,
    version                  bigint not null default 0,
    name                     varchar(200) not null,
    slug                     varchar(100) unique,
    default_currency         varchar(3) default 'USD',
    timezone                 varchar(60) default 'UTC',
    fiscal_year_start_month  int not null default 1,
    deleted_at               timestamptz
);

create index idx_organizations_slug on organizations (slug);

create table teams (
    id               uuid primary key default gen_random_uuid(),
    created_at       timestamptz not null default now(),
    updated_at       timestamptz not null default now(),
    created_by       uuid,
    updated_by       uuid,
    version          bigint not null default 0,
    organization_id  uuid not null references organizations (id),
    name             varchar(150) not null,
    department       varchar(100),
    lead_user_id     uuid
);

create index idx_teams_organization_id on teams (organization_id);

-- ---------------------------------------------------------------------
-- RBAC: permission catalog, role bundles, and their join tables
-- ---------------------------------------------------------------------

create table permissions (
    id           uuid primary key default gen_random_uuid(),
    created_at   timestamptz not null default now(),
    updated_at   timestamptz not null default now(),
    created_by   uuid,
    updated_by   uuid,
    version      bigint not null default 0,
    resource     varchar(40) not null,
    action       varchar(20) not null,
    scope        varchar(20) not null,
    description  varchar(200) not null,
    constraint uq_permissions_resource_action_scope unique (resource, action, scope)
);

create table roles (
    id               uuid primary key default gen_random_uuid(),
    created_at       timestamptz not null default now(),
    updated_at       timestamptz not null default now(),
    created_by       uuid,
    updated_by       uuid,
    version          bigint not null default 0,
    name             varchar(100) not null,
    description      varchar(500),
    organization_id  uuid references organizations (id),
    is_system_role   boolean not null default false,
    constraint uq_roles_organization_id_name unique (organization_id, name)
);

create index idx_roles_organization_id on roles (organization_id);

create table role_permissions (
    role_id        uuid not null references roles (id) on delete cascade,
    permission_id  uuid not null references permissions (id) on delete cascade,
    primary key (role_id, permission_id)
);

-- ---------------------------------------------------------------------
-- Users
-- ---------------------------------------------------------------------

create table users (
    id                      uuid primary key default gen_random_uuid(),
    created_at              timestamptz not null default now(),
    updated_at              timestamptz not null default now(),
    created_by              uuid,
    updated_by              uuid,
    version                 bigint not null default 0,
    email                   varchar(255) not null unique,
    password_hash           varchar(255) not null,
    first_name              varchar(100) not null,
    last_name               varchar(100) not null,
    avatar_url              varchar(500),
    phone                   varchar(30),
    status                  varchar(30) not null default 'PENDING_VERIFICATION',
    email_verified          boolean not null default false,
    organization_id         uuid references organizations (id),
    manager_id              uuid references users (id),
    team_id                 uuid references teams (id),
    timezone                varchar(60) default 'UTC',
    locale                  varchar(20) default 'en-US',
    last_login_at           timestamptz,
    mfa_enabled             boolean not null default false,
    mfa_secret              varchar(255),
    failed_login_attempts   int not null default 0,
    locked_until            timestamptz,
    deleted_at              timestamptz
);

create index idx_users_organization_id on users (organization_id);
create index idx_users_team_id on users (team_id);
create index idx_users_email on users (email);

create table user_roles (
    user_id  uuid not null references users (id) on delete cascade,
    role_id  uuid not null references roles (id) on delete cascade,
    primary key (user_id, role_id)
);

-- ---------------------------------------------------------------------
-- Auth tokens: refresh (rotating, revocable) / password reset / email
-- verification. All three store only a SHA-256 hash of the raw token -
-- see security/token/SecureTokenService's javadoc for why.
-- ---------------------------------------------------------------------

create table refresh_tokens (
    id                    uuid primary key default gen_random_uuid(),
    user_id               uuid not null references users (id) on delete cascade,
    token_hash            varchar(64) not null unique,
    expires_at            timestamptz not null,
    revoked_at            timestamptz,
    replaced_by_token_id  uuid,
    device_info           varchar(255),
    ip_address            varchar(64),
    created_at            timestamptz not null default now()
);

create index idx_refresh_tokens_user_id on refresh_tokens (user_id);
create index idx_refresh_tokens_token_hash on refresh_tokens (token_hash);

create table password_reset_tokens (
    id           uuid primary key default gen_random_uuid(),
    user_id      uuid not null references users (id) on delete cascade,
    token_hash   varchar(64) not null unique,
    expires_at   timestamptz not null,
    used_at      timestamptz,
    created_at   timestamptz not null default now()
);

create index idx_password_reset_tokens_token_hash on password_reset_tokens (token_hash);

create table email_verification_tokens (
    id           uuid primary key default gen_random_uuid(),
    user_id      uuid not null references users (id) on delete cascade,
    token_hash   varchar(64) not null unique,
    expires_at   timestamptz not null,
    used_at      timestamptz,
    created_at   timestamptz not null default now()
);

create index idx_email_verification_tokens_token_hash on email_verification_tokens (token_hash);

-- ---------------------------------------------------------------------
-- Audit log: append-only, written exclusively by audit.listener.AuditEventListener
-- ---------------------------------------------------------------------

create table audit_events (
    id               uuid primary key default gen_random_uuid(),
    user_id          uuid,
    organization_id  uuid,
    action           varchar(100) not null,
    resource_type    varchar(100),
    resource_id      varchar(100),
    detail           varchar(2000),
    ip_address       varchar(64),
    timestamp        timestamptz not null default now()
);

create index idx_audit_events_organization_id on audit_events (organization_id, timestamp desc);
create index idx_audit_events_user_id on audit_events (user_id, timestamp desc);
