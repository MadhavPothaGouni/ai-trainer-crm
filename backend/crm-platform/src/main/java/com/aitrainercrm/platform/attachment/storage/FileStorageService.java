package com.aitrainercrm.platform.attachment.storage;

import java.util.UUID;

/**
 * Where an {@code Attachment}'s actual bytes live, decoupled from the
 * metadata row in Postgres - the same "interface + swappable
 * implementation" shape {@code notification.email.EmailService} already
 * established for transactional email. {@code LocalFileStorageService} is
 * the only implementation today (writes under a configurable directory on
 * local disk); a production deployment would add an S3/GCS-backed
 * implementation behind this same interface with no change to
 * {@code AttachmentService} or any caller.
 */
public interface FileStorageService {

    /** Persists the given bytes under a fresh key scoped to the organization and returns that key - callers store it as {@code Attachment#storageKey} and pass it back into {@link #retrieve}/{@link #delete} later. */
    String store(UUID organizationId, String originalFileName, byte[] content);

    byte[] retrieve(String storageKey);

    void delete(String storageKey);
}
