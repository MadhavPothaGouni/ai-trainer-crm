package com.aitrainercrm.platform.email.repository;

import com.aitrainercrm.platform.email.entity.EmailMessage;
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

public interface EmailMessageRepository extends JpaRepository<EmailMessage, UUID> {

    @Query("select e from EmailMessage e where e.id = :id and e.organizationId = :organizationId and e.deletedAt is null")
    Optional<EmailMessage> findActiveByIdAndOrganizationId(@Param("id") UUID id, @Param("organizationId") UUID organizationId);

    Page<EmailMessage> findByOrganizationIdAndDeletedAtIsNullOrderBySentAtDesc(UUID organizationId, Pageable pageable);

    Page<EmailMessage> findByOrganizationIdAndOwnerIdInAndDeletedAtIsNullOrderBySentAtDesc(
            UUID organizationId, Set<UUID> ownerIds, Pageable pageable);

    Page<EmailMessage> findByOrganizationIdAndRelatedToTypeAndRelatedToIdAndDeletedAtIsNullOrderBySentAtDesc(
            UUID organizationId, EmailMessage.RelatedToType relatedToType, UUID relatedToId, Pageable pageable);

    Page<EmailMessage> findByOrganizationIdAndOwnerIdInAndRelatedToTypeAndRelatedToIdAndDeletedAtIsNullOrderBySentAtDesc(
            UUID organizationId, Set<UUID> ownerIds, EmailMessage.RelatedToType relatedToType, UUID relatedToId, Pageable pageable);

    /** Unpaginated variant for CSV export - see AccountRepository's identical pair for why. */
    List<EmailMessage> findByOrganizationIdAndDeletedAtIsNullOrderBySentAtDesc(UUID organizationId);

    List<EmailMessage> findByOrganizationIdAndOwnerIdInAndDeletedAtIsNullOrderBySentAtDesc(UUID organizationId, Set<UUID> ownerIds);

    /** See ActivityRepository#reassignRelatedTo's javadoc - the EmailMessage quarter of DuplicateMatchService#merge's fan-out. */
    @Modifying
    @Query(
            "update EmailMessage e set e.relatedToId = :survivorId where e.organizationId = :organizationId "
                    + "and e.relatedToType = :relatedToType and e.relatedToId = :absorbedId")
    int reassignRelatedTo(
            @Param("organizationId") UUID organizationId, @Param("relatedToType") EmailMessage.RelatedToType relatedToType,
            @Param("absorbedId") UUID absorbedId, @Param("survivorId") UUID survivorId);
}
