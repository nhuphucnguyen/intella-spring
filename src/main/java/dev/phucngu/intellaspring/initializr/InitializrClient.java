package dev.phucngu.intellaspring.initializr;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.StringJoiner;

/**
 * Minimal client for the public Initializr service (start.spring.io): metadata for the
 * wizard and a generated project zip. The service contract is public and stable
 * ({@code https://start.spring.io}, JSON metadata + {@code /starter.zip}).
 */
public final class InitializrClient {

    public static final String SERVICE_URL = "https://start.spring.io";
    private static final Duration TIMEOUT = Duration.ofSeconds(20);

    /** Everything the service needs to stamp out a project. */
    public record GenerateRequest(@NotNull String type, @NotNull String bootVersion,
                                  @NotNull String groupId, @NotNull String artifactId,
                                  @NotNull String name, @NotNull String description,
                                  @NotNull String packageName, @NotNull String packaging,
                                  @NotNull String javaVersion, @NotNull List<String> dependencies) {

        /** The query string for {@code /starter.zip} — also asserted in tests. */
        public @NotNull String query() {
            StringJoiner joiner = new StringJoiner("&");
            joiner.add("type=" + encode(type));
            if (!bootVersion.isBlank()) {
                joiner.add("bootVersion=" + encode(bootVersion));
            }
            joiner.add("groupId=" + encode(groupId));
            joiner.add("artifactId=" + encode(artifactId));
            joiner.add("name=" + encode(name));
            joiner.add("description=" + encode(description));
            joiner.add("packageName=" + encode(packageName));
            joiner.add("packaging=" + encode(packaging));
            joiner.add("javaVersion=" + encode(javaVersion));
            if (!dependencies.isEmpty()) {
                joiner.add("dependencies=" + encode(String.join(",", dependencies)));
            }
            return joiner.toString();
        }

        /** A copy that lets the service pick its default Boot version (its explicit default id is known to 500). */
        public @NotNull GenerateRequest withoutBootVersion() {
            return new GenerateRequest(type, "", groupId, artifactId, name, description,
                    packageName, packaging, javaVersion, dependencies);
        }

        private static @NotNull String encode(@NotNull String value) {
            return URLEncoder.encode(value, StandardCharsets.UTF_8);
        }
    }

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();

    public @NotNull InitializrModels.Metadata fetchMetadata() throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(SERVICE_URL))
                .timeout(TIMEOUT)
                // Initializr client protocol: negotiate a stable metadata version the way
                // IDEA's own wizard does — plain application/json yields a representation
                // whose default bootVersion id ("4.1.1.RELEASE") the generator rejects
                .header("Accept", "application/vnd.initializr.v2.3+json,application/vnd.initializr.v2.1+json;q=0.9")
                .header("User-Agent", "intella-spring")
                .GET()
                .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("start.spring.io answered " + response.statusCode());
        }
        return InitializrModels.parse(response.body());
    }

    /** The generated project as a zip; a Boot-version rejection (HTTP 500) is retried on the service default. */
    public byte @NotNull [] generate(@NotNull GenerateRequest request) throws IOException, InterruptedException {
        try {
            return fetchZip(request.query());
        } catch (IOException e) {
            String message = String.valueOf(e.getMessage());
            if (!request.bootVersion().isBlank() && message.contains("500")) {
                return fetchZip(request.withoutBootVersion().query());
            }
            throw e;
        }
    }

    private byte @NotNull [] fetchZip(@NotNull String query) throws IOException, InterruptedException {
        HttpRequest httpRequest = HttpRequest.newBuilder(URI.create(SERVICE_URL + "/starter.zip?" + query))
                .timeout(TIMEOUT)
                .header("Accept", "application/zip")
                .header("User-Agent", "intella-spring")
                .GET()
                .build();
        HttpResponse<byte[]> response = http.send(httpRequest, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() != 200) {
            throw new IOException("start.spring.io answered " + response.statusCode()
                    + " — check the Boot version");
        }
        return response.body();
    }
}
