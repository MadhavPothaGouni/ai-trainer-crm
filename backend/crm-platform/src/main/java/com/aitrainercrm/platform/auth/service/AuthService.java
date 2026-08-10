package com.aitrainercrm.platform.auth.service;

import com.aitrainercrm.platform.audit.event.AuthAuditEvents;
import com.aitrainercrm.platform.auth.dto.AuthResponse;
import com.aitrainercrm.platform.auth.dto.LoginRequest;
import com.aitrainercrm.platform.auth.dto.RegisterRequest;
import com.aitrainercrm.platform.auth.entity.EmailVerificationToken;
import com.aitrainercrm.platform.auth.entity.PasswordResetToken;
import com.aitrainercrm.platform.auth.entity.RefreshToken;
import com.aitrainercrm.platform.auth.exception.AccountLockedException;
import com.aitrainercrm.platform.auth.exception.InvalidCredentialsException;
import com.aitrainercrm.platform.auth.exception.InvalidTokenException;
import com.aitrainercrm.platform.auth.repository.EmailVerificationTokenRepository;
import com.aitrainercrm.platform.auth.repository.PasswordResetTokenRepository;
import com.aitrainercrm.platform.auth.repository.RefreshTokenRepository;
import com.aitrainercrm.platform.common.exception.DuplicateResourceException;
import com.aitrainercrm.platform.config.SecurityProperties;
import com.aitrainercrm.platform.notification.email.EmailService;
import com.aitrainercrm.platform.organization.entity.Organization;
import com.aitrainercrm.platform.organization.service.OrganizationService;
import com.aitrainercrm.platform.role.entity.Role;
import com.aitrainercrm.platform.role.service.RoleService;
import com.aitrainercrm.platform.security.jwt.JwtTokenProvider;
import com.aitrainercrm.platform.security.token.SecureTokenService;
import com.aitrainercrm.platform.security.userdetails.UserPrincipal;
import com.aitrainercrm.platform.user.entity.User;
import com.aitrainercrm.platform.user.repository.UserRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Everything auth-related funnels through this one service: registration,
 * login with lockout, refresh-token rotation with reuse detection,
 * password reset, email verification, and authenticated password change.
 * See RefreshToken's javadoc for the rotation/reuse-detection design; see
 * AuthAuditEvents for what gets recorded and why this class never writes
 * to the audit table directly.
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final EmailVerificationTokenRepository emailVerificationTokenRepository;
    private final OrganizationService organizationService;
    private final RoleService roleService;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final SecureTokenService secureTokenService;
    private final SecurityProperties securityProperties;
    private final EmailService emailService;
    private final ApplicationEventPublisher events;

    @Transactional
    public AuthResponse register(RegisterRequest request, String ipAddress) {
        String email = normalizeEmail(request.email());
        if (userRepository.existsByEmailAndDeletedAtIsNull(email)) {
            // Deliberately vague externally (DuplicateResourceException -> 409 with a generic
            // message) - see InvalidCredentialsException's javadoc for the same reasoning
            // applied to login; this one is a softer case since 409 already implies "exists."
            throw new DuplicateResourceException("An account with this email already exists");
        }

        String organizationName = request.organizationName() != null && !request.organizationName().isBlank()
                ? request.organizationName()
                : "%s's Organization".formatted(request.firstName());
        Organization organization = organizationService.createOrganization(organizationName);
        Role ownerRole = roleService.getOwnerRole(organization.getId());

        User user = new User(email, passwordEncoder.encode(request.password()), request.firstName(), request.lastName());
        user.setOrganizationId(organization.getId());
        user.addRole(ownerRole);
        userRepository.save(user);

        issueEmailVerificationToken(user);
        emailService.sendWelcomeEmail(user.getEmail(), user.getFirstName());

        events.publishEvent(new AuthAuditEvents.UserRegistered(user.getId(), user.getEmail(), organization.getId(), ipAddress));

        return issueTokenPair(user, "registration", ipAddress);
    }

    @Transactional
    public AuthResponse login(LoginRequest request, String deviceInfo, String ipAddress) {
        String email = normalizeEmail(request.email());
        User user = userRepository
                .findByEmailAndDeletedAtIsNull(email)
                .orElseGet(() -> {
                    events.publishEvent(new AuthAuditEvents.LoginFailed(email, ipAddress, "no such account"));
                    return null;
                });

        if (user == null) {
            throw new InvalidCredentialsException();
        }

        if (user.isAccountLocked()) {
            long minutesRemaining = Math.max(1, ChronoUnit.MINUTES.between(Instant.now(), user.getLockedUntil()));
            throw new AccountLockedException(minutesRemaining);
        }

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            registerFailedLoginAttempt(user, ipAddress);
            throw new InvalidCredentialsException();
        }

        if (user.getStatus() == User.Status.SUSPENDED || user.getStatus() == User.Status.DEACTIVATED) {
            throw new InvalidCredentialsException();
        }

        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        user.setLastLoginAt(Instant.now());
        userRepository.save(user);

        events.publishEvent(new AuthAuditEvents.UserLoggedIn(user.getId(), user.getEmail(), ipAddress, deviceInfo));

        return issueTokenPair(user, deviceInfo, ipAddress);
    }

    @Transactional
    public AuthResponse refresh(String rawRefreshToken, String deviceInfo, String ipAddress) {
        String tokenHash = secureTokenService.hash(rawRefreshToken);
        RefreshToken existing = refreshTokenRepository
                .findByTokenHash(tokenHash)
                .orElseThrow(() -> new InvalidTokenException("Refresh token is invalid"));

        if (existing.isRevoked()) {
            // A token that's already been rotated away is being presented again - the only
            // legitimate holder of the *current* token would never do this, so treat every
            // token issued to this user as compromised and force a full re-login.
            refreshTokenRepository.revokeAllForUser(existing.getUserId(), Instant.now());
            events.publishEvent(new AuthAuditEvents.RefreshTokenReused(existing.getUserId(), ipAddress));
            throw new InvalidTokenException("Refresh token has already been used - all sessions revoked for safety");
        }

        if (existing.isExpired()) {
            throw new InvalidTokenException("Refresh token has expired");
        }

        User user = userRepository
                .findActiveById(existing.getUserId())
                .orElseThrow(() -> new InvalidTokenException("Account no longer exists"));

        RefreshToken rotated = issueRefreshToken(user, deviceInfo, ipAddress);
        existing.setRevokedAt(Instant.now());
        existing.setReplacedByTokenId(rotated.getId());
        refreshTokenRepository.save(existing);

        String accessToken = jwtTokenProvider.generateAccessToken(new UserPrincipal(user));
        return AuthResponse.of(
                accessToken, rotated.getRawTokenForResponseOnly(), jwtTokenProvider.getAccessTokenExpirationSeconds(),
                user.getId(), user.getEmail(), user.getFullName());
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        String tokenHash = secureTokenService.hash(rawRefreshToken);
        refreshTokenRepository.findByTokenHash(tokenHash).ifPresent(token -> {
            if (!token.isRevoked()) {
                token.setRevokedAt(Instant.now());
                refreshTokenRepository.save(token);
                events.publishEvent(new AuthAuditEvents.UserLoggedOut(token.getUserId(), null));
            }
        });
    }

    @Transactional
    public void forgotPassword(String rawEmail, String ipAddress) {
        String email = normalizeEmail(rawEmail);
        // No DuplicateResourceException-style "account not found" here: this endpoint always
        // returns 200 regardless of whether the email exists, so it can't be used to enumerate
        // registered accounts.
        userRepository.findByEmailAndDeletedAtIsNull(email).ifPresent(user -> {
            String rawToken = secureTokenService.generateRawToken();
            Instant expiresAt = Instant.now().plus(securityProperties.passwordResetTokenExpirationMinutes(), ChronoUnit.MINUTES);
            passwordResetTokenRepository.save(
                    new PasswordResetToken(user.getId(), secureTokenService.hash(rawToken), expiresAt));

            emailService.sendPasswordResetEmail(user.getEmail(), rawToken);
            events.publishEvent(new AuthAuditEvents.PasswordResetRequested(user.getId(), user.getEmail(), ipAddress));
        });
    }

    @Transactional
    public void resetPassword(String rawToken, String newPassword) {
        String tokenHash = secureTokenService.hash(rawToken);
        PasswordResetToken token = passwordResetTokenRepository
                .findByTokenHash(tokenHash)
                .filter(PasswordResetToken::isUsable)
                .orElseThrow(() -> new InvalidTokenException("Password reset link is invalid or has expired"));

        User user = userRepository
                .findActiveById(token.getUserId())
                .orElseThrow(() -> new InvalidTokenException("Account no longer exists"));

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        token.setUsedAt(Instant.now());
        passwordResetTokenRepository.save(token);

        // A password reset is a strong signal the account may have been compromised (or the
        // owner just forgot it and wants everything else signed out) - either way, every
        // existing session should require re-authentication with the new password.
        refreshTokenRepository.revokeAllForUser(user.getId(), Instant.now());

        events.publishEvent(new AuthAuditEvents.PasswordChanged(user.getId(), user.getEmail()));
    }

    @Transactional
    public void changePassword(UUID userId, String currentPassword, String newPassword) {
        User user = userRepository
                .findActiveById(userId)
                .orElseThrow(() -> new InvalidCredentialsException());

        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        refreshTokenRepository.revokeAllForUser(user.getId(), Instant.now());

        events.publishEvent(new AuthAuditEvents.PasswordChanged(user.getId(), user.getEmail()));
    }

    @Transactional
    public void verifyEmail(String rawToken) {
        String tokenHash = secureTokenService.hash(rawToken);
        EmailVerificationToken token = emailVerificationTokenRepository
                .findByTokenHash(tokenHash)
                .filter(EmailVerificationToken::isUsable)
                .orElseThrow(() -> new InvalidTokenException("Verification link is invalid or has expired"));

        User user = userRepository
                .findActiveById(token.getUserId())
                .orElseThrow(() -> new InvalidTokenException("Account no longer exists"));

        user.setEmailVerified(true);
        if (user.getStatus() == User.Status.PENDING_VERIFICATION) {
            user.setStatus(User.Status.ACTIVE);
        }
        userRepository.save(user);

        token.setUsedAt(Instant.now());
        emailVerificationTokenRepository.save(token);
    }

    private void registerFailedLoginAttempt(User user, String ipAddress) {
        user.setFailedLoginAttempts(user.getFailedLoginAttempts() + 1);
        if (user.getFailedLoginAttempts() >= securityProperties.maxFailedLoginAttempts()) {
            user.setLockedUntil(Instant.now().plus(securityProperties.accountLockoutMinutes(), ChronoUnit.MINUTES));
            events.publishEvent(new AuthAuditEvents.AccountLocked(user.getId(), user.getEmail(), ipAddress));
        }
        userRepository.save(user);
        events.publishEvent(new AuthAuditEvents.LoginFailed(user.getEmail(), ipAddress, "wrong password"));
    }

    private void issueEmailVerificationToken(User user) {
        String rawToken = secureTokenService.generateRawToken();
        Instant expiresAt = Instant.now().plus(securityProperties.emailVerificationTokenExpirationHours(), ChronoUnit.HOURS);
        emailVerificationTokenRepository.save(
                new EmailVerificationToken(user.getId(), secureTokenService.hash(rawToken), expiresAt));
        emailService.sendEmailVerificationEmail(user.getEmail(), rawToken);
    }

    private AuthResponse issueTokenPair(User user, String deviceInfo, String ipAddress) {
        RefreshToken refreshToken = issueRefreshToken(user, deviceInfo, ipAddress);
        String accessToken = jwtTokenProvider.generateAccessToken(new UserPrincipal(user));
        return AuthResponse.of(
                accessToken, refreshToken.getRawTokenForResponseOnly(), jwtTokenProvider.getAccessTokenExpirationSeconds(),
                user.getId(), user.getEmail(), user.getFullName());
    }

    private RefreshToken issueRefreshToken(User user, String deviceInfo, String ipAddress) {
        String rawToken = secureTokenService.generateRawToken();
        Instant expiresAt = Instant.now().plus(30, ChronoUnit.DAYS);
        RefreshToken token = new RefreshToken(user.getId(), secureTokenService.hash(rawToken), expiresAt, deviceInfo, ipAddress);
        token.setRawTokenForResponseOnly(rawToken);
        return refreshTokenRepository.save(token);
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase();
    }
}
