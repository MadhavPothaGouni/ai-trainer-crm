package com.aitrainercrm.platform.certification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aitrainercrm.platform.certification.dto.AwardCertificationRequest;
import com.aitrainercrm.platform.certification.entity.Certification;
import com.aitrainercrm.platform.certification.entity.UserCertification;
import com.aitrainercrm.platform.certification.repository.UserCertificationRepository;
import com.aitrainercrm.platform.common.exception.ForbiddenException;
import com.aitrainercrm.platform.security.authorization.ScopeAuthorizationService;
import com.aitrainercrm.platform.security.userdetails.UserPrincipal;
import com.aitrainercrm.platform.user.repository.UserRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

/** See {@link UserCertificationService}'s javadoc - mirrors {@code CourseEnrollmentServiceTest}'s shape. */
@ExtendWith(MockitoExtension.class)
class UserCertificationServiceTest {

    @Mock private UserCertificationRepository userCertificationRepository;
    @Mock private CertificationService certificationService;
    @Mock private UserRepository userRepository;
    @Mock private ScopeAuthorizationService scopeAuthorizationService;
    @Mock private ApplicationEventPublisher events;

    private UserCertificationService service;

    private final UUID organizationId = UUID.randomUUID();
    private final UUID callerId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new UserCertificationService(userCertificationRepository, certificationService, userRepository, scopeAuthorizationService, events);
    }

    private UserPrincipal principal(UUID userId) {
        return new UserPrincipal(userId, "rep@example.com", organizationId, List.of());
    }

    @Test
    void award_certificationWithValidityMonths_computesExpiresAtFromEarnedDate() {
        UUID certificationId = UUID.randomUUID();
        Certification certification = certification(certificationId, 12);
        when(certificationService.findOrThrow(organizationId, certificationId)).thenReturn(certification);

        LocalDate earnedAt = LocalDate.of(2026, 1, 15);
        UserCertification result = service.award(
                principal(callerId), new AwardCertificationRequest(certificationId, null, earnedAt, "CRED-001"));

        assertThat(result.getUserId()).isEqualTo(callerId);
        assertThat(result.getExpiresAt()).isEqualTo(LocalDate.of(2027, 1, 15));
        verify(userCertificationRepository).save(result);
    }

    @Test
    void award_certificationWithNoValidityMonths_neverExpires() {
        UUID certificationId = UUID.randomUUID();
        Certification certification = certification(certificationId, null);
        when(certificationService.findOrThrow(organizationId, certificationId)).thenReturn(certification);

        UserCertification result = service.award(
                principal(callerId), new AwardCertificationRequest(certificationId, null, LocalDate.of(2026, 1, 15), null));

        assertThat(result.getExpiresAt()).isNull();
    }

    @Test
    void award_forSomeoneElseWithoutOrganizationScope_isForbidden() {
        UUID certificationId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();
        when(certificationService.findOrThrow(organizationId, certificationId)).thenReturn(certification(certificationId, 12));
        when(scopeAuthorizationService.highestGranted(any(), any(), any())).thenReturn(ScopeAuthorizationService.Access.TEAM);

        assertThatThrownBy(() -> service.award(
                        principal(callerId), new AwardCertificationRequest(certificationId, otherUserId, LocalDate.now(), null)))
                .isInstanceOf(ForbiddenException.class);
        verify(userCertificationRepository, never()).save(any());
    }

    @Test
    void updateStatus_revoking_persistsTheNewStatusAndNotes() {
        UUID userCertificationId = UUID.randomUUID();
        UserCertification userCertification = userCertification(userCertificationId, callerId);
        when(userCertificationRepository.findActiveByIdAndOrganizationId(userCertificationId, organizationId))
                .thenReturn(Optional.of(userCertification));

        UserCertification result = service.updateStatus(principal(callerId), userCertificationId, UserCertification.Status.REVOKED, "Policy violation");

        assertThat(result.getStatus()).isEqualTo(UserCertification.Status.REVOKED);
        assertThat(result.getNotes()).isEqualTo("Policy violation");
    }

    private Certification certification(UUID id, Integer validityMonths) {
        Certification certification = new Certification(organizationId, "Certified Solutions Consultant");
        certification.setId(id);
        certification.setValidityMonths(validityMonths);
        return certification;
    }

    private UserCertification userCertification(UUID id, UUID userId) {
        UserCertification userCertification = new UserCertification(organizationId, UUID.randomUUID(), userId, LocalDate.now());
        userCertification.setId(id);
        return userCertification;
    }
}
