package com.aitrainercrm.platform.intakeform.entity;

import com.aitrainercrm.platform.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One client's completed response to an {@link IntakeForm} - see V60's migration comment for the
 * backstory. Owner-scoped, same {@code contactId}-is-the-client / {@code ownerId}-is-the-
 * authorization-subject split every other contact-facing occurrence entity in this platform uses.
 * Has no status field - a completed submission is a point-in-time fact, same shape as
 * {@code ProgressPhoto}/{@code PromoRedemption}. {@code responses} is an opaque free-text blob (the
 * frontend JSON-encodes the client's answers into it) - this platform has no per-form-type schema
 * to validate structured answers against.
 */
@Entity
@Table(name = "intake_form_submissions")
@Getter
@Setter
@NoArgsConstructor
public class IntakeFormSubmission extends BaseEntity {

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "form_id", nullable = false)
    private UUID formId;

    @Column(name = "contact_id", nullable = false)
    private UUID contactId;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(name = "submitted_at", nullable = false)
    private Instant submittedAt = Instant.now();

    @Column(columnDefinition = "text")
    private String responses;

    @Column(length = 2000)
    private String notes;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    public IntakeFormSubmission(UUID organizationId, UUID formId, UUID contactId, UUID ownerId) {
        this.organizationId = organizationId;
        this.formId = formId;
        this.contactId = contactId;
        this.ownerId = ownerId;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }
}
