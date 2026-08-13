package com.aitrainercrm.platform.contract.repository;

import com.aitrainercrm.platform.contract.entity.Contract;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ContractRepository extends JpaRepository<Contract, UUID> {

    @Query("select c from Contract c where c.id = :id and c.organizationId = :organizationId and c.deletedAt is null")
    Optional<Contract> findActiveByIdAndOrganizationId(@Param("id") UUID id, @Param("organizationId") UUID organizationId);

    Page<Contract> findByOrganizationIdAndDeletedAtIsNull(UUID organizationId, Pageable pageable);

    Page<Contract> findByOrganizationIdAndOwnerIdInAndDeletedAtIsNull(UUID organizationId, Set<UUID> ownerIds, Pageable pageable);

    boolean existsByOrganizationIdAndContractNumberAndDeletedAtIsNull(UUID organizationId, String contractNumber);
}
