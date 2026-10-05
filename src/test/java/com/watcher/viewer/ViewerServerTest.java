package com.watcher.viewer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class ViewerServerTest {

    private ViewerServer server;
    private S3Client mockS3;
    private HttpClient httpClient;

    @BeforeEach
    public void setUp() throws IOException {
        mockS3 = mock(S3Client.class);
        server = new ViewerServer(mockS3, "test-bucket", 0); // 0 binds to free ephemeral port
        server.start();
        httpClient = HttpClient.newHttpClient();
    }

    @AfterEach
    public void tearDown() {
        if (server != null) {
            server.stop();
        }
    }

    @Test
    public void testServeHtmlIndex() throws Exception {
        int port = server.getPort();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/"))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("Watcher"), "HTML should contain Watcher title");
        assertTrue(response.headers().firstValue("Content-Type").orElse("").contains("text/html"));
    }

    @Test
    public void testGetDatesApi() throws Exception {
        CommonPrefix d1 = CommonPrefix.builder().prefix("2026-09-28/").build();
        CommonPrefix d2 = CommonPrefix.builder().prefix("2026-09-27/").build();

        when(mockS3.listObjectsV2(any(ListObjectsV2Request.class)))
                .thenReturn(ListObjectsV2Response.builder()
                        .commonPrefixes(List.of(d1, d2))
                        .build());

        int port = server.getPort();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/dates"))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("2026-09-28"));
        assertTrue(response.body().contains("2026-09-27"));
    }

    @Test
    public void testBatchDeleteApi() throws Exception {
        when(mockS3.deleteObjects(any(DeleteObjectsRequest.class)))
                .thenReturn(DeleteObjectsResponse.builder()
                        .deleted(List.of(
                                DeletedObject.builder().key("key1.jpg").build(),
                                DeletedObject.builder().key("key2.jpg").build()
                        ))
                        .build());

        int port = server.getPort();
        String jsonPayload = "{\"keys\":[\"key1.jpg\",\"key2.jpg\"]}";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/delete"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("\"success\":true"));
        assertTrue(response.body().contains("\"deletedCount\":2"));
        verify(mockS3, times(1)).deleteObjects(any(DeleteObjectsRequest.class));
    }
}
