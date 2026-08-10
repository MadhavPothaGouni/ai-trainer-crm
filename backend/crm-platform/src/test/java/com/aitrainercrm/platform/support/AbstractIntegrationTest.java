package com.aitrainercrm.platform.support;

import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Base class for every test that needs a real database. Flyway runs the
 * real V1/V2 migrations against it on each Spring context start, so these
 * tests exercise the actual schema - not an H2 approximation of it - which
 * matters here given the JSON/enum/partial-index details Postgres and H2
 * don't agree on.
 *
 * <p>This deliberately does NOT use {@code @Testcontainers}/{@code @Container}.
 * Those annotations stop the container in {@code afterAll} for every test
 * class that touches it - fine for a container that's private to one class,
 * but this one is a {@code static} field shared across every integration
 * test class in the suite. With multiple test classes, class A's
 * {@code afterAll} would stop the (shared) container, and starting it back
 * up for class B gets Testcontainers a brand-new Docker container with a
 * new randomly-mapped host port. Spring's {@code ApplicationContext} cache
 * meanwhile reuses class A's already-built context - including a connection
 * pool still wired to the old, now-dead port - so class B fails with
 * "Connection refused" against a port nothing is listening on anymore.
 *
 * <p>Instead this follows Testcontainers' documented "singleton container"
 * pattern: start it once, eagerly, in a static initializer, and never stop
 * it ourselves. It lives for the lifetime of the test JVM and is cleaned up
 * by Testcontainers' Ryuk reaper when the run ends.
 *
 * <p>Subclasses just add {@code @SpringBootTest} (or a slice annotation)
 * and extend this; the datasource is wired automatically via
 * {@link DynamicPropertySource}.
 */
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("crm_test")
            .withUsername("crm_test")
            .withPassword("crm_test");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void registerDatasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }
}
