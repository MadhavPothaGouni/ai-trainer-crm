package com.aitrainercrm.platform.exercise.repository;

import com.aitrainercrm.platform.exercise.entity.PersonalRecord;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PersonalRecordRepository extends JpaRepository<PersonalRecord, UUID> {

    @Query("select r from PersonalRecord r where r.id = :id and r.organizationId = :organizationId and r.deletedAt is null")
    Optional<PersonalRecord> findActiveByIdAndOrganizationId(@Param("id") UUID id, @Param("organizationId") UUID organizationId);

    Page<PersonalRecord> findByOrganizationIdAndDeletedAtIsNull(UUID organizationId, Pageable pageable);

    Page<PersonalRecord> findByOrganizationIdAndOwnerIdInAndDeletedAtIsNull(UUID organizationId, Set<UUID> ownerIds, Pageable pageable);

    /** The contact's current best for this exercise+record type - used by {@code PersonalRecordService#assertIsImprovement}. */
    @Query(
            "select max(r.value) from PersonalRecord r where r.contactId = :contactId and r.exerciseId = :exerciseId "
                    + "and r.recordType = :recordType and r.deletedAt is null")
    Optional<BigDecimal> findBestValue(
            @Param("contactId") UUID contactId, @Param("exerciseId") UUID exerciseId, @Param("recordType") PersonalRecord.RecordType recordType);

    /** Same as above, but excluding the record being updated - used when re-checking an edit. */
    @Query(
            "select max(r.value) from PersonalRecord r where r.contactId = :contactId and r.exerciseId = :exerciseId "
                    + "and r.recordType = :recordType and r.id <> :excludeId and r.deletedAt is null")
    Optional<BigDecimal> findBestValueExcluding(
            @Param("contactId") UUID contactId,
            @Param("exerciseId") UUID exerciseId,
            @Param("recordType") PersonalRecord.RecordType recordType,
            @Param("excludeId") UUID excludeId);
}
