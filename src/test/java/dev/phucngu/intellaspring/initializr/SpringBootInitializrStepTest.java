package dev.phucngu.intellaspring.initializr;

import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import org.jetbrains.annotations.NotNull;

/**
 * Static helpers of the native New Project wizard step: artifact sanitizing and package
 * defaulting (name → group.name).
 */
public class SpringBootInitializrStepTest extends BasePlatformTestCase {

    public void testSanitizeReplacesUnsafeCharacters() {
        assertEquals("My-App", SpringBootInitializrStep.sanitize("My App"));
        assertEquals("a..b", SpringBootInitializrStep.sanitize("a..b"));
        assertEquals("x-1-2-3", SpringBootInitializrStep.sanitize("x 1/2\\3"));
    }

    public void testDefaultPackageJoinsGroupAndArtifact() {
        assertEquals("com.example.my-app", SpringBootInitializrStep.defaultPackage("com.example", "My App"));
        assertEquals("solo", SpringBootInitializrStep.defaultPackage("", "Solo"));
        assertEquals("org.acme.orders", SpringBootInitializrStep.defaultPackage("Org.Acme", "Orders"));
    }
    public void testFallbackMetadataFeedsTheForm() {
        // the fallback must at least contain the canonical maven type and web starter
        InitializrModels.Metadata fallback = InitializrModels.fallback();
        assertTrue(fallback.buildTypes().stream().anyMatch(t -> "maven-project".equals(t.id())));
        assertTrue(fallback.groups().stream().flatMap(g -> g.dependencies().stream())
                .anyMatch(dep -> "web".equals(dep.id())));
        assertNotNull(fallback.defaultBootVersion());
    }

    public void testRequestQueryCarriesSelections() {
        InitializrClient.GenerateRequest request = new InitializrClient.GenerateRequest(
                "maven-project", "4.1.1", "com.example", "agenttest", "agenttest",
                "demo", "com.example.agenttest", "jar", "25", java.util.List.of("web", "data-jpa"));
        String query = request.query();
        assertTrue(query, query.contains("type=maven-project"));
        assertTrue(query, query.contains("bootVersion=4.1.1"));
        assertTrue(query, query.contains("dependencies=" + encode("web,data-jpa")));
        assertTrue(query, query.contains("packaging=jar"));
        assertTrue(query, query.contains("javaVersion=25"));
    }

    /** Regression: start.spring.io 500s on its own default Boot version sent explicitly; the retry drops the param. */
    public void testBlankBootVersionIsOmittedFromQueryAndRetryDropsIt() {
        InitializrClient.GenerateRequest request = new InitializrClient.GenerateRequest(
                "gradle-project", "4.1.1", "com.example", "demo", "demo",
                "", "com.example.demo", "jar", "25", java.util.List.of());
        String retryQuery = request.withoutBootVersion().query();
        assertFalse(retryQuery, retryQuery.contains("bootVersion"));
        assertTrue(retryQuery, retryQuery.contains("type=gradle-project"));
        assertTrue(retryQuery, retryQuery.contains("artifactId=demo"));
        // a blank boot version never makes it into the query either
        InitializrClient.GenerateRequest blank = request.withoutBootVersion();
        assertFalse(blank.query(), blank.query().contains("bootVersion"));
    }

    private static @NotNull String encode(@NotNull String raw) {
        return java.net.URLEncoder.encode(raw, java.nio.charset.StandardCharsets.UTF_8);
    }
}
