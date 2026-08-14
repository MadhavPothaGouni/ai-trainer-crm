package com.aitrainercrm.platform.security.jwt;

import com.aitrainercrm.platform.role.service.RolePermissionCacheService;
import com.aitrainercrm.platform.security.userdetails.UserPrincipal;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Issues and validates short-lived JWT access tokens. Refresh tokens are
 * deliberately NOT JWTs - they're opaque random strings persisted (hashed)
 * in the database (see auth.entity.RefreshToken), because a refresh token
 * has to be revocable server-side (logout, password change, "sign out
 * everywhere") and a self-contained JWT can't be un-issued before it
 * naturally expires.
 *
 * <p>The token carries role ids, not the flattened permission list. Earlier this
 * embedded every authority name directly (one role's worth of "RESOURCE:ACTION:SCOPE"
 * strings), which was fine while the permission catalog was small - but it grows by
 * roughly a dozen entries with every new module, and a role like OWNER holds all of
 * them. That pushed a real access token past 20KB, which blew past Tomcat's default
 * 8KB max-http-header-size on the very next request (a 431 on whatever endpoint the
 * client called next, since the oversized Authorization header itself gets rejected
 * before the request even reaches a controller). Role ids don't grow with the
 * catalog - typically one or two per user - and the actual authority list gets
 * resolved from a cached lookup (RolePermissionCacheService) once per role id
 * instead of being duplicated into every token.
 */
@Component
public class JwtTokenProvider {

    private static final Logger log = LoggerFactory.getLogger(JwtTokenProvider.class);
    private static final String CLAIM_ORG_ID = "org";
    private static final String CLAIM_ROLE_IDS = "roles";
    private static final String CLAIM_EMAIL = "email";

    private final Key signingKey;
    private final JwtProperties properties;
    private final RolePermissionCacheService rolePermissionCacheService;

    public JwtTokenProvider(JwtProperties properties, RolePermissionCacheService rolePermissionCacheService) {
        this.properties = properties;
        this.rolePermissionCacheService = rolePermissionCacheService;
        this.signingKey = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
    }

    public String generateAccessToken(UserPrincipal principal) {
        Instant now = Instant.now();
        Instant expiry = now.plus(properties.accessTokenExpirationMinutes(), ChronoUnit.MINUTES);

        List<String> roleIds = principal.getRoleIds().stream().map(UUID::toString).toList();

        return Jwts.builder()
                .subject(principal.getId().toString())
                .claim(CLAIM_EMAIL, principal.getEmail())
                .claim(CLAIM_ORG_ID, principal.getOrganizationId() == null ? null : principal.getOrganizationId().toString())
                .claim(CLAIM_ROLE_IDS, roleIds)
                .issuer(properties.issuer())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(signingKey)
                .compact();
    }

    public int getAccessTokenExpirationSeconds() {
        return properties.accessTokenExpirationMinutes() * 60;
    }

    /** Returns empty rather than throwing on any parse/validation failure - callers just treat that as "not authenticated." */
    public java.util.Optional<UserPrincipal> parseAndValidate(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith((javax.crypto.SecretKey) signingKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            UUID userId = UUID.fromString(claims.getSubject());
            String email = claims.get(CLAIM_EMAIL, String.class);
            String orgIdStr = claims.get(CLAIM_ORG_ID, String.class);
            UUID orgId = orgIdStr == null ? null : UUID.fromString(orgIdStr);
            @SuppressWarnings("unchecked")
            List<String> roleIdStrings = claims.get(CLAIM_ROLE_IDS, List.class);

            List<String> authorities = roleIdStrings == null
                    ? List.of()
                    : roleIdStrings.stream()
                            .map(UUID::fromString)
                            .flatMap(roleId -> rolePermissionCacheService.getAuthorityNames(roleId).stream())
                            .distinct()
                            .toList();

            return java.util.Optional.of(new UserPrincipal(userId, email, orgId, authorities));
        } catch (ExpiredJwtException ex) {
            log.debug("JWT expired: {}", ex.getMessage());
            return java.util.Optional.empty();
        } catch (JwtException | IllegalArgumentException ex) {
            log.debug("JWT invalid: {}", ex.getMessage());
            return java.util.Optional.empty();
        }
    }
}
