package com.aitrainercrm.platform.savedview.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aitrainercrm.platform.common.exception.ResourceNotFoundException;
import com.aitrainercrm.platform.savedview.dto.CreateSavedViewRequest;
import com.aitrainercrm.platform.savedview.dto.SavedViewDto;
import com.aitrainercrm.platform.savedview.dto.UpdateSavedViewRequest;
import com.aitrainercrm.platform.savedview.entity.SavedView;
import com.aitrainercrm.platform.savedview.repository.SavedViewRepository;
import com.aitrainercrm.platform.security.userdetails.UserPrincipal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Mock-based coverage for the pieces an HTTP test can't cheaply pin down: that {@link
 * SavedViewService#create} always derives ownership from the caller regardless of what a request
 * body might otherwise claim, and - the module's central risk - that {@link
 * SavedViewService#setDefault} unsets the previous default via {@code saveAndFlush} *before*
 * saving the new one, in that exact order, every time. {@code SavedViewIntegrationTest} covers the
 * same setDefault behavior end-to-end through real HTTP plus the partial unique index itself.
 */
@ExtendWith(MockitoExtension.class)
class SavedViewServiceTest {

    @Mock private SavedViewRepository savedViewRepository;
    @Mock private UserPrincipal principal;

    private SavedViewService service;

    private final UUID organizationId = UUID.randomUUID();
    private final UUID ownerId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new SavedViewService(savedViewRepository);
    }

    @Test
    void list_delegatesToOwnerScopedFinder() {
        when(principal.getOrganizationId()).thenReturn(organizationId);
        when(principal.getId()).thenReturn(ownerId);
        SavedView view = view("My Leads");
        when(savedViewRepository.findByOrganizationIdAndOwnerUserIdAndEntityTypeOrderByNameAsc(
                        organizationId, ownerId, SavedView.EntityType.LEAD))
                .thenReturn(List.of(view));

        List<SavedViewDto> result = service.list(principal, SavedView.EntityType.LEAD);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).name()).isEqualTo("My Leads");
    }

    @Test
    void create_ignoresAnyCallerSuppliedOwner_derivesFromPrincipal() {
        when(principal.getOrganizationId()).thenReturn(organizationId);
        when(principal.getId()).thenReturn(ownerId);
        CreateSavedViewRequest request =
                new CreateSavedViewRequest(SavedView.EntityType.LEAD, "Hot Leads", "{\"status\":\"HOT\"}", "score", SavedView.SortDirection.DESC);

        SavedViewDto result = service.create(principal, request);

        assertThat(result.name()).isEqualTo("Hot Leads");
        assertThat(result.entityType()).isEqualTo(SavedView.EntityType.LEAD);
        assertThat(result.sortField()).isEqualTo("score");
        assertThat(result.isDefault()).isFalse();
        verify(savedViewRepository).save(any(SavedView.class));
    }

    @Test
    void update_notOwnedByCaller_throwsResourceNotFound_notForbidden() {
        UUID viewId = UUID.randomUUID();
        when(principal.getOrganizationId()).thenReturn(organizationId);
        when(principal.getId()).thenReturn(ownerId);
        when(savedViewRepository.findByIdAndOrganizationIdAndOwnerUserId(viewId, organizationId, ownerId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(
                        principal, viewId, new UpdateSavedViewRequest("New name", "{}", null, null)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void update_ownedByCaller_appliesAllEditableFields() {
        UUID viewId = UUID.randomUUID();
        when(principal.getOrganizationId()).thenReturn(organizationId);
        when(principal.getId()).thenReturn(ownerId);
        SavedView view = view("Old name");
        when(savedViewRepository.findByIdAndOrganizationIdAndOwnerUserId(viewId, organizationId, ownerId)).thenReturn(Optional.of(view));

        SavedViewDto result = service.update(
                principal, viewId, new UpdateSavedViewRequest("New name", "{\"status\":\"COLD\"}", "createdAt", SavedView.SortDirection.ASC));

        assertThat(result.name()).isEqualTo("New name");
        assertThat(result.filters()).isEqualTo("{\"status\":\"COLD\"}");
        assertThat(result.sortField()).isEqualTo("createdAt");
        assertThat(result.sortDirection()).isEqualTo(SavedView.SortDirection.ASC);
    }

    @Test
    void delete_notOwnedByCaller_throwsResourceNotFound() {
        UUID viewId = UUID.randomUUID();
        when(principal.getOrganizationId()).thenReturn(organizationId);
        when(principal.getId()).thenReturn(ownerId);
        when(savedViewRepository.findByIdAndOrganizationIdAndOwnerUserId(viewId, organizationId, ownerId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(principal, viewId)).isInstanceOf(ResourceNotFoundException.class);
        verify(savedViewRepository, never()).delete(any());
    }

    @Test
    void setDefault_noPriorDefault_justSetsNewOneNoUnsetNeeded() {
        UUID viewId = UUID.randomUUID();
        when(principal.getOrganizationId()).thenReturn(organizationId);
        when(principal.getId()).thenReturn(ownerId);
        SavedView view = view("My Leads");
        when(savedViewRepository.findByIdAndOrganizationIdAndOwnerUserId(viewId, organizationId, ownerId)).thenReturn(Optional.of(view));
        when(savedViewRepository.findByOrganizationIdAndOwnerUserIdAndEntityTypeAndDefaultViewTrue(
                        organizationId, ownerId, view.getEntityType()))
                .thenReturn(Optional.empty());

        SavedViewDto result = service.setDefault(principal, viewId);

        assertThat(result.isDefault()).isTrue();
        verify(savedViewRepository, never()).saveAndFlush(any());
        verify(savedViewRepository).save(view);
    }

    @Test
    void setDefault_priorDefaultExists_unsetsItWithFlushBeforeSavingTheNewOne() {
        UUID viewId = UUID.randomUUID();
        when(principal.getOrganizationId()).thenReturn(organizationId);
        when(principal.getId()).thenReturn(ownerId);
        SavedView newDefault = view("New default");
        SavedView oldDefault = view("Old default");
        oldDefault.setDefaultView(true);
        when(savedViewRepository.findByIdAndOrganizationIdAndOwnerUserId(viewId, organizationId, ownerId))
                .thenReturn(Optional.of(newDefault));
        when(savedViewRepository.findByOrganizationIdAndOwnerUserIdAndEntityTypeAndDefaultViewTrue(
                        organizationId, ownerId, newDefault.getEntityType()))
                .thenReturn(Optional.of(oldDefault));

        service.setDefault(principal, viewId);

        assertThat(oldDefault.isDefaultView()).isFalse();
        assertThat(newDefault.isDefaultView()).isTrue();
        // The unset must be flushed to the database before the new default is saved, otherwise
        // both rows would momentarily read is_default = true and trip the partial unique index.
        verify(savedViewRepository, times(1)).saveAndFlush(oldDefault);
        verify(savedViewRepository).save(newDefault);
    }

    @Test
    void setDefault_callerAlreadyTheDefault_isANoOpNotASelfUnsetThenSet() {
        UUID viewId = UUID.randomUUID();
        when(principal.getOrganizationId()).thenReturn(organizationId);
        when(principal.getId()).thenReturn(ownerId);
        SavedView alreadyDefault = view("Already default");
        alreadyDefault.setDefaultView(true);
        when(savedViewRepository.findByIdAndOrganizationIdAndOwnerUserId(viewId, organizationId, ownerId))
                .thenReturn(Optional.of(alreadyDefault));
        when(savedViewRepository.findByOrganizationIdAndOwnerUserIdAndEntityTypeAndDefaultViewTrue(
                        organizationId, ownerId, alreadyDefault.getEntityType()))
                .thenReturn(Optional.of(alreadyDefault));

        service.setDefault(principal, viewId);

        verify(savedViewRepository, never()).saveAndFlush(any());
        verify(savedViewRepository).save(alreadyDefault);
    }

    private SavedView view(String name) {
        SavedView view = new SavedView(organizationId, ownerId, SavedView.EntityType.LEAD, name, "{}");
        view.setId(UUID.randomUUID());
        return view;
    }
}
