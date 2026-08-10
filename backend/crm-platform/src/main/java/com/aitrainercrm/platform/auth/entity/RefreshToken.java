package com.aitrainercrm.platform.auth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UuidGenerator;

/**
 * An opaque, rotating refresh token. Only the SHA-256 hash of the raw token
 * is stored (mirrors password hashing: a DB dump should never hand out
 * usable tokens) - the raw value exists only in memory long enough to
 * return it to the client once, at issuance.
 *
 * <p>Rotation + reuse detection: every {@code /auth/refresh} call revokes
 * the token it was given and issues a brand new one, chained via
 * {@link #replacedByTokenId}. If a revoked token is ever presented again,
 * that's a strong signal it was stolen and reused (the legitimate client
 * would only ever have the newest token in the chain) - see
 * AuthService#refresh, which responds by revoking the entire chain.
 */
@Entity
@Table(name = "refresh_tokens")
@Getter
@Setter
@NoArgsConstructor
public class RefreshToken {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "replaced_by_token_id")
    private UUID replacedByTokenId;

    @Column(name = "device_info", length = 255)
    private String deviceInfo;

    @Column(name = "ip_address", length = 64)
    private String ipAddress;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;

    /**
     * Holds the raw (un-hashed) token only in memory, only between issuance
     * and the moment AuthService serializes it into an AuthResponse. Never
     * persisted (see {@code @Transient}) and never populated when a
     * RefreshToken is loaded back out of the database - only tokenHash is
     * ever read from storage.
     */
    @Transient
    private String rawTokenForResponseOnly;

    public RefreshToken(UUID userId, String tokenHash, Instant expiresAt, String deviceInfo, String ipAddress) {
        this.userId = userId;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
        this.deviceInfo = deviceInfo;
        this.ipAddress = ipAddress;
    }

    public boolean isExpired() {
        return expiresAt.isBefore(Instant.now());
    }

    public boolean isRevoked() {
        return revokedAt != null;
    }

    public boolean isUsable() {
        return !isExpired() && !isRevoked();
    }
}
