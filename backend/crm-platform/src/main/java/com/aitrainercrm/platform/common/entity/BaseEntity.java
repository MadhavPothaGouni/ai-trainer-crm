package com.aitrainercrm.platform.common.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * Shared columns every business entity in this platform gets: a UUID primary
 * key (not a sequential long - CRM ids get exposed in URLs/exports/webhooks
 * constantly, and a sequential id leaks "how many records exist" and makes
 * enumeration attacks trivial), audit timestamps, who created/last touched
 * the row, and an optimistic-locking version column.
 *
 * <p>{@code @Version} matters more than it might look here: several modules
 * (opportunities, orders, invoices) will have multiple users editing the
 * same record concurrently, and losing a concurrent update silently is
 * exactly the kind of bug that's expensive to track down in a CRM.
 */
@Getter
@Setter
@MappedSuperclass
@EqualsAndHashCode(of = "id")
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseEntity {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @CreatedBy
    @Column(name = "created_by", updatable = false)
    private UUID createdBy;

    @LastModifiedBy
    @Column(name = "updated_by")
    private UUID updatedBy;

    // Deliberately left uninitialized (no "= 0L" default). Spring Data JPA's
    // SimpleJpaRepository.save() decides persist() vs merge() by checking
    // isNew(), and for @Version entities that check is "is version null" -
    // not "is id null". A non-null default here made every brand-new entity
    // look pre-existing, so save() ran merge() instead of persist(). merge()
    // returns a *different* managed copy and leaves the object you passed in
    // untouched, which is why ids kept coming back null after .save(entity)
    // even though a row really was inserted. Hibernate initializes this to 0
    // itself on the actual insert.
    @Version
    @Column(name = "version", nullable = false)
    private Long version;
}
