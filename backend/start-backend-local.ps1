$env:DB_URL = "jdbc:postgresql://localhost:5432/eltano_db"
$env:DB_USER = "postgres"
$env:DB_PASSWORD = "change-me-local-db-password"
$env:ADMIN_BASIC_USER = "admin-user"
$env:ADMIN_BASIC_PASS = "change-me-admin-pass"
$env:STOREFRONT_BASIC_USER = "storefront-user"
$env:STOREFRONT_BASIC_PASS = "change-me-storefront-pass"
$env:EXPIRED_ADMIN_BASIC_USER = "expired-admin"
$env:EXPIRED_ADMIN_BASIC_PASS = "change-me-expired-pass"

.\mvnw.cmd spring-boot:run
