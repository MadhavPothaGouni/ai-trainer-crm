package com.aitrainercrm.platform.intakeform.entity;

import com.aitrainercrm.platform.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * The organization's catalog of intake questionnaires - see V60's migration comment for the
 * backstory. Shared-organization-catalog shape like {@link com.aitrainercrm.platform.room.entity.Room}:
 * no {@code ownerId}, TEAM/DEPARTMENT/ORGANIZATION scopes only.
 */
@Entity
@Table(name = "intake_forms")
@Getter
@Setter
@NoArgsConstructor
public class IntakeForm extends BaseEntity {

    public enum FormType {
        NEW_CLIENT, HEALTH_SCREENING, LIABILITY_WAIVER, OTHER
    }

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(nullable = false, length = 200)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(name = "form_type", nullable = false, length = 30)
    private FormType formType = FormType.OTHER;

    @Column(nullable = false)
    private boolean active = true;

    @Column(length = 2000)
    private String notes;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    public IntakeForm(UUID organizationId, String title) {
        this.organizationId = organizationId;
        this.title = title;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }
}
