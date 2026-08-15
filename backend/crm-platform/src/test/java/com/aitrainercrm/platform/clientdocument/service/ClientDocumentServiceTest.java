package com.aitrainercrm.platform.clientdocument.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aitrainercrm.platform.clientdocument.dto.CreateClientDocumentRequest;
import com.aitrainercrm.platform.clientdocument.entity.ClientDocument;
import com.aitrainercrm.platform.clientdocument.repository.ClientDocumentRepository;
import com.aitrainercrm.platform.common.exception.ForbiddenException;
import com.aitrainercrm.platform.contact.repository.ContactRepository;
import com.aitrainercrm.platform.security.authorization.ScopeAuthorizationService;
import com.aitrainercrm.platform.security.userdetails.UserPrincipal;
import com.aitrainercrm.platform.user.repository.UserRepository;
import java.time.Instant;
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

/** See {@link ClientDocumentService}'s javadoc for the shape this mirrors ({@code ClientGoalService}). */
@ExtendWith(MockitoExtension.class)
class ClientDocumentServiceTest {

    @Mock private ClientDocumentRepository clientDocumentRepository;
    @Mock private ContactRepository contactRepository;
    @Mock private UserRepository userRepository;
    @Mock private ScopeAuthorizationService scopeAuthorizationService;
    @Mock private ApplicationEventPublisher events;

    private ClientDocumentService service;

    private final UUID organizationId = UUID.randomUUID();
    private final UUID callerId = UUID.randomUUID();
    private final UUID contactId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new ClientDocumentService(clientDocumentRepository, contactRepository, userRepository, scopeAuthorizationService, events);
    }

    private UserPrincipal principal(UUID userId) {
        return new UserPrincipal(userId, "coach@example.com", organizationId, List.of());
    }

    private CreateClientDocumentRequest createRequest(UUID ownerId) {
        return new CreateClientDocumentRequest(contactId, ClientDocument.DocumentType.WAIVER, "Liability Waiver", LocalDate.of(2027, 1, 1), null, null, ownerId);
    }

    @Test
    void create_noOwnerIdRequested_selfAssignsTheCaller() {
        when(contactRepository.existsByIdAndOrganizationIdAndDeletedAtIsNull(contactId, organizationId)).thenReturn(true);

        ClientDocument result = service.create(principal(callerId), createRequest(null));

        assertThat(result.getOwnerId()).isEqualTo(callerId);
        assertThat(result.getContactId()).isEqualTo(contactId);
        assertThat(result.getStatus()).isEqualTo(ClientDocument.Status.PENDING);
        verify(clientDocumentRepository).save(result);
    }

    @Test
    void create_assigningSomeoneElseWithoutOrganizationScope_isForbidden() {
        UUID otherUserId = UUID.randomUUID();
        when(scopeAuthorizationService.highestGranted(any(), any(), any())).thenReturn(ScopeAuthorizationService.Access.TEAM);

        assertThatThrownBy(() -> service.create(principal(callerId), createRequest(otherUserId))).isInstanceOf(ForbiddenException.class);
        verify(clientDocumentRepository, never()).save(any());
    }

    @Test
    void updateStatus_movingToSignedForTheFirstTime_stampsSignedAt() {
        UUID documentId = UUID.randomUUID();
        ClientDocument document = new ClientDocument(organizationId, contactId, callerId, "Liability Waiver");
        document.setId(documentId);
        when(clientDocumentRepository.findActiveByIdAndOrganizationId(documentId, organizationId)).thenReturn(Optional.of(document));

        ClientDocument result = service.updateStatus(principal(callerId), documentId, ClientDocument.Status.SIGNED);

        assertThat(result.getStatus()).isEqualTo(ClientDocument.Status.SIGNED);
        assertThat(result.getSignedAt()).isNotNull();
    }

    @Test
    void updateStatus_movingToSignedASecondTime_doesNotOverwriteSignedAt() {
        UUID documentId = UUID.randomUUID();
        ClientDocument document = new ClientDocument(organizationId, contactId, callerId, "Liability Waiver");
        document.setId(documentId);
        Instant originalSignedAt = Instant.parse("2026-06-01T00:00:00Z");
        document.setSignedAt(originalSignedAt);
        document.setStatus(ClientDocument.Status.REVOKED);
        when(clientDocumentRepository.findActiveByIdAndOrganizationId(documentId, organizationId)).thenReturn(Optional.of(document));

        ClientDocument result = service.updateStatus(principal(callerId), documentId, ClientDocument.Status.SIGNED);

        assertThat(result.getSignedAt()).isEqualTo(originalSignedAt);
    }
}
