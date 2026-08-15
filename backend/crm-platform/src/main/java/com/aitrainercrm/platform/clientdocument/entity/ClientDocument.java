package com.aitrainercrm.platform.clientdocument.entity;

import com.aitrainercrm.platform.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A signed document tied to one client - liability waiver, medical clearance, photo release, or
 * other paperwork. See V48's migration comment for the gap this fills. Owner-scoped like
 * {@link com.aitrainercrm.platform.clientgoal.entity.ClientGoal}, full OWN/TEAM/DEPARTMENT/
 * ORGANIZATION ladder; {@link #contactId} is the client the document is FOR (never the
 * authorization subject, same split ClientGoal#contactId already established). {@link #status}
 * is a free (non-linear) state machine like every other lifecycle field in this platform - a
 * REVOKED document being reinstated to SIGNED is a legitimate correction, never blocked.
 * {@link #signedAt} is stamped the first time status moves to SIGNED and never overwritten
 * afterward, same "stamp once" rule {@code Contract#signedAt} already establishes.
 */
@Entity
@Table(name = "client_documents")
@Getter
@Setter
@NoArgsConstructor
public class ClientDocument extends BaseEntity {

    public enum DocumentType {
        WAIVER, MEDICAL_CLEARANCE, PHOTO_RELEASE, CONTRACT_ADDENDUM, OTHER
    }

    public enum Status {
        PENDING, SIGNED, EXPIRED, REVOKED
    }

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    /** The client the document is FOR - never the authorization subject. See this class's javadoc. */
    @Column(name = "contact_id", nullable = false)
    private UUID contactId;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", nullable = false, length = 30)
    private DocumentType documentType = DocumentType.OTHER;

    @Column(nullable = false, length = 200)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.PENDING;

    /** Stamped the first time status moves to SIGNED, never overwritten afterward - see this class's javadoc. */
    @Column(name = "signed_at")
    private Instant signedAt;

    @Column(name = "expires_at")
    private LocalDate expiresAt;

    @Column(name = "file_url", length = 2000)
    private String fileUrl;

    @Column(length = 2000)
    private String notes;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    public ClientDocument(UUID organizationId, UUID contactId, UUID ownerId, String title) {
        this.organizationId = organizationId;
        this.contactId = contactId;
        this.ownerId = ownerId;
        this.title = title;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }
}
