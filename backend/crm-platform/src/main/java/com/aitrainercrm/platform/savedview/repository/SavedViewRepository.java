package com.aitrainercrm.platform.savedview.repository;

import com.aitrainercrm.platform.savedview.entity.SavedView;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SavedViewRepository extends JpaRepository<SavedView, UUID> {

    /** Folds "does this row exist" and "is it mine" into one call, same reasoning NotificationRepository#findOwnById documents - someone else's saved view 404s exactly like one that doesn't exist, never 403s and confirms its existence. */
    Optional<SavedView> findByIdAndOrganizationIdAndOwnerUserId(UUID id, UUID organizationId, UUID ownerUserId);

    List<SavedView> findByOrganizationIdAndOwnerUserIdAndEntityTypeOrderByNameAsc(
            UUID organizationId, UUID ownerUserId, SavedView.EntityType entityType);

    /** The "unset the old one" half of SavedViewService#setDefault - see its javadoc for why the write order relative to the new default matters. */
    Optional<SavedView> findByOrganizationIdAndOwnerUserIdAndEntityTypeAndDefaultViewTrue(
            UUID organizationId, UUID ownerUserId, SavedView.EntityType entityType);
}
