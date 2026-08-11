package com.aitrainercrm.platform.apikey.repository;

import com.aitrainercrm.platform.apikey.entity.ApiKey;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ApiKeyRepository extends JpaRepository<ApiKey, UUID> {

    Optional<ApiKey> findByKeyPrefix(String keyPrefix);

    @Query("select k from ApiKey k where k.id = :id and k.organizationId = :organizationId")
    Optional<ApiKey> findByIdAndOrganizationId(@Param("id") UUID id, @Param("organizationId") UUID organizationId);

    Page<ApiKey> findByOrganizationIdOrderByCreatedAtDesc(UUID organizationId, Pageable pageable);
}
