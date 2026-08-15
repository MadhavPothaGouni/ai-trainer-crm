package com.aitrainercrm.platform.groupclass.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.aitrainercrm.platform.contact.repository.ContactRepository;
import com.aitrainercrm.platform.groupclass.dto.CreateClassWaitlistRequest;
import com.aitrainercrm.platform.groupclass.entity.ClassWaitlist;
import com.aitrainercrm.platform.groupclass.repository.ClassWaitlistRepository;
import com.aitrainercrm.platform.security.authorization.ScopeAuthorizationService;
import com.aitrainercrm.platform.security.userdetails.UserPrincipal;
import com.aitrainercrm.platform.user.repository.UserRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

/**
 * Covers the two behaviors unique to this service: server-computed {@link ClassWaitlist#getPosition()}
 * (see {@link ClassWaitlistService#create}) and the stamp-once {@code notifiedAt} field
 * (see {@link ClassWaitlistService#updateStatus}).
 */
@ExtendWith(MockitoExtension.class)
class ClassWaitlistServiceTest {

    @Mock private ClassWaitlistRepository classWaitlistRepository;
    @Mock private ClassSessionService classSessionService;
    @Mock private ContactRepository contactRepository;
    @Mock private UserRepository userRepository;
    @Mock private ScopeAuthorizationService scopeAuthorizationService;
    @Mock private ApplicationEventPublisher events;

    private ClassWaitlistService service;

    private final UUID organizationId = UUID.randomUUID();
    private final UUID callerId = UUID.randomUUID();
    private final UUID classSessionId = UUID.randomUUID();
    private final UUID contactId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new ClassWaitlistService(
                classWaitlistRepository, classSessionService, contactRepository, userRepository, scopeAuthorizationService, events);
    }

    private UserPrincipal principal() {
        return new UserPrincipal(callerId, "staff@example.com", organizationId, List.of());
    }

    @Test
    void create_secondEntryForSameSession_getsNextPosition() {
        CreateClassWaitlistRequest request = new CreateClassWaitlistRequest(classSessionId, contactId, null, null);
        when(contactRepository.existsByIdAndOrganizationIdAndDeletedAtIsNull(contactId, organizationId)).thenReturn(true);
        when(classWaitlistRepository.countByClassSessionIdAndStatusAndDeletedAtIsNull(classSessionId, ClassWaitlist.Status.WAITING))
                .thenReturn(2L);

        ClassWaitlist waitlist = service.create(principal(), request);

        assertThat(waitlist.getPosition()).isEqualTo(3);
        assertThat(waitlist.getStatus()).isEqualTo(ClassWaitlist.Status.WAITING);
    }

    @Test
    void create_firstEntryForSession_getsPositionOne() {
        CreateClassWaitlistRequest request = new CreateClassWaitlistRequest(classSessionId, contactId, null, null);
        when(contactRepository.existsByIdAndOrganizationIdAndDeletedAtIsNull(contactId, organizationId)).thenReturn(true);
        when(classWaitlistRepository.countByClassSessionIdAndStatusAndDeletedAtIsNull(classSessionId, ClassWaitlist.Status.WAITING))
                .thenReturn(0L);

        ClassWaitlist waitlist = service.create(principal(), request);

        assertThat(waitlist.getPosition()).isEqualTo(1);
        assertThat(waitlist.getOwnerId()).isEqualTo(callerId);
    }

    @Test
    void updateStatus_movingToNotified_stampsNotifiedAtOnce() {
        ClassWaitlist waitlist = new ClassWaitlist(organizationId, classSessionId, contactId, callerId, 1);
        waitlist.setId(UUID.randomUUID());
        when(classWaitlistRepository.findActiveByIdAndOrganizationId(waitlist.getId(), organizationId)).thenReturn(Optional.of(waitlist));

        ClassWaitlist updated = service.updateStatus(principal(), waitlist.getId(), ClassWaitlist.Status.NOTIFIED);

        assertThat(updated.getStatus()).isEqualTo(ClassWaitlist.Status.NOTIFIED);
        assertThat(updated.getNotifiedAt()).isNotNull();
    }

    @Test
    void updateStatus_alreadyNotified_doesNotOverwriteNotifiedAt() {
        ClassWaitlist waitlist = new ClassWaitlist(organizationId, classSessionId, contactId, callerId, 1);
        waitlist.setId(UUID.randomUUID());
        Instant firstNotifiedAt = Instant.now().minusSeconds(3600);
        waitlist.setNotifiedAt(firstNotifiedAt);
        waitlist.setStatus(ClassWaitlist.Status.NOTIFIED);
        when(classWaitlistRepository.findActiveByIdAndOrganizationId(waitlist.getId(), organizationId)).thenReturn(Optional.of(waitlist));

        ClassWaitlist updated = service.updateStatus(principal(), waitlist.getId(), ClassWaitlist.Status.NOTIFIED);

        assertThat(updated.getNotifiedAt()).isEqualTo(firstNotifiedAt);
    }
}
