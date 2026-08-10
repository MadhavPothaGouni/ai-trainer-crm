package com.aitrainercrm.platform.user.repository;

import com.aitrainercrm.platform.user.entity.User;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmailAndDeletedAtIsNull(String email);

    boolean existsByEmailAndDeletedAtIsNull(String email);

    @Query("select u from User u where u.id = :id and u.deletedAt is null")
    Optional<User> findActiveById(@Param("id") UUID id);

    long countByOrganizationIdAndDeletedAtIsNull(UUID organizationId);

    Page<User> findByOrganizationIdAndDeletedAtIsNull(UUID organizationId, Pageable pageable);

    /** Used to guard against ever leaving an organization with zero OWNERs - see UserService#assertOwnerRemains. */
    long countByOrganizationIdAndRoles_NameAndDeletedAtIsNull(UUID organizationId, String roleName);
}
