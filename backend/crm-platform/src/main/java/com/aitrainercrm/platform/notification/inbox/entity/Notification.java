package com.aitrainercrm.platform.notification.inbox.entity;

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
 * One teammate's personal inbox item - "you were assigned this ticket," "you
 * were mentioned on this lead." See V17's migration comment for the full
 * backstory, but the short version: this is not a fourth owner-scoped or
 * shared-org-resource module. A notification's visibility never widens with
 * role the way a Ticket's or an EmailMessage's does (a manager doesn't get
 * to read a teammate's notification feed just by holding a higher scope),
 * so it has no {@code Permission.Resource} entry and {@code
 * NotificationService} never calls {@code ScopeAuthorizationService} -
 * every check is simply "does {@link #recipientUserId} equal the caller."
 *
 * <p>{@link #senderUserId} is who triggered the notification, kept purely
 * for display ("From Priya Patel") - unlike {@code ownerId} everywhere else
 * in this schema, it grants no access of its own. {@link #relatedToType}/
 * {@link #relatedToId} are optional and validated the same way {@code
 * CalendarEvent}'s are (both-null-or-both-set, no DB foreign key - see
 * {@code CalendarEventService#validateRelatedTo}) since a general
 * announcement has no single CRM record to deep-link to. There is
 * deliberately no {@code deletedAt} - see V17's migration comment for why a
 * personal inbox item can just be hard-deleted.
 */
@Entity
@Table(name = "notifications")
@Getter
@Setter
@NoArgsConstructor
public class Notification extends BaseEntity {

    public enum Type {
        ASSIGNMENT, MENTION, REMINDER, GENERAL,
        /** System-generated, never sent by a human via {@code POST /notifications} - see NotificationService#createSystem and SlaEvaluationService's javadoc. No DB check constraint on this column (see V17), so adding a value here needed no migration. */
        ESCALATION
    }

    public enum RelatedToType {
        ACCOUNT, CONTACT, OPPORTUNITY, LEAD, TICKET
    }

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "recipient_user_id", nullable = false)
    private UUID recipientUserId;

    /** Nullable - a future system-generated notification (e.g. a scheduled digest) has no human sender. */
    @Column(name = "sender_user_id")
    private UUID senderUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Type type;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(length = 2000)
    private String body;

    @Enumerated(EnumType.STRING)
    @Column(name = "related_to_type", length = 20)
    private RelatedToType relatedToType;

    @Column(name = "related_to_id")
    private UUID relatedToId;

    /** Null means unread - see NotificationRepository's index and query shapes, both built around this column being null/non-null rather than a separate boolean flag. */
    @Column(name = "read_at")
    private Instant readAt;

    public Notification(UUID organizationId, UUID recipientUserId, Type type, String title) {
        this.organizationId = organizationId;
        this.recipientUserId = recipientUserId;
        this.type = type;
        this.title = title;
    }

    public boolean isRead() {
        return readAt != null;
    }
}
