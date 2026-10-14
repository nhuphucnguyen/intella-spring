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
            joiner.add("bootVersion=" + encode(bootVersion));
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

        private static @NotNull String encode(@NotNull String value) {
            return URLEncoder.encode(value, StandardCharsets.UTF_8);
        }
    }

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();

    public @NotNull InitializrModels.Metadata fetchMetadata() throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(SERVICE_URL))
                .timeout(TIMEOUT)
                .header("Accept", "application/json")
                .header("User-Agent", "intella-spring")
                .GET()
                .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("start.spring.io answered " + response.statusCode());
        }
        return InitializrModels.parse(response.body());
    }

    /** The generated project as a zip. */
    public byte @NotNull [] generate(@NotNull GenerateRequest request) throws IOException, InterruptedException {
        HttpRequest httpRequest = HttpRequest.newBuilder(URI.create(SERVICE_URL + "/starter.zip?" + request.query()))
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
