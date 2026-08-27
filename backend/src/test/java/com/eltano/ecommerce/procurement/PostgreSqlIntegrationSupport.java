package com.eltano.ecommerce.procurement;

import org.flywaydb.core.Flyway;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

public abstract class PostgreSqlIntegrationSupport {
    static {
        boolean windows = System.getProperty("os.name", "").toLowerCase().contains("win");
        boolean dockerHostMissing = System.getenv("DOCKER_HOST") == null && System.getProperty("docker.host") == null;
        if (windows && dockerHostMissing) System.setProperty("docker.host", "npipe:////./pipe/dockerDesktopLinuxEngine");
    }

    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
    private static boolean migrated;

    @DynamicPropertySource
    static synchronized void postgreSqlProperties(DynamicPropertyRegistry registry) {
        if (!POSTGRES.isRunning()) POSTGRES.start();
        if (!migrated) {
            Flyway.configure()
                    .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                    .locations("classpath:db/migration")
                    .load()
                    .migrate();
            migrated = true;
        }
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }
}
