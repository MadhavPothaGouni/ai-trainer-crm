package com.aitrainercrm.platform.lead.repository;

import com.aitrainercrm.platform.lead.entity.Lead;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LeadRepository extends JpaRepository<Lead, UUID> {

    @Query("select l from Lead l where l.id = :id and l.organizationId = :organizationId and l.deletedAt is null")
    Optional<Lead> findActiveByIdAndOrganizationId(@Param("id") UUID id, @Param("organizationId") UUID organizationId);

    Page<Lead> findByOrganizationIdAndDeletedAtIsNull(UUID organizationId, Pageable pageable);

    Page<Lead> findByOrganizationIdAndOwnerIdInAndDeletedAtIsNull(UUID organizationId, Set<UUID> ownerIds, Pageable pageable);

    /** Unpaginated variants for CSV export - see AccountRepository's identical pair for why. */
    List<Lead> findByOrganizationIdAndDeletedAtIsNullOrderByCreatedAtDesc(UUID organizationId);

    List<Lead> findByOrganizationIdAndOwnerIdInAndDeletedAtIsNullOrderByCreatedAtDesc(UUID organizationId, Set<UUID> ownerIds);

    /** Existence check that respects tenant + soft-delete - used by ActivityService to validate a related-to reference. */
    boolean existsByIdAndOrganizationIdAndDeletedAtIsNull(UUID id, UUID organizationId);

    /** DuplicateDetectionListener's email-match candidate search - excludes the new lead itself and anything already CONVERTED (a converted lead's identity has effectively moved to its converted Account/Contact/Opportunity, so flagging it as a duplicate of a fresh lead isn't useful). DuplicateDetectionListener always passes Lead.Status.CONVERTED for excludedStatus; it's a parameter rather than a literal purely to sidestep JPQL enum-literal syntax. */
    @Query(
            "select l from Lead l where l.organizationId = :organizationId and lower(l.email) = lower(:email) "
                    + "and l.id <> :excludeId and l.status <> :excludedStatus and l.deletedAt is null")
    List<Lead> findDuplicateCandidatesByEmail(
            @Param("organizationId") UUID organizationId, @Param("email") String email, @Param("excludeId") UUID excludeId,
            @Param("excludedStatus") Lead.Status excludedStatus);

    /** DataSubjectRequestService's lookup for GDPR export/erasure - deliberately no deletedAt/status filter (unlike findDuplicateCandidatesByEmail above), since an already-soft-deleted or already-CONVERTED Lead still holds live PII a right-to-be-forgotten request needs to reach. See V30's migration comment. */
    @Query("select l from Lead l where l.organizationId = :organizationId and lower(l.email) = lower(:email)")
    List<Lead> findByOrganizationIdAndEmailIgnoreCase(@Param("organizationId") UUID organizationId, @Param("email") String email);

    /** Fallback candidate search when the lead has no email - first+last+company, case-insensitive. See DuplicateDetectionListener's javadoc for why this only runs when email is blank. */
    @Query(
            "select l from Lead l where l.organizationId = :organizationId and lower(l.firstName) = lower(:firstName) "
                    + "and lower(l.lastName) = lower(:lastName) and lower(l.companyName) = lower(:companyName) "
                    + "and l.id <> :excludeId and l.status <> :excludedStatus and l.deletedAt is null")
    List<Lead> findDuplicateCandidatesByName(
            @Param("organizationId") UUID organizationId, @Param("firstName") String firstName, @Param("lastName") String lastName,
            @Param("companyName") String companyName, @Param("excludeId") UUID excludeId, @Param("excludedStatus") Lead.Status excludedStatus);
}
