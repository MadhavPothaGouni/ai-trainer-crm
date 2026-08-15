package com.aitrainercrm.platform.clientfeedback.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.aitrainercrm.platform.clientfeedback.dto.CreateClientFeedbackRequest;
import com.aitrainercrm.platform.clientfeedback.entity.ClientFeedback;
import com.aitrainercrm.platform.clientfeedback.repository.ClientFeedbackRepository;
import com.aitrainercrm.platform.common.exception.ForbiddenException;
import com.aitrainercrm.platform.contact.repository.ContactRepository;
import com.aitrainercrm.platform.role.entity.Permission;
import com.aitrainercrm.platform.security.authorization.ScopeAuthorizationService;
import com.aitrainercrm.platform.security.userdetails.UserPrincipal;
import com.aitrainercrm.platform.user.repository.UserRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

/** See {@link ClientFeedbackService}'s javadoc - mostly exists to cover the resolveOwner self-vs-other split, same as every owner-scoped sibling. */
@ExtendWith(MockitoExtension.class)
class ClientFeedbackServiceTest {

    @Mock private ClientFeedbackRepository clientFeedbackRepository;
    @Mock private ContactRepository contactRepository;
    @Mock private UserRepository userRepository;
    @Mock private ScopeAuthorizationService scopeAuthorizationService;
    @Mock private ApplicationEventPublisher events;

    private ClientFeedbackService service;

    private final UUID organizationId = UUID.randomUUID();
    private final UUID callerId = UUID.randomUUID();
    private final UUID contactId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new ClientFeedbackService(clientFeedbackRepository, contactRepository, userRepository, scopeAuthorizationService, events);
    }

    private UserPrincipal principal() {
        return new UserPrincipal(callerId, "coach@example.com", organizationId, List.of());
    }

    @Test
    void create_noOwnerIdRequested_selfAssignsTheCaller() {
        when(contactRepository.existsByIdAndOrganizationIdAndDeletedAtIsNull(contactId, organizationId)).thenReturn(true);

        ClientFeedback feedback = service.create(
                principal(), new CreateClientFeedbackRequest(contactId, 9, ClientFeedback.RelatedType.SESSION, Instant.now(), "Great session", null));

        assertThat(feedback.getOwnerId()).isEqualTo(callerId);
        assertThat(feedback.getNpsScore()).isEqualTo(9);
        assertThat(feedback.getRelatedType()).isEqualTo(ClientFeedback.RelatedType.SESSION);
    }

    @Test
    void create_assigningSomeoneElseWithoutOrganizationScope_isForbidden() {
        UUID otherUserId = UUID.randomUUID();
        when(scopeAuthorizationService.highestGranted(any(UserPrincipal.class), eq(Permission.Resource.CLIENT_FEEDBACK), eq(Permission.Action.CREATE)))
                .thenReturn(ScopeAuthorizationService.Access.TEAM);

        assertThatThrownBy(() -> service.create(
                        principal(),
                        new CreateClientFeedbackRequest(contactId, 7, ClientFeedback.RelatedType.CLASS, Instant.now(), null, otherUserId)))
                .isInstanceOf(ForbiddenException.class);
    }
}
