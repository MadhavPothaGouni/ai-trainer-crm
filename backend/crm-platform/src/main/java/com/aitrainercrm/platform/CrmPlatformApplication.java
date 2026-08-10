package com.aitrainercrm.platform;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Entry point for the AI-Trainer CRM backend.
 *
 * <p>This is a modular monolith: every business module (auth, users,
 * organizations, roles, leads, opportunities, ...) lives as a package under
 * {@code com.aitrainercrm.platform}, not as a separate deployable service.
 * Modules communicate through Spring-managed service interfaces and,
 * for cross-module side effects, Spring application events - not direct
 * repository access into another module's tables. That boundary is a
 * convention (Java doesn't enforce package-private modules across
 * top-level packages) but it's the one this whole codebase follows, and
 * it's what keeps a later extraction into real services realistic if the
 * platform ever needs it.
 */
@SpringBootApplication
@EnableJpaAuditing
@EnableCaching
@EnableAsync
@EnableScheduling
@ConfigurationPropertiesScan
public class CrmPlatformApplication {

    public static void main(String[] args) {
        SpringApplication.run(CrmPlatformApplication.class, args);
    }
}
