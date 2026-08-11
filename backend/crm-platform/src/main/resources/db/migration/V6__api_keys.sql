-- Programmatic auth: an API key lets a script or external integration call
-- this API without a human logging in through /auth/login. Modeled on the
-- GitHub/Stripe pattern rather than a long-lived JWT: the raw secret is
-- generated once, shown to the caller exactly once in the create response,
-- and never stored or retrievable again - only a bcrypt hash of it lives
-- here (hashed_secret, via the same PasswordEncoder bean user passwords
-- use). key_prefix is NOT secret - it's a short, indexed, always-visible
-- identifier (like "ak_1a2b3c4d") so a key can be looked up on every
-- request without a table scan, and so a user can tell their keys apart in
-- a list without ever seeing the secret again.
--
-- An API key authenticates as whichever user created it (created_by_user_id)
-- - see ApiKeyAuthenticationFilter and ApiKeyService#authenticate - rather
-- than carrying its own independent permission set. That's a deliberate
-- scope trim (a proper implementation would let the creator pick a subset
-- of their own permissions to delegate to the key) documented in the
-- module's javadoc and the root README's Roadmap; it does mean a key
-- automatically loses access the moment its creator is deactivated or
-- demoted, which is the safer default to ship first.
create table api_keys (
    id                  uuid primary key default gen_random_uuid(),
    created_at          timestamptz not null default now(),
    updated_at          timestamptz not null default now(),
    created_by          uuid,
    updated_by          uuid,
    version             bigint not null default 0,
    organization_id     uuid not null references organizations (id),
    name                varchar(200) not null,
    key_prefix          varchar(20) not null,
    hashed_secret       varchar(255) not null,
    created_by_user_id  uuid not null references users (id),
    last_used_at        timestamptz,
    expires_at          timestamptz,
    revoked_at          timestamptz
);

create unique index idx_api_keys_key_prefix on api_keys (key_prefix);
create index idx_api_keys_organization_id on api_keys (organization_id);
