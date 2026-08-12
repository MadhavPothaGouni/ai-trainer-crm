package com.aitrainercrm.platform.attachment.repository;

import com.aitrainercrm.platform.attachment.entity.Attachment;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AttachmentRepository extends JpaRepository<Attachment, UUID> {

    @Query("select a from Attachment a where a.id = :id and a.organizationId = :organizationId and a.deletedAt is null")
    Optional<Attachment> findActiveByIdAndOrganizationId(@Param("id") UUID id, @Param("organizationId") UUID organizationId);

    Page<Attachment> findByOrganizationIdAndDeletedAtIsNullOrderByCreatedAtDesc(UUID organizationId, Pageable pageable);

    Page<Attachment> findByOrganizationIdAndOwnerIdInAndDeletedAtIsNullOrderByCreatedAtDesc(
            UUID organizationId, Set<UUID> ownerIds, Pageable pageable);

    Page<Attachment> findByOrganizationIdAndRelatedToTypeAndRelatedToIdAndDeletedAtIsNullOrderByCreatedAtDesc(
            UUID organizationId, Attachment.RelatedToType relatedToType, UUID relatedToId, Pageable pageable);

    Page<Attachment> findByOrganizationIdAndOwnerIdInAndRelatedToTypeAndRelatedToIdAndDeletedAtIsNullOrderByCreatedAtDesc(
            UUID organizationId, Set<UUID> ownerIds, Attachment.RelatedToType relatedToType, UUID relatedToId, Pageable pageable);

    /** Unpaginated variant for CSV export - see AccountRepository's identical pair for why. */
    List<Attachment> findByOrganizationIdAndDeletedAtIsNullOrderByCreatedAtDesc(UUID organizationId);

    List<Attachment> findByOrganizationIdAndOwnerIdInAndDeletedAtIsNullOrderByCreatedAtDesc(UUID organizationId, Set<UUID> ownerIds);

    /** See ActivityRepository#reassignRelatedTo's javadoc - the Attachment quarter of DuplicateMatchService#merge's fan-out. */
    @Modifying
    @Query(
            "update Attachment a set a.relatedToId = :survivorId where a.organizationId = :organizationId "
                    + "and a.relatedToType = :relatedToType and a.relatedToId = :absorbedId")
    int reassignRelatedTo(
            @Param("organizationId") UUID organizationId, @Param("relatedToType") Attachment.RelatedToType relatedToType,
            @Param("absorbedId") UUID absorbedId, @Param("survivorId") UUID survivorId);
}
