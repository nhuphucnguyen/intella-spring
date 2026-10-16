package dev.phucngu.intellaspring.initializr;

import com.intellij.util.io.HttpRequests;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.StringJoiner;

/**
 * Minimal client for the public Initializr service (start.spring.io): metadata for the
 * wizard and a generated project zip. The service contract is public and stable
 * ({@code https://start.spring.io}, JSON metadata + {@code /starter.zip}).
 */
public final class InitializrClient {

    public static final String SERVICE_URL = "https://start.spring.io";
    private static final int TIMEOUT_MS = 20_000;
    private static final String USER_AGENT = "intella-spring";

    /** The wire seam: production goes through the IDE's proxy-aware HttpRequests, tests plug a fake. */
    interface Transport {
        byte @NotNull [] get(@NotNull String url, @NotNull String accept) throws IOException;
    }

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

    private final Transport transport;

    public InitializrClient() {
        this(InitializrClient::httpGet);
    }

    InitializrClient(@NotNull Transport transport) {
        this.transport = transport;
    }

    private static byte @NotNull [] httpGet(@NotNull String url, @NotNull String accept) throws IOException {
        return HttpRequests.request(url)
                .accept(accept)
                .userAgent(USER_AGENT)
                .connectTimeout(TIMEOUT_MS)
                .readTimeout(TIMEOUT_MS)
                .readBytes(null);
    }

    public @NotNull InitializrModels.Metadata fetchMetadata() throws IOException, InterruptedException {
        // Initializr client protocol: negotiate a stable metadata version the way
        // IDEA's own wizard does — plain application/json yields a representation
        // whose default bootVersion id ("4.1.1.RELEASE") the generator rejects
        byte[] body = get(SERVICE_URL,
                "application/vnd.initializr.v2.3+json,application/vnd.initializr.v2.1+json;q=0.9");
        return InitializrModels.parse(new String(body, StandardCharsets.UTF_8));
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

    private byte @NotNull [] fetchZip(@NotNull String query) throws IOException {
        return get(SERVICE_URL + "/starter.zip?" + query, "application/zip");
    }

    private byte @NotNull [] get(@NotNull String url, @NotNull String accept) throws IOException {
        try {
            return transport.get(url, accept);
        } catch (HttpRequests.HttpStatusException e) {
            throw new IOException("start.spring.io answered " + e.getStatusCode()
                    + (url.contains("/starter.zip") ? " — check the Boot version" : ""), e);
        }
    }
}
