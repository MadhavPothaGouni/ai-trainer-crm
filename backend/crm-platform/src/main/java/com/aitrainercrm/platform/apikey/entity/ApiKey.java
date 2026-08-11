package com.aitrainercrm.platform.apikey.entity;

import com.aitrainercrm.platform.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A programmatic-auth credential. Only {@code hashedSecret} (a bcrypt hash,
 * via the same {@code PasswordEncoder} bean user passwords use) is ever
 * stored - the raw secret is generated once in {@code ApiKeyService#create},
 * returned to the caller exactly one time, and is unrecoverable after that;
 * see V6's migration comment for the full rationale and how this differs
 * from {@code WebhookSubscription#secret}.
 *
 * <p>{@code keyPrefix} is the non-secret half of the key (e.g.
 * {@code "ak_1a2b3c4d"}) - indexed and unique, so
 * {@code ApiKeyAuthenticationFilter} can look a key up in O(1) on every
 * request without a table scan, and so a user can tell their keys apart in
 * a list without the secret ever being shown again.
 */
@Entity
@Table(name = "api_keys")
@Getter
@Setter
@NoArgsConstructor
public class ApiKey extends BaseEntity {

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(name = "key_prefix", nullable = false, length = 20)
    private String keyPrefix;

    @Column(name = "hashed_secret", nullable = false, length = 255)
    private String hashedSecret;

    /** Whoever created this key - the key authenticates *as* this user; see the module's service javadoc for why. */
    @Column(name = "created_by_user_id", nullable = false)
    private UUID createdByUserId;

    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    public ApiKey(UUID organizationId, String name, String keyPrefix, String hashedSecret, UUID createdByUserId, Instant expiresAt) {
        this.organizationId = organizationId;
        this.name = name;
        this.keyPrefix = keyPrefix;
        this.hashedSecret = hashedSecret;
        this.createdByUserId = createdByUserId;
        this.expiresAt = expiresAt;
    }

    public boolean isRevoked() {
        return revokedAt != null;
    }

    public boolean isExpired() {
        return expiresAt != null && expiresAt.isBefore(Instant.now());
    }

    public boolean isUsable() {
        return !isRevoked() && !isExpired();
    }
}
