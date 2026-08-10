package com.aitrainercrm.platform.security.jwt;

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
 */
@Component
public class JwtTokenProvider {

    private static final Logger log = LoggerFactory.getLogger(JwtTokenProvider.class);
    private static final String CLAIM_ORG_ID = "org";
    private static final String CLAIM_AUTHORITIES = "auth";
    private static final String CLAIM_EMAIL = "email";

    private final Key signingKey;
    private final JwtProperties properties;

    public JwtTokenProvider(JwtProperties properties) {
        this.properties = properties;
        this.signingKey = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
    }

    public String generateAccessToken(UserPrincipal principal) {
        Instant now = Instant.now();
        Instant expiry = now.plus(properties.accessTokenExpirationMinutes(), ChronoUnit.MINUTES);

        List<String> authorityNames = principal.getAuthorities().stream()
                .map(Object::toString)
                .toList();

        return Jwts.builder()
                .subject(principal.getId().toString())
                .claim(CLAIM_EMAIL, principal.getEmail())
                .claim(CLAIM_ORG_ID, principal.getOrganizationId() == null ? null : principal.getOrganizationId().toString())
                .claim(CLAIM_AUTHORITIES, authorityNames)
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
            List<String> authorities = claims.get(CLAIM_AUTHORITIES, List.class);

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
