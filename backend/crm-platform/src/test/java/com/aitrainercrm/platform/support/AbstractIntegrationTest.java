package com.aitrainercrm.platform.support;

import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
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

    /**
     * Polls {@code action} (typically an HTTP GET through {@code MockMvc} plus a bit of JSON
     * navigation) every 50ms until {@code condition} is satisfied or 3 seconds pass, then returns
     * whatever the last poll produced. Every {@code @Async @EventListener} in this codebase
     * (duplicate detection, territory assignment, the commission engine, ...) is fired-and-forgotten
     * from an HTTP request thread, so a test asserting on its side effect has no signal for "has it
     * run yet" other than the effect itself - a fixed {@code Thread.sleep} guesses at that, and
     * guesses wrong under CI load. Polling for the actual condition removes the guess: it returns as
     * soon as the listener's effect is visible, and only spends the full 3 seconds when something is
     * genuinely broken - at which point the caller's own assertion on the returned (still-not-ready)
     * value fails with a real diagnostic instead of the poll silently swallowing a bug.
     */
    protected static <T> T awaitAsync(Callable<T> action, Predicate<T> condition) throws Exception {
        long deadlineNanos = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        T result = action.call();
        while (!condition.test(result) && System.nanoTime() < deadlineNanos) {
            Thread.sleep(50);
            result = action.call();
        }
        return result;
    }
}
