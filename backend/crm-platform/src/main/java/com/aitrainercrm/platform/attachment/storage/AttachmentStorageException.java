package com.aitrainercrm.platform.attachment.storage;

/**
 * An infrastructure failure (disk full, permissions, I/O error), not a business-rule
 * violation - deliberately not a {@code BusinessException} subclass, so it falls through
 * to GlobalExceptionHandler's generic 500 handler like any other unexpected failure,
 * rather than being reported to the client as something they can fix.
 */
public class AttachmentStorageException extends RuntimeException {

    public AttachmentStorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
