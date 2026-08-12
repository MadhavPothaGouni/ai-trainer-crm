package com.aitrainercrm.platform.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Backs {@code LocalFileStorageService} - see its javadoc for why local
 * disk is the default (and a documented limitation, not a production
 * plan) rather than an S3-backed implementation.
 */
@ConfigurationProperties(prefix = "crm.storage")
public record StorageProperties(String uploadDir) {

    public StorageProperties {
        if (uploadDir == null || uploadDir.isBlank()) {
            uploadDir = "./uploads";
        }
    }
}
