package com.aitrainercrm.platform.support;

import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Base class for every test that needs a real database. A single
 * PostgreSQL container is started once per JVM (the {@code static} field
 * plus Testcontainers' own reuse-within-a-run behavior) and Flyway runs
 * the real V1/V2 migrations against it on each Spring context start, so
 * these tests exercise the actual schema - not an H2 approximation of it -
 * which matters here given the JSON/enum/partial-index details Postgres
 * and H2 don't agree on.
 *
 * <p>Subclasses just add {@code @SpringBootTest} (or a slice annotation)
 * and extend this; the datasource is wired automatically via
 * {@link DynamicPropertySource}.
 */
@Testcontainers
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("crm_test")
            .withUsername("crm_test")
            .withPassword("crm_test");

    @DynamicPropertySource
    static void registerDatasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }
}
