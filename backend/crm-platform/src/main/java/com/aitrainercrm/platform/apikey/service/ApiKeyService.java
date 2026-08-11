package com.aitrainercrm.platform.apikey.service;

import com.aitrainercrm.platform.apikey.dto.ApiKeyDto;
import com.aitrainercrm.platform.apikey.dto.CreateApiKeyRequest;
import com.aitrainercrm.platform.apikey.entity.ApiKey;
import com.aitrainercrm.platform.apikey.repository.ApiKeyRepository;
import com.aitrainercrm.platform.audit.event.CrmAuditEvents;
import com.aitrainercrm.platform.common.exception.ResourceNotFoundException;
import com.aitrainercrm.platform.security.userdetails.UserPrincipal;
import com.aitrainercrm.platform.user.entity.User;
import com.aitrainercrm.platform.user.repository.UserRepository;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Issues and validates programmatic-auth API keys. A key authenticates
 * *as the user who created it* - {@link #authenticate} returns a
 * {@code UserPrincipal} for that user, built the same way
 * {@code AuthService#login} builds one from that user's own login. That's a
 * deliberate scope trim (a
 * fuller implementation would let the creator delegate a subset of their
 * permissions to the key, not all of them) called out in V6's migration
 * comment and the root README's Roadmap - it keeps the RBAC story
 * completely unchanged (a key can never do more than its creator can, and
 * automatically loses access the moment its creator is deactivated,
 * demoted, or removed) rather than inventing a second, parallel
 * authorization model just for keys.
 */
@Service
@RequiredArgsConstructor
public class ApiKeyService {

    private static final String KEY_PREFIX_TAG = "ak_";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final ApiKeyRepository apiKeyRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher events;

    @Transactional(readOnly = true)
    public Page<ApiKeyDto> list(UserPrincipal principal, Pageable pageable) {
        return apiKeyRepository
                .findByOrganizationIdOrderByCreatedAtDesc(principal.getOrganizationId(), pageable)
                .map(ApiKeyDto::from);
    }

    /**
     * Generates a fresh {@code prefix.secret} key, stores only a bcrypt
     * hash of the secret half, and returns the one and only response that
     * will ever contain the raw value - see {@link ApiKeyDto}'s javadoc.
     */
    @Transactional
    public ApiKeyDto create(UserPrincipal principal, CreateApiKeyRequest request) {
        String prefix = KEY_PREFIX_TAG + randomUrlSafeToken(8);
        String secret = randomUrlSafeToken(32);
        String rawKey = prefix + "." + secret;

        ApiKey apiKey = new ApiKey(
                principal.getOrganizationId(), request.name(), prefix, passwordEncoder.encode(secret), principal.getId(), request.expiresAt());
        apiKeyRepository.save(apiKey);

        events.publishEvent(new CrmAuditEvents.RecordCreated(principal.getId(), principal.getOrganizationId(), "ApiKey", apiKey.getId()));

        return ApiKeyDto.builder()
                .id(apiKey.getId())
                .name(apiKey.getName())
                .keyPrefix(apiKey.getKeyPrefix())
                .createdByUserId(apiKey.getCreatedByUserId())
                .expiresAt(apiKey.getExpiresAt())
                .createdAt(apiKey.getCreatedAt())
                .rawKey(rawKey)
                .build();
    }

    @Transactional
    public void revoke(UserPrincipal principal, UUID apiKeyId) {
        ApiKey apiKey = apiKeyRepository
                .findByIdAndOrganizationId(apiKeyId, principal.getOrganizationId())
                .orElseThrow(() -> new ResourceNotFoundException("ApiKey", apiKeyId));
        apiKey.setRevokedAt(Instant.now());
        apiKeyRepository.save(apiKey);

        events.publishEvent(new CrmAuditEvents.RecordDeleted(principal.getId(), principal.getOrganizationId(), "ApiKey", apiKeyId));
    }

    /**
     * Called by {@code ApiKeyAuthenticationFilter} on every request bearing
     * an {@code X-Api-Key} header. Splits {@code prefix.secret}, looks the
     * key up by its (indexed, non-secret) prefix, and only then does the
     * relatively expensive bcrypt comparison against the secret half -
     * never against a key that doesn't exist, is revoked, or has expired.
     * On success, best-effort stamps {@code lastUsedAt} (a failed save here
     * shouldn't fail the request it's just trying to timestamp).
     *
     * <p>Returns a fully-built {@code UserPrincipal}, not a bare {@code User},
     * and builds it right here rather than leaving that to the caller. That
     * matters because {@code UserPrincipal}'s constructor walks every one of
     * the user's roles and flattens each role's permissions into authorities
     * - {@code User.roles} is {@code EAGER} so that part's always safe, but
     * {@code Role.permissions} is deliberately {@code LAZY} (see {@code
     * Role}'s javadoc - eagerly joining permissions onto every {@code Role}
     * fetched anywhere, including plain role-management list screens that
     * never need them, isn't a trade worth making just for this one call
     * site). Building the principal after this method returns means doing it
     * outside this method's {@code @Transactional} session, which is exactly
     * what used to throw {@code LazyInitializationException} here -
     * {@code ApiKeyAuthenticationFilter} used to do {@code
     * .map(UserPrincipal::new)} on the result of this call, by which point
     * the session was already closed. {@code AuthService#login} never hits
     * this because it already builds its {@code UserPrincipal} before its own
     * {@code @Transactional} method returns; this does the same thing.
     */
    @Transactional
    public Optional<UserPrincipal> authenticate(String rawKey) {
        int separator = rawKey.indexOf('.');
        if (separator < 0) return Optional.empty();
        String prefix = rawKey.substring(0, separator);
        String secret = rawKey.substring(separator + 1);

        return apiKeyRepository
                .findByKeyPrefix(prefix)
                .filter(ApiKey::isUsable)
                .filter(apiKey -> passwordEncoder.matches(secret, apiKey.getHashedSecret()))
                .flatMap(apiKey -> {
                    apiKey.setLastUsedAt(Instant.now());
                    apiKeyRepository.save(apiKey);
                    return userRepository.findActiveById(apiKey.getCreatedByUserId());
                })
                // Deliberately not requiring Status.ACTIVE here: a freshly-registered
                // owner is PENDING_VERIFICATION until they log in once (see
                // AuthService#login), and JwtAuthenticationFilter never gates on status
                // at all - it authenticates PENDING_VERIFICATION users just fine, as
                // every other request in ApiKeyIntegrationTest itself demonstrates. An
                // API key should authenticate exactly as much as its creator's own JWT
                // does, no more and no less; requiring ACTIVE here made key auth
                // *stricter* than the login it's supposed to stand in for. SUSPENDED
                // and DEACTIVATED are the two statuses AuthService#login itself treats
                // as hard blocks, so those (plus a locked account) are what disqualify
                // a key too.
                .filter(user -> user.getStatus() != User.Status.SUSPENDED
                        && user.getStatus() != User.Status.DEACTIVATED
                        && !user.isAccountLocked())
                .map(UserPrincipal::new);
    }

    private String randomUrlSafeToken(int byteLength) {
        byte[] bytes = new byte[byteLength];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
