package com.aitrainercrm.platform.attachment.entity;

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
 * A file uploaded against exactly one Account/Contact/Opportunity/Lead/
 * Ticket - a contract, a screenshot, an invoice PDF. See V18's migration
 * comment for why this is a genuinely new permission-catalog resource
 * (like {@code EmailMessage}/{@code CalendarEvent}/{@code Team} before it),
 * owner-scoped exactly like {@code Ticket}.
 *
 * <p>{@link #storageKey} is an opaque pointer into whichever
 * {@code FileStorageService} implementation is active - never returned to
 * an API client directly; {@code AttachmentController#download} is the only
 * path back to the actual bytes. The file content itself is never stored
 * in this entity or in Postgres at all, same reasoning nothing else in
 * this schema stores large binary blobs in a column - see {@code
 * attachment.storage.FileStorageService}'s javadoc.
 *
 * <p>{@link #relatedToId} has no JPA relationship or DB foreign key, same
 * reasoning as every other polymorphic related-to reference in this
 * codebase (V4's migration comment has the fullest version) - unlike
 * {@code CalendarEvent}'s, it's required, not optional, the same shape as
 * {@code EmailMessage}'s: there's no "standalone" file upload concept in
 * this platform, every attachment is about some CRM record.
 */
@Entity
@Table(name = "attachments")
@Getter
@Setter
@NoArgsConstructor
public class Attachment extends BaseEntity {

    public enum RelatedToType {
        ACCOUNT, CONTACT, OPPORTUNITY, LEAD, TICKET
    }

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "related_to_type", nullable = false, length = 20)
    private RelatedToType relatedToType;

    @Column(name = "related_to_id", nullable = false)
    private UUID relatedToId;

    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

    @Column(name = "content_type", length = 150)
    private String contentType;

    @Column(name = "file_size_bytes", nullable = false)
    private long fileSizeBytes;

    /** Never serialized in AttachmentDto - see this class's javadoc. */
    @Column(name = "storage_key", nullable = false, length = 500)
    private String storageKey;

    @Column(length = 1000)
    private String description;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    public Attachment(
            UUID organizationId, RelatedToType relatedToType, UUID relatedToId,
            String fileName, String contentType, long fileSizeBytes, String storageKey, UUID ownerId) {
        this.organizationId = organizationId;
        this.relatedToType = relatedToType;
        this.relatedToId = relatedToId;
        this.fileName = fileName;
        this.contentType = contentType;
        this.fileSizeBytes = fileSizeBytes;
        this.storageKey = storageKey;
        this.ownerId = ownerId;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }
}
