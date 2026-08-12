package com.aitrainercrm.platform.savedview.service;

import com.aitrainercrm.platform.common.exception.ResourceNotFoundException;
import com.aitrainercrm.platform.savedview.dto.CreateSavedViewRequest;
import com.aitrainercrm.platform.savedview.dto.SavedViewDto;
import com.aitrainercrm.platform.savedview.dto.UpdateSavedViewRequest;
import com.aitrainercrm.platform.savedview.entity.SavedView;
import com.aitrainercrm.platform.savedview.repository.SavedViewRepository;
import com.aitrainercrm.platform.security.userdetails.UserPrincipal;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A user's own saved list-page filters. See {@link SavedView}'s javadoc and V26's migration
 * comment for why this skips the permission catalog entirely - every method here takes the
 * caller's own id as the only filter that matters, the same {@code findOwnOrThrow}-folds-
 * existence-and-ownership shape {@code NotificationService} uses, but purer: unlike {@code
 * NotificationService#create} (any org member can name any other member as recipient), {@link
 * #create} here always derives the owner from {@code principal}, ignoring anything a request
 * body could otherwise claim.
 */
@Service
@RequiredArgsConstructor
public class SavedViewService {

    private final SavedViewRepository savedViewRepository;

    @Transactional(readOnly = true)
    public List<SavedViewDto> list(UserPrincipal principal, SavedView.EntityType entityType) {
        return savedViewRepository
                .findByOrganizationIdAndOwnerUserIdAndEntityTypeOrderByNameAsc(principal.getOrganizationId(), principal.getId(), entityType)
                .stream()
                .map(SavedViewDto::from)
                .toList();
    }

    @Transactional
    public SavedViewDto create(UserPrincipal principal, CreateSavedViewRequest request) {
        SavedView view = new SavedView(
                principal.getOrganizationId(), principal.getId(), request.entityType(), request.name(), request.filters());
        view.setSortField(request.sortField());
        view.setSortDirection(request.sortDirection());
        savedViewRepository.save(view);
        return SavedViewDto.from(view);
    }

    @Transactional
    public SavedViewDto update(UserPrincipal principal, UUID viewId, UpdateSavedViewRequest request) {
        SavedView view = findOwnOrThrow(principal, viewId);
        view.setName(request.name());
        view.setFilters(request.filters());
        view.setSortField(request.sortField());
        view.setSortDirection(request.sortDirection());
        savedViewRepository.save(view);
        return SavedViewDto.from(view);
    }

    @Transactional
    public void delete(UserPrincipal principal, UUID viewId) {
        SavedView view = findOwnOrThrow(principal, viewId);
        savedViewRepository.delete(view);
    }

    /**
     * Marks {@code viewId} as this owner's default for its entity type, unsetting whichever view
     * held that spot before - the exact same unset-then-set shape {@code
     * DashboardService#setDefault} uses, for the exact same reason: {@code saved_views} has a
     * partial unique index enforcing at most one {@code is_default = true} row per {@code
     * (organization_id, owner_user_id, entity_type)} (see V26), Postgres checks a non-deferred
     * unique index immediately after each row-affecting statement rather than at commit, and
     * Hibernate's flush order follows persistence-context insertion order rather than setter-call
     * order - {@code view} entered the session first (via {@code findOwnOrThrow}), so a plain
     * save()+save() would flush its "is_default = true" UPDATE before the old default's
     * "is_default = false" one, momentarily giving this owner two defaults and tripping the
     * unique index (translated to a 409 by {@code GlobalExceptionHandler#handleDataIntegrityViolation}).
     * Flushing the unset immediately guarantees it reaches the database first.
     */
    @Transactional
    public SavedViewDto setDefault(UserPrincipal principal, UUID viewId) {
        SavedView view = findOwnOrThrow(principal, viewId);

        savedViewRepository
                .findByOrganizationIdAndOwnerUserIdAndEntityTypeAndDefaultViewTrue(
                        principal.getOrganizationId(), principal.getId(), view.getEntityType())
                .filter(current -> !current.getId().equals(view.getId()))
                .ifPresent(current -> {
                    current.setDefaultView(false);
                    savedViewRepository.saveAndFlush(current);
                });

        view.setDefaultView(true);
        savedViewRepository.save(view);
        return SavedViewDto.from(view);
    }

    private SavedView findOwnOrThrow(UserPrincipal principal, UUID viewId) {
        return savedViewRepository.findByIdAndOrganizationIdAndOwnerUserId(viewId, principal.getOrganizationId(), principal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("SavedView", viewId));
    }
}
