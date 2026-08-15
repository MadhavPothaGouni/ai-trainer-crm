package com.aitrainercrm.platform.progressphoto.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.aitrainercrm.platform.common.exception.ForbiddenException;
import com.aitrainercrm.platform.contact.repository.ContactRepository;
import com.aitrainercrm.platform.progressphoto.dto.CreateProgressPhotoRequest;
import com.aitrainercrm.platform.progressphoto.entity.ProgressPhoto;
import com.aitrainercrm.platform.progressphoto.repository.ProgressPhotoRepository;
import com.aitrainercrm.platform.role.entity.Permission;
import com.aitrainercrm.platform.security.authorization.ScopeAuthorizationService;
import com.aitrainercrm.platform.security.userdetails.UserPrincipal;
import com.aitrainercrm.platform.user.repository.UserRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

/** See {@link ProgressPhotoService}'s javadoc - mostly exists to cover the resolveOwner self-vs-other split, same as every owner-scoped sibling. */
@ExtendWith(MockitoExtension.class)
class ProgressPhotoServiceTest {

    @Mock private ProgressPhotoRepository progressPhotoRepository;
    @Mock private ContactRepository contactRepository;
    @Mock private UserRepository userRepository;
    @Mock private ScopeAuthorizationService scopeAuthorizationService;
    @Mock private ApplicationEventPublisher events;

    private ProgressPhotoService service;

    private final UUID organizationId = UUID.randomUUID();
    private final UUID callerId = UUID.randomUUID();
    private final UUID contactId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new ProgressPhotoService(progressPhotoRepository, contactRepository, userRepository, scopeAuthorizationService, events);
    }

    private UserPrincipal principal() {
        return new UserPrincipal(callerId, "coach@example.com", organizationId, List.of());
    }

    @Test
    void create_noOwnerIdRequested_selfAssignsTheCallerAndStampsTakenAt() {
        when(contactRepository.existsByIdAndOrganizationIdAndDeletedAtIsNull(contactId, organizationId)).thenReturn(true);

        ProgressPhoto photo = service.create(
                principal(), new CreateProgressPhotoRequest(contactId, "https://example.com/photo.jpg", ProgressPhoto.Category.FRONT, null, null));

        assertThat(photo.getOwnerId()).isEqualTo(callerId);
        assertThat(photo.getTakenAt()).isNotNull();
    }

    @Test
    void create_assigningSomeoneElseWithoutOrganizationScope_isForbidden() {
        UUID otherUserId = UUID.randomUUID();
        when(scopeAuthorizationService.highestGranted(principal(), Permission.Resource.PROGRESS_PHOTO, Permission.Action.CREATE))
                .thenReturn(ScopeAuthorizationService.Access.TEAM);

        assertThatThrownBy(() -> service.create(
                        principal(), new CreateProgressPhotoRequest(contactId, "https://example.com/photo.jpg", ProgressPhoto.Category.FRONT, null, otherUserId)))
                .isInstanceOf(ForbiddenException.class);
    }
}
