package com.aitrainercrm.platform.gdpr.entity;

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
 * One row per GDPR/CCPA-style data-subject action taken against a person's email address - an
 * audit-log entry, the same "run log, not a soft-deletable business record" shape {@code
 * ImportJob} already established (no {@code deletedAt} here either). See V30's migration comment
 * for the full reasoning behind identifying the subject by email rather than a specific Contact/
 * Lead id, and for why {@link RequestType#ERASURE} scrubs PII columns in place instead of hard-
 * deleting rows.
 *
 * <p>{@link Status#COMPLETED} means the request ran to completion - which is true even when zero
 * Contacts/Leads matched the email, the same "ran to completion isn't the same as every row
 * succeeding" distinction {@code ImportJob#status}'s javadoc makes. {@link Status#FAILED} is
 * reserved for a request that threw before it could finish.
 */
@Entity
@Table(name = "data_subject_requests")
@Getter
@Setter
@NoArgsConstructor
public class DataSubjectRequest extends BaseEntity {

    public enum RequestType {
        EXPORT, ERASURE
    }

    public enum Status {
        COMPLETED, FAILED
    }

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "request_type", nullable = false, length = 20)
    private RequestType requestType;

    @Column(name = "subject_email", nullable = false, length = 255)
    private String subjectEmail;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;

    @Column(name = "initiated_by_user_id", nullable = false)
    private UUID initiatedByUserId;

    @Column(name = "contacts_affected", nullable = false)
    private int contactsAffected;

    @Column(name = "leads_affected", nullable = false)
    private int leadsAffected;

    @Column(name = "result_note", length = 500)
    private String resultNote;

    @Column(name = "completed_at")
    private Instant completedAt;

    public DataSubjectRequest(UUID organizationId, RequestType requestType, String subjectEmail, UUID initiatedByUserId) {
        this.organizationId = organizationId;
        this.requestType = requestType;
        this.subjectEmail = subjectEmail;
        this.initiatedByUserId = initiatedByUserId;
        this.status = Status.COMPLETED;
    }

    public static DataSubjectRequest failed(
            UUID organizationId, RequestType requestType, String subjectEmail, UUID initiatedByUserId, String reason) {
        DataSubjectRequest request = new DataSubjectRequest(organizationId, requestType, subjectEmail, initiatedByUserId);
        request.status = Status.FAILED;
        request.resultNote = reason;
        return request;
    }
}
