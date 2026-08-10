package com.aitrainercrm.platform.auth.repository;

import com.aitrainercrm.platform.auth.entity.RefreshToken;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    List<RefreshToken> findByUserIdAndRevokedAtIsNull(UUID userId);

    // REQUIRES_NEW is deliberate, not decorative: AuthService.refresh() calls this
    // when it detects a reused (already-rotated) refresh token, then immediately
    // throws InvalidTokenException to reject the request. That throw would roll
    // back the *caller's* transaction - and by default this bulk update would join
    // that same transaction, so the "revoke every session" security response would
    // get silently undone along with everything else. Running it in its own
    // transaction means the revocation is durably committed before the exception
    // ever propagates, regardless of what the caller does afterward.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @Modifying
    @Query("update RefreshToken t set t.revokedAt = :now where t.userId = :userId and t.revokedAt is null")
    int revokeAllForUser(@Param("userId") UUID userId, @Param("now") Instant now);

    @Modifying
    @Query("delete from RefreshToken t where t.expiresAt < :cutoff")
    int deleteExpiredBefore(@Param("cutoff") Instant cutoff);
}
