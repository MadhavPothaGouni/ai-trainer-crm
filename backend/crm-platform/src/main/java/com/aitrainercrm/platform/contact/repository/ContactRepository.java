package com.aitrainercrm.platform.contact.repository;

import com.aitrainercrm.platform.contact.entity.Contact;
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

public interface ContactRepository extends JpaRepository<Contact, UUID> {

    @Query("select c from Contact c where c.id = :id and c.organizationId = :organizationId and c.deletedAt is null")
    Optional<Contact> findActiveByIdAndOrganizationId(@Param("id") UUID id, @Param("organizationId") UUID organizationId);

    Page<Contact> findByOrganizationIdAndDeletedAtIsNull(UUID organizationId, Pageable pageable);

    Page<Contact> findByOrganizationIdAndOwnerIdInAndDeletedAtIsNull(UUID organizationId, Set<UUID> ownerIds, Pageable pageable);

    /** Unpaginated variants for CSV export - see AccountRepository's identical pair for why. */
    List<Contact> findByOrganizationIdAndDeletedAtIsNullOrderByCreatedAtDesc(UUID organizationId);

    List<Contact> findByOrganizationIdAndOwnerIdInAndDeletedAtIsNullOrderByCreatedAtDesc(UUID organizationId, Set<UUID> ownerIds);

    boolean existsByIdAndOrganizationIdAndDeletedAtIsNull(UUID id, UUID organizationId);

    /** DuplicateDetectionListener's email-match candidate search. */
    @Query(
            "select c from Contact c where c.organizationId = :organizationId and lower(c.email) = lower(:email) "
                    + "and c.id <> :excludeId and c.deletedAt is null")
    List<Contact> findDuplicateCandidatesByEmail(
            @Param("organizationId") UUID organizationId, @Param("email") String email, @Param("excludeId") UUID excludeId);

    /** Fallback when the contact has no email - first+last name only, no company field to scope it by (Contact has no companyName the way Lead does) - see DuplicateDetectionListener's javadoc for why NAME-reason Contact matches are a weaker signal than EMAIL-reason ones. */
    @Query(
            "select c from Contact c where c.organizationId = :organizationId and lower(c.firstName) = lower(:firstName) "
                    + "and lower(c.lastName) = lower(:lastName) and c.id <> :excludeId and c.deletedAt is null")
    List<Contact> findDuplicateCandidatesByName(
            @Param("organizationId") UUID organizationId, @Param("firstName") String firstName, @Param("lastName") String lastName,
            @Param("excludeId") UUID excludeId);

    /** DataSubjectRequestService's lookup for GDPR export/erasure - deliberately no deletedAt filter, since an already-soft-deleted Contact still holds live PII a right-to-be-forgotten request needs to reach. See V30's migration comment. */
    @Query("select c from Contact c where c.organizationId = :organizationId and lower(c.email) = lower(:email)")
    List<Contact> findByOrganizationIdAndEmailIgnoreCase(@Param("organizationId") UUID organizationId, @Param("email") String email);

    /** Reassigns every Contact pointing at the absorbed Account over to the survivor - the ACCOUNT half of DuplicateMatchService#merge's fan-out. Bulk update rather than load-N-then-save-N, same shape NotificationRepository#markAllRead uses. */
    @Modifying
    @Query("update Contact c set c.accountId = :survivorId where c.organizationId = :organizationId and c.accountId = :absorbedId")
    int reassignAccountId(
            @Param("organizationId") UUID organizationId, @Param("absorbedId") UUID absorbedId, @Param("survivorId") UUID survivorId);
}
