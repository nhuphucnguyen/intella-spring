package dev.phucngu.intellaspring;

import com.intellij.testFramework.UsefulTestCase;
import dev.phucngu.intellaspring.initializr.InitializrClient.GenerateRequest;
import dev.phucngu.intellaspring.initializr.InitializrModels;
import dev.phucngu.intellaspring.initializr.Unzips;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * The Initializr wizard's offline parts: metadata parsing, request composition, and safe
 * extraction. Network calls are exercised live in the IDE, not here.
 */
class InitializrTest {

    private static final String SAMPLE_METADATA = """
            {
              "bootVersion": {"default": "3.4.2", "values": [
                {"id": "3.4.2", "name": "3.4.2"}, {"id": "3.3.8", "name": "3.3.8"}]},
              "javaVersion": {"values": [{"id": "23"}, {"id": "21"}, {"id": "17"}]},
              "type": {"values": [
                {"id": "maven-project", "name": "Maven"},
                {"id": "gradle-project", "name": "Gradle"}]},
              "dependencies": {"values": [
                {"name": "Web", "values": [
                  {"id": "web", "name": "Spring Web", "description": "Web apps"},
                  {"id": "rest-repositories", "name": "Spring Data REST"}]},
                {"name": "Data", "values": [
                  {"id": "data-jpa", "name": "Spring Data JPA", "description": "JPA + Hibernate"}]}]}
            }
            """;

    @Test
    void metadataParsingReadsVersionsTypesAndGroups() {
        InitializrModels.Metadata metadata = InitializrModels.parse(SAMPLE_METADATA);
        UsefulTestCase.assertOrderedEquals(metadata.bootVersions(), "3.4.2", "3.3.8");
        UsefulTestCase.assertOrderedEquals(metadata.javaVersions(), "23", "21", "17");
        UsefulTestCase.assertOrderedEquals(metadata.buildTypes().stream().map(InitializrModels.BuildType::id).toList(),
                "maven-project", "gradle-project");
        org.junit.jupiter.api.Assertions.assertEquals("3.4.2", metadata.defaultBootVersion());
        org.junit.jupiter.api.Assertions.assertEquals(2, metadata.groups().size());
        org.junit.jupiter.api.Assertions.assertEquals("Spring Web", metadata.groups().get(0).dependencies().get(0).name());
        // a dependency without description gets "" rather than a crash
        org.junit.jupiter.api.Assertions.assertEquals("",
                metadata.groups().get(0).dependencies().get(1).description());
    }

    @Test
    void fallbackMetadataIsUsable() {
        InitializrModels.Metadata fallback = InitializrModels.fallback();
        org.junit.jupiter.api.Assertions.assertFalse(fallback.bootVersions().isEmpty());
        org.junit.jupiter.api.Assertions.assertFalse(fallback.groups().isEmpty());
        org.junit.jupiter.api.Assertions.assertTrue(fallback.groups().stream()
                .flatMap(g -> g.dependencies().stream())
                .anyMatch(d -> d.id().equals("web")));
    }

    @Test
    void requestQueryCarriesEveryParameter() {
        GenerateRequest request = new GenerateRequest("gradle-project", "3.4.2",
                "com.acme", "orders", "orders", "Order service",
                "com.acme.orders", "jar", "21", List.of("web", "data-jpa"));
        org.junit.jupiter.api.Assertions.assertEquals(
                "type=gradle-project&bootVersion=3.4.2&groupId=com.acme&artifactId=orders"
                        + "&name=orders&description=Order+service&packageName=com.acme.orders"
                        + "&packaging=jar&javaVersion=21&dependencies=web%2Cdata-jpa",
                request.query());
        org.junit.jupiter.api.Assertions.assertFalse(
                new GenerateRequest("maven-project", "3.4.2", "g", "a", "a", "d", "g.a", "jar", "21", List.of())
                        .query().contains("dependencies="));
    }

    @Test
    void unzipExtractsAndRefusesZipSlip(@TempDir Path dir) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            zip.putNextEntry(new ZipEntry("pom.xml"));
            zip.write("<project/>".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("src/main/java/App.java"));
            zip.write("class App{}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("../evil.txt"));
            zip.write("no".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        Path target = dir.resolve("orders");
        Unzips.unzip(out.toByteArray(), target);
        org.junit.jupiter.api.Assertions.assertEquals("<project/>", Files.readString(target.resolve("pom.xml")));
        org.junit.jupiter.api.Assertions.assertTrue(Files.exists(target.resolve("src/main/java/App.java")));
        org.junit.jupiter.api.Assertions.assertFalse(Files.exists(dir.resolve("evil.txt")));
    }
}
