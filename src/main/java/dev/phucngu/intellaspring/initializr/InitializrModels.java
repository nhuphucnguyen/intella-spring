package dev.phucngu.intellaspring.initializr;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Models of the public Initializr metadata (start.spring.io, {@code Accept: application/json})
 * plus a curated offline fallback so the wizard works without network.
 */
public final class InitializrModels {

    /** A build system the service can generate. */
    public record BuildType(@NotNull String id, @NotNull String name) {
    }

    /** One selectable starter dependency. */
    public record Dependency(@NotNull String id, @NotNull String name, @NotNull String description) {
    }

    public record DependencyGroup(@NotNull String name, @NotNull List<Dependency> dependencies) {
    }

    public record Metadata(@NotNull List<BuildType> buildTypes, @NotNull String defaultBootVersion,
                           @NotNull List<String> bootVersions, @NotNull List<String> javaVersions,
                           @NotNull List<DependencyGroup> groups) {
    }

    private InitializrModels() {
    }

    public static @NotNull Metadata parse(@NotNull String json) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        List<BuildType> types = new ArrayList<>();
        for (JsonElement e : values(root, "type")) {
            types.add(new BuildType(e.getAsJsonObject().get("id").getAsString(),
                    e.getAsJsonObject().get("name").getAsString()));
        }
        String defaultBoot = root.getAsJsonObject("bootVersion").get("default").getAsString();
        List<String> boots = new ArrayList<>();
        for (JsonElement e : values(root, "bootVersion")) {
            boots.add(e.getAsJsonObject().get("id").getAsString());
        }
        List<String> javas = new ArrayList<>();
        for (JsonElement e : values(root, "javaVersion")) {
            javas.add(e.getAsJsonObject().get("id").getAsString());
        }
        List<DependencyGroup> groups = new ArrayList<>();
        for (JsonElement groupElement : root.getAsJsonObject("dependencies").getAsJsonArray("values")) {
            JsonObject group = groupElement.getAsJsonObject();
            List<Dependency> dependencies = new ArrayList<>();
            for (JsonElement depElement : group.getAsJsonArray("values")) {
                JsonObject dep = depElement.getAsJsonObject();
                dependencies.add(new Dependency(
                        dep.get("id").getAsString(),
                        dep.has("name") ? dep.get("name").getAsString() : dep.get("id").getAsString(),
                        dep.has("description") ? dep.get("description").getAsString() : ""));
            }
            groups.add(new DependencyGroup(group.get("name").getAsString(), dependencies));
        }
        if (types.isEmpty() || boots.isEmpty() || groups.isEmpty()) {
            return fallback();
        }
        return new Metadata(List.copyOf(types), defaultBoot, List.copyOf(boots),
                javas.isEmpty() ? List.of("21", "17") : List.copyOf(javas), List.copyOf(groups));
    }

    private static @NotNull JsonArray values(@NotNull JsonObject root, @NotNull String field) {
        JsonObject single = root.getAsJsonObject(field);
        return single == null || single.get("values") == null ? new JsonArray() : single.getAsJsonArray("values");
    }

    /** A small, useful offline set — the wizard works even when start.spring.io is unreachable. */
    public static @NotNull Metadata fallback() {
        List<DependencyGroup> groups = List.of(
                new DependencyGroup("Web", List.of(
                        dep("web", "Spring Web", "Build web, including RESTful, applications using Spring MVC"),
                        dep("rest-repositories", "Spring Data REST", "Exposing Spring Data repositories over REST"),
                        dep("session", "Spring Session", "Clustered session state"),
                        dep("websocket", "WebSocket", "WebSocket support with SockJS and STOMP"))),
                new DependencyGroup("Data", List.of(
                        dep("data-jpa", "Spring Data JPA", "JPA with Spring Data JPA and Hibernate"),
                        dep("jdbc", "JDBC API", "JDBC databases with the JDBC API"),
                        dep("data-redis", "Spring Data Redis", "Redis key-value data store access"),
                        dep("h2", "H2 Database", "H2 in-memory database"),
                        dep("mysql", "MySQL Driver", "MySQL JDBC driver"),
                        dep("postgresql", "PostgreSQL Driver", "PostgreSQL JDBC driver"))),
                new DependencyGroup("Core", List.of(
                        dep("devtools", "Spring Boot DevTools", "Fast application restarts and live reload"),
                        dep("lombok", "Lombok", "Java annotation library reducing boilerplate"),
                        dep("configuration-processor", "Configuration Annotation Processor",
                                "Generate metadata for custom configuration keys"),
                        dep("validation", "Validation", "Bean validation with Hibernate Validator"),
                        dep("actuator", "Spring Boot Actuator", "Production-ready endpoints for monitoring"),
                        dep("aop", "Aspect Oriented Programming", "Cross-cutting concerns with Spring AOP and AspectJ"),
                        dep("cache", "Caching", "Spring cache abstraction"),
                        dep("mail", "Java Mail Sender", "Email sending with Java Mail and Spring"),
                        dep("security", "Spring Security", "Authentication and authorization framework"),
                        dep("oauth2-client", "OAuth2 Client", "OAuth2 / OpenID Connect login"))));
        return new Metadata(
                List.of(new BuildType("maven-project", "Maven - pom.xml"),
                        new BuildType("gradle-project", "Gradle - Groovy DSL"),
                        new BuildType("gradle-project-kotlin", "Gradle - Kotlin DSL")),
                "4.1.1", List.of("4.1.1", "4.0.8", "3.5.6"), List.of("25", "21", "17"),
                groups);
    }

    private static @NotNull Dependency dep(@NotNull String id, @NotNull String name, @NotNull String description) {
        return new Dependency(id, name, description);
    }
}
