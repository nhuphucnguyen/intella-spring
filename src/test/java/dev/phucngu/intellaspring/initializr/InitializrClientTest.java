package dev.phucngu.intellaspring.initializr;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The client's request protocol, driven through the transport seam — no network. */
class InitializrClientTest {

    private static InitializrClient.GenerateRequest request(String bootVersion) {
        return new InitializrClient.GenerateRequest("maven-project", bootVersion, "com.example", "demo", "demo",
                "Demo", "com.example.demo", "jar", "21", List.of("web"));
    }

    @Test
    void metadataRequestNegotiatesVersionedAcceptHeader() throws Exception {
        List<String> accepts = new ArrayList<>();
        InitializrClient client = new InitializrClient((url, accept) -> {
            assertEquals(InitializrClient.SERVICE_URL, url);
            accepts.add(accept);
            return ("{\"bootVersion\": {\"default\": \"3.4.2\", \"values\": []}, \"javaVersion\": {\"values\": []},"
                    + " \"type\": {\"values\": []}, \"dependencies\": {\"values\": []}}").getBytes(StandardCharsets.UTF_8);
        });
        client.fetchMetadata();
        assertTrue(accepts.get(0).startsWith("application/vnd.initializr.v2.3+json"), accepts.toString());
    }

    @Test
    void generateRetriesOnServiceDefaultWhenBootVersionIsRejected() throws Exception {
        List<String> urls = new ArrayList<>();
        InitializrClient client = new InitializrClient((url, accept) -> {
            urls.add(url);
            if (url.contains("bootVersion=")) {
                throw new IOException("start.spring.io answered 500");
            }
            return new byte[]{1, 2, 3};
        });
        assertArrayEquals(new byte[]{1, 2, 3}, client.generate(request("4.1.1.RELEASE")));
        assertEquals(2, urls.size());
        assertTrue(urls.get(0).contains("bootVersion="));
        assertTrue(urls.get(1).startsWith(InitializrClient.SERVICE_URL + "/starter.zip?"));
    }

    @Test
    void failureWithoutBootVersionIsNotRetried() {
        List<String> urls = new ArrayList<>();
        InitializrClient client = new InitializrClient((url, accept) -> {
            urls.add(url);
            throw new IOException("offline");
        });
        assertThrows(IOException.class, () -> client.generate(request("")));
        assertEquals(1, urls.size());
    }
}
