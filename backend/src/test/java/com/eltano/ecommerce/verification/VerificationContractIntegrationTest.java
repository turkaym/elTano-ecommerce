package com.eltano.ecommerce.verification;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class VerificationContractIntegrationTest {

    @Test
    void readmeDefinesCanonicalFullSuiteVerificationContract() throws IOException {
        String readme = Files.readString(Path.of("..", "README.md"));

        assertTrue(readme.contains("## Verificacion completa (contrato canonico)"));
        assertTrue(readme.contains("mvn -B clean verify"));
        assertTrue(readme.contains("npm ci"));
        assertTrue(readme.contains("npm test -- --watch=false"));
        assertTrue(readme.contains("npm run lint"));
        assertTrue(readme.contains("npm run build"));
    }

    @Test
    void frontendPackageDefinesCiUsableCanonicalScripts() throws IOException {
        String packageJson = Files.readString(Path.of("..", "frontend", "package.json"));

        assertTrue(packageJson.contains("\"test\": \"vitest run\""));
        assertTrue(packageJson.contains("\"lint\": \"eslint .\""));
        assertTrue(packageJson.contains("\"build\": \"tsc -b && vite build\""));
    }

    @Test
    void backendPomSupportsVerifyLifecycleWithoutTestSkipping() throws IOException {
        String pom = Files.readString(Path.of("pom.xml"));

        assertTrue(pom.contains("<artifactId>spring-boot-maven-plugin</artifactId>"));
        assertTrue(!pom.contains("<skipTests>true</skipTests>"));
        assertTrue(!pom.contains("<maven.test.skip>true</maven.test.skip>"));
    }
}
