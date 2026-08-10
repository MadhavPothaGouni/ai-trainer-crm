package com.aitrainercrm.platform.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aitrainercrm.platform.auth.dto.AuthResponse;
import com.aitrainercrm.platform.auth.dto.LoginRequest;
import com.aitrainercrm.platform.auth.dto.RegisterRequest;
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
import com.aitrainercrm.platform.user.entity.User;
import com.aitrainercrm.platform.user.repository.UserRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Every dependency is mocked - no Spring context, no database - so this
 * suite runs in milliseconds and pins down the business rules that live in
 * AuthService itself: enumeration-safe error messages, lockout after N
 * failed attempts, refresh-token rotation, and reuse detection. The
 * happy-path "does this actually work end-to-end against Postgres" case is
 * covered separately by AuthControllerIntegrationTest.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private PasswordResetTokenRepository passwordResetTokenRepository;
    @Mock private EmailVerificationTokenRepository emailVerificationTokenRepository;
    @Mock private OrganizationService organizationService;
    @Mock private RoleService roleService;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtTokenProvider jwtTokenProvider;
    @Mock private SecureTokenService secureTokenService;
    @Mock private EmailService emailService;
    @Mock private ApplicationEventPublisher events;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        SecurityProperties securityProperties = new SecurityProperties(5, 15, 30, 24);
        authService = new AuthService(
                userRepository, refreshTokenRepository, passwordResetTokenRepository,
                emailVerificationTokenRepository, organizationService, roleService, passwordEncoder,
                jwtTokenProvider, secureTokenService, securityProperties, emailService, events);

        // Shared stubs used by (almost) every flow that issues a fresh token pair.
        when(secureTokenService.generateRawToken()).thenReturn("raw-token-value");
        when(secureTokenService.hash(anyString())).thenReturn("hashed-token-value");
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void register_createsOrganizationAndOwner_andReturnsTokenPair() {
        RegisterRequest request = new RegisterRequest("new.user@example.com", "Str0ng!Pass", "Ada", "Lovelace", null);
        Organization organization = organizationWithId("Ada Lovelace's Organization");
        Role ownerRole = new Role(RoleService.OWNER, "Full access", organization.getId(), false);

        when(userRepository.existsByEmailAndDeletedAtIsNull("new.user@example.com")).thenReturn(false);
        when(organizationService.createOrganization(anyString())).thenReturn(organization);
        when(roleService.getOwnerRole(organization.getId())).thenReturn(ownerRole);
        when(passwordEncoder.encode("Str0ng!Pass")).thenReturn("bcrypt-hash");
        when(jwtTokenProvider.generateAccessToken(any())).thenReturn("access-token");
        when(jwtTokenProvider.getAccessTokenExpirationSeconds()).thenReturn(900);

        AuthResponse response = authService.register(request, "203.0.113.5");

        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.refreshToken()).isEqualTo("raw-token-value");
        assertThat(response.email()).isEqualTo("new.user@example.com");
        verify(userRepository, times(1)).save(any(User.class));
        verify(emailService).sendWelcomeEmail(eq("new.user@example.com"), eq("Ada"));
        verify(emailService).sendEmailVerificationEmail(eq("new.user@example.com"), anyString());
    }

    @Test
    void register_whenEmailAlreadyExists_throwsDuplicateResourceException_withoutTouchingOrganizations() {
        RegisterRequest request = new RegisterRequest("taken@example.com", "Str0ng!Pass", "Ada", "Lovelace", null);
        when(userRepository.existsByEmailAndDeletedAtIsNull("taken@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(request, "203.0.113.5"))
                .isInstanceOf(DuplicateResourceException.class);

        verify(organizationService, never()).createOrganization(anyString());
    }

    @Test
    void login_withCorrectPassword_returnsTokenPair_andResetsFailedAttempts() {
        User user = activeUser("member@example.com", "bcrypt-hash");
        user.setFailedLoginAttempts(3);
        LoginRequest request = new LoginRequest("member@example.com", "correct-password");

        when(userRepository.findByEmailAndDeletedAtIsNull("member@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("correct-password", "bcrypt-hash")).thenReturn(true);
        when(jwtTokenProvider.generateAccessToken(any())).thenReturn("access-token");
        when(jwtTokenProvider.getAccessTokenExpirationSeconds()).thenReturn(900);

        AuthResponse response = authService.login(request, "JUnit/1.0", "203.0.113.5");

        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(user.getFailedLoginAttempts()).isZero();
    }

    @Test
    void login_withWrongPassword_incrementsFailedAttempts_andThrowsGenericInvalidCredentials() {
        User user = activeUser("member@example.com", "bcrypt-hash");
        LoginRequest request = new LoginRequest("member@example.com", "wrong-password");

        when(userRepository.findByEmailAndDeletedAtIsNull("member@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong-password", "bcrypt-hash")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(request, "JUnit/1.0", "203.0.113.5"))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Invalid email or password");

        assertThat(user.getFailedLoginAttempts()).isEqualTo(1);
    }

    @Test
    void login_whenAccountIsLocked_throwsAccountLockedException_withoutCheckingPassword() {
        User user = activeUser("locked@example.com", "bcrypt-hash");
        user.setLockedUntil(Instant.now().plus(10, ChronoUnit.MINUTES));
        LoginRequest request = new LoginRequest("locked@example.com", "irrelevant");

        when(userRepository.findByEmailAndDeletedAtIsNull("locked@example.com")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.login(request, "JUnit/1.0", "203.0.113.5"))
                .isInstanceOf(AccountLockedException.class);

        verify(passwordEncoder, never()).matches(anyString(), anyString());
    }

    @Test
    void login_withUnknownEmail_throwsSameGenericExceptionAsWrongPassword() {
        LoginRequest request = new LoginRequest("nobody@example.com", "whatever");
        when(userRepository.findByEmailAndDeletedAtIsNull("nobody@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(request, "JUnit/1.0", "203.0.113.5"))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Invalid email or password");
    }

    @Test
    void refresh_whenTokenWasAlreadyRevoked_revokesEntireChain_andRejects() {
        UUID userId = UUID.randomUUID();
        RefreshToken revoked = new RefreshToken(userId, "hashed-token-value", Instant.now().plusSeconds(3600), "d", "ip");
        revoked.setRevokedAt(Instant.now().minusSeconds(60));

        when(refreshTokenRepository.findByTokenHash("hashed-token-value")).thenReturn(Optional.of(revoked));

        assertThatThrownBy(() -> authService.refresh("some-raw-token", "JUnit/1.0", "203.0.113.5"))
                .isInstanceOf(InvalidTokenException.class);

        verify(refreshTokenRepository).revokeAllForUser(eq(userId), any(Instant.class));
        verify(events).publishEvent(any(com.aitrainercrm.platform.audit.event.AuthAuditEvents.RefreshTokenReused.class));
    }

    @Test
    void refresh_whenTokenIsExpired_rejectsWithoutRevokingAnything() {
        UUID userId = UUID.randomUUID();
        RefreshToken expired = new RefreshToken(userId, "hashed-token-value", Instant.now().minusSeconds(60), "d", "ip");

        when(refreshTokenRepository.findByTokenHash("hashed-token-value")).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> authService.refresh("some-raw-token", "JUnit/1.0", "203.0.113.5"))
                .isInstanceOf(InvalidTokenException.class);

        verify(refreshTokenRepository, never()).revokeAllForUser(any(), any());
    }

    @Test
    void forgotPassword_withUnregisteredEmail_doesNothing_butDoesNotThrow() {
        when(userRepository.findByEmailAndDeletedAtIsNull("ghost@example.com")).thenReturn(Optional.empty());

        authService.forgotPassword("ghost@example.com", "203.0.113.5");

        verify(passwordResetTokenRepository, never()).save(any());
        verify(emailService, never()).sendPasswordResetEmail(anyString(), anyString());
    }

    @Test
    void changePassword_withWrongCurrentPassword_throws_andNeverRevokesSessions() {
        UUID userId = UUID.randomUUID();
        User user = activeUser("member@example.com", "bcrypt-hash");
        when(userRepository.findActiveById(userId)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong-current", "bcrypt-hash")).thenReturn(false);

        assertThatThrownBy(() -> authService.changePassword(userId, "wrong-current", "New!Passw0rd"))
                .isInstanceOf(InvalidCredentialsException.class);

        verify(refreshTokenRepository, never()).revokeAllForUser(any(), any());
    }

    private User activeUser(String email, String passwordHash) {
        User user = new User(email, passwordHash, "Member", "User");
        user.setStatus(User.Status.ACTIVE);
        return user;
    }

    private Organization organizationWithId(String name) {
        Organization organization = new Organization(name, "ada-lovelaces-organization");
        organization.setId(UUID.randomUUID());
        return organization;
    }
}
