package com.watcher.viewer;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * Built-in lightweight HTTP server to browse, filter by date range, and manage captures stored on Cloudflare R2 / S3.
 */
public class ViewerServer {
    private static final Logger logger = LogManager.getLogger(ViewerServer.class);

    private final S3Client s3Client;
    private final String bucketName;
    private final int port;
    private int actualPort;
    private HttpServer server;
    private final Gson gson = new Gson();

    public ViewerServer(S3Client s3Client, String bucketName, int port) {
        this.s3Client = Objects.requireNonNull(s3Client, "s3Client cannot be null");
        this.bucketName = Objects.requireNonNull(bucketName, "bucketName cannot be null");
        this.port = port >= 0 ? port : 8080;
    }

    public synchronized void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(port), 0);
        this.actualPort = server.getAddress().getPort();
        server.setExecutor(Executors.newFixedThreadPool(8));

        server.createContext("/", new StaticHtmlHandler());
        server.createContext("/api/dates", new DatesHandler());
        server.createContext("/api/machines", new MachinesHandler());
        server.createContext("/api/screenshots", new ScreenshotsHandler());
        server.createContext("/api/image", new ImageProxyHandler());
        server.createContext("/api/delete", new DeleteHandler());

        server.start();
        logger.info("Watcher Web Viewer started on http://localhost:{}", getPort());
    }

    public synchronized void stop() {
        if (server != null) {
            server.stop(1);
            logger.info("Watcher Web Viewer stopped.");
        }
    }

    public int getPort() {
        return actualPort > 0 ? actualPort : port;
    }

    /**
     * Helper method to list all available date prefixes from S3.
     */
    private List<String> listAvailableDates() {
        List<String> dates = new ArrayList<>();
        String continuationToken = null;
        do {
            ListObjectsV2Request.Builder reqB = ListObjectsV2Request.builder()
                    .bucket(bucketName)
                    .delimiter("/");
            if (continuationToken != null) {
                reqB.continuationToken(continuationToken);
            }
            ListObjectsV2Response res = s3Client.listObjectsV2(reqB.build());
            if (res.commonPrefixes() != null) {
                for (CommonPrefix cp : res.commonPrefixes()) {
                    String prefix = cp.prefix();
                    if (prefix.endsWith("/")) {
                        prefix = prefix.substring(0, prefix.length() - 1);
                    }
                    if (prefix.matches("\\d{4}-\\d{2}-\\d{2}")) {
                        dates.add(prefix);
                    }
                }
            }
            continuationToken = res.nextContinuationToken();
        } while (continuationToken != null);

        dates.sort(Collections.reverseOrder());
        return dates;
    }

    /**
     * Serves the single-page application viewer.html from classpath resources.
     */
    private class StaticHtmlHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "text/plain", "Method Not Allowed".getBytes(StandardCharsets.UTF_8));
                return;
            }

            try (InputStream is = getClass().getResourceAsStream("/viewer.html")) {
                if (is == null) {
                    byte[] notFound = "Error: viewer.html resource not found in classpath".getBytes(StandardCharsets.UTF_8);
                    sendResponse(exchange, 404, "text/plain", notFound);
                    return;
                }
                byte[] htmlBytes = is.readAllBytes();
                sendResponse(exchange, 200, "text/html; charset=UTF-8", htmlBytes);
            }
        }
    }

    /**
     * Returns a list of dates (top-level folders) in descending order.
     */
    private class DatesHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "text/plain", "Method Not Allowed".getBytes(StandardCharsets.UTF_8));
                return;
            }

            try {
                List<String> dates = listAvailableDates();
                byte[] json = gson.toJson(dates).getBytes(StandardCharsets.UTF_8);
                sendResponse(exchange, 200, "application/json; charset=UTF-8", json);
            } catch (Exception e) {
                logger.error("Error listing dates from S3", e);
                sendJsonError(exchange, 500, e.getMessage());
            }
        }
    }

    /**
     * Returns a list of machines.
     * Query params: startDate, endDate (or date)
     */
    private class MachinesHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "text/plain", "Method Not Allowed".getBytes(StandardCharsets.UTF_8));
                return;
            }

            Map<String, String> query = parseQueryParams(exchange.getRequestURI().getRawQuery());
            String singleDate = query.get("date");
            String startDate = query.get("startDate");
            String endDate = query.get("endDate");

            if (startDate == null && singleDate != null) startDate = singleDate;
            if (endDate == null && singleDate != null) endDate = singleDate;

            try {
                List<String> allDates = listAvailableDates();
                List<String> targetDates = filterDatesInRange(allDates, startDate, endDate);

                Set<String> uniqueMachines = new HashSet<>();
                List<String> datesToScan = (targetDates != null && !targetDates.isEmpty()) ? targetDates : allDates;

                for (String d : datesToScan) {
                    String prefix = d + "/";
                    String continuationToken = null;
                    do {
                        ListObjectsV2Request.Builder reqB = ListObjectsV2Request.builder()
                                .bucket(bucketName)
                                .prefix(prefix)
                                .delimiter("/");
                        if (continuationToken != null) {
                            reqB.continuationToken(continuationToken);
                        }
                        ListObjectsV2Response res = s3Client.listObjectsV2(reqB.build());
                        if (res.commonPrefixes() != null) {
                            for (CommonPrefix cp : res.commonPrefixes()) {
                                String full = cp.prefix();
                                String relative = full.substring(prefix.length());
                                if (relative.endsWith("/")) {
                                    relative = relative.substring(0, relative.length() - 1);
                                }
                                if (!relative.isBlank()) {
                                    uniqueMachines.add(relative);
                                }
                            }
                        }
                        continuationToken = res.nextContinuationToken();
                    } while (continuationToken != null);
                }

                // If still empty (e.g. range selected has no captures yet), fallback to scan allDates
                if (uniqueMachines.isEmpty() && !allDates.isEmpty() && datesToScan != allDates) {
                    for (String d : allDates) {
                        String prefix = d + "/";
                        ListObjectsV2Request req = ListObjectsV2Request.builder()
                                .bucket(bucketName)
                                .prefix(prefix)
                                .delimiter("/")
                                .build();
                        ListObjectsV2Response res = s3Client.listObjectsV2(req);
                        if (res.commonPrefixes() != null) {
                            for (CommonPrefix cp : res.commonPrefixes()) {
                                String full = cp.prefix();
                                String relative = full.substring(prefix.length());
                                if (relative.endsWith("/")) {
                                    relative = relative.substring(0, relative.length() - 1);
                                }
                                if (!relative.isBlank()) {
                                    uniqueMachines.add(relative);
                                }
                            }
                        }
                    }
                }

                List<String> machines = new ArrayList<>(uniqueMachines);
                Collections.sort(machines);
                byte[] json = gson.toJson(machines).getBytes(StandardCharsets.UTF_8);
                sendResponse(exchange, 200, "application/json; charset=UTF-8", json);
            } catch (Exception e) {
                logger.error("Error listing machines", e);
                sendJsonError(exchange, 500, e.getMessage());
            }
        }
    }

    /**
     * Returns screenshots matching date range (startDate to endDate) and machine.
     * Query params: startDate, endDate (or date), machine (optional)
     */
    private class ScreenshotsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "text/plain", "Method Not Allowed".getBytes(StandardCharsets.UTF_8));
                return;
            }

            Map<String, String> query = parseQueryParams(exchange.getRequestURI().getRawQuery());
            String singleDate = query.get("date");
            String startDate = query.get("startDate");
            String endDate = query.get("endDate");
            String machine = query.get("machine");

            if (startDate == null && singleDate != null) startDate = singleDate;
            if (endDate == null && singleDate != null) endDate = singleDate;

            try {
                List<String> allDates = listAvailableDates();
                List<String> targetDates = filterDatesInRange(allDates, startDate, endDate);

                List<JsonObject> items = new ArrayList<>();

                for (String d : targetDates) {
                    String prefix = d + "/";
                    if (machine != null && !machine.isBlank() && !machine.equalsIgnoreCase("ALL")) {
                        prefix += machine + "/";
                    }

                    String continuationToken = null;
                    do {
                        ListObjectsV2Request.Builder b = ListObjectsV2Request.builder()
                                .bucket(bucketName)
                                .prefix(prefix);
                        if (continuationToken != null) {
                            b.continuationToken(continuationToken);
                        }

                        ListObjectsV2Response res = s3Client.listObjectsV2(b.build());
                        if (res.contents() != null) {
                            for (S3Object obj : res.contents()) {
                                String key = obj.key();
                                if (key.endsWith(".jpg") || key.endsWith(".jpeg") || key.endsWith(".png")) {
                                    JsonObject item = new JsonObject();
                                    item.addProperty("key", key);
                                    item.addProperty("size", obj.size());
                                    item.addProperty("lastModified", obj.lastModified() != null ? obj.lastModified().toString() : "");

                                    String[] parts = key.split("/");
                                    String itemDate = parts.length > 0 ? parts[0] : "";
                                    String itemMachine = parts.length > 1 ? parts[1] : "";
                                    String filename = parts.length > 2 ? parts[2] : parts[parts.length - 1];

                                    item.addProperty("date", itemDate);
                                    item.addProperty("machine", itemMachine);
                                    item.addProperty("filename", filename);

                                    items.add(item);
                                }
                            }
                        }
                        continuationToken = res.nextContinuationToken();
                    } while (continuationToken != null);
                }

                // Sort newest capture first
                items.sort((a, b) -> b.get("key").getAsString().compareTo(a.get("key").getAsString()));

                JsonArray array = new JsonArray();
                for (JsonObject o : items) {
                    array.add(o);
                }

                byte[] json = gson.toJson(array).getBytes(StandardCharsets.UTF_8);
                sendResponse(exchange, 200, "application/json; charset=UTF-8", json);
            } catch (Exception e) {
                logger.error("Error listing screenshots", e);
                sendJsonError(exchange, 500, e.getMessage());
            }
        }
    }

    /**
     * Batch deletes objects from S3.
     * Accepts POST with JSON body: { "keys": ["key1", "key2", ...] } or ["key1", "key2", ...]
     */
    private class DeleteHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "text/plain", "Method Not Allowed".getBytes(StandardCharsets.UTF_8));
                return;
            }

            List<String> keysToDelete = new ArrayList<>();
            try (InputStream is = exchange.getRequestBody();
                 InputStreamReader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {
                JsonElement jsonElement = JsonParser.parseReader(reader);
                if (jsonElement.isJsonObject()) {
                    JsonObject obj = jsonElement.getAsJsonObject();
                    if (obj.has("keys") && obj.get("keys").isJsonArray()) {
                        for (JsonElement el : obj.getAsJsonArray("keys")) {
                            keysToDelete.add(el.getAsString());
                        }
                    }
                } else if (jsonElement.isJsonArray()) {
                    for (JsonElement el : jsonElement.getAsJsonArray()) {
                        keysToDelete.add(el.getAsString());
                    }
                }
            } catch (Exception e) {
                sendJsonError(exchange, 400, "Invalid JSON payload: " + e.getMessage());
                return;
            }

            if (keysToDelete.isEmpty()) {
                JsonObject res = new JsonObject();
                res.addProperty("success", true);
                res.addProperty("deletedCount", 0);
                sendResponse(exchange, 200, "application/json; charset=UTF-8", gson.toJson(res).getBytes(StandardCharsets.UTF_8));
                return;
            }

            try {
                int totalDeleted = 0;
                // S3 DeleteObjects supports up to 1000 keys per request
                for (int i = 0; i < keysToDelete.size(); i += 1000) {
                    List<String> chunk = keysToDelete.subList(i, Math.min(i + 1000, keysToDelete.size()));
                    List<ObjectIdentifier> objIds = chunk.stream()
                            .map(k -> ObjectIdentifier.builder().key(k).build())
                            .collect(Collectors.toList());

                    DeleteObjectsRequest delReq = DeleteObjectsRequest.builder()
                            .bucket(bucketName)
                            .delete(Delete.builder().objects(objIds).build())
                            .build();

                    DeleteObjectsResponse delRes = s3Client.deleteObjects(delReq);
                    int count = delRes.deleted() != null ? delRes.deleted().size() : chunk.size();
                    totalDeleted += count;
                }

                logger.info("Batch deleted {} objects from S3 bucket [{}]", totalDeleted, bucketName);
                JsonObject res = new JsonObject();
                res.addProperty("success", true);
                res.addProperty("deletedCount", totalDeleted);
                sendResponse(exchange, 200, "application/json; charset=UTF-8", gson.toJson(res).getBytes(StandardCharsets.UTF_8));
            } catch (Exception e) {
                logger.error("Error deleting objects from S3", e);
                sendJsonError(exchange, 500, "Delete failed: " + e.getMessage());
            }
        }
    }

    /**
     * Proxies image bytes from S3/R2 directly to browser with caching headers.
     * Query param: key=...
     */
    private class ImageProxyHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "text/plain", "Method Not Allowed".getBytes(StandardCharsets.UTF_8));
                return;
            }

            Map<String, String> query = parseQueryParams(exchange.getRequestURI().getRawQuery());
            String key = query.get("key");
            if (key == null || key.isBlank()) {
                sendResponse(exchange, 400, "text/plain", "Missing key".getBytes(StandardCharsets.UTF_8));
                return;
            }

            try {
                GetObjectRequest getReq = GetObjectRequest.builder()
                        .bucket(bucketName)
                        .key(key)
                        .build();

                try (ResponseInputStream<GetObjectResponse> s3Stream = s3Client.getObject(getReq)) {
                    exchange.getResponseHeaders().set("Content-Type", "image/jpeg");
                    exchange.getResponseHeaders().set("Cache-Control", "public, max-age=86400");

                    long contentLength = s3Stream.response().contentLength();
                    exchange.sendResponseHeaders(200, contentLength > 0 ? contentLength : 0);

                    try (OutputStream os = exchange.getResponseBody()) {
                        s3Stream.transferTo(os);
                    }
                }
            } catch (NoSuchKeyException e) {
                sendResponse(exchange, 404, "text/plain", "Image not found".getBytes(StandardCharsets.UTF_8));
            } catch (Exception e) {
                logger.error("Error retrieving image for key {}", key, e);
                sendResponse(exchange, 500, "text/plain", ("Error: " + e.getMessage()).getBytes(StandardCharsets.UTF_8));
            }
        }
    }

    private List<String> filterDatesInRange(List<String> allDates, String start, String end) {
        if (allDates == null || allDates.isEmpty()) {
            return Collections.emptyList();
        }

        if (start != null && end != null && start.compareTo(end) > 0) {
            String temp = start;
            start = end;
            end = temp;
        }

        final String finalStart = start;
        final String finalEnd = end;

        return allDates.stream().filter(d -> {
            if (finalStart != null && !finalStart.isBlank() && d.compareTo(finalStart) < 0) {
                return false;
            }
            if (finalEnd != null && !finalEnd.isBlank() && d.compareTo(finalEnd) > 0) {
                return false;
            }
            return true;
        }).collect(Collectors.toList());
    }

    private void sendResponse(HttpExchange exchange, int statusCode, String contentType, byte[] data) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(statusCode, data.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(data);
        }
    }

    private void sendJsonError(HttpExchange exchange, int statusCode, String message) throws IOException {
        JsonObject err = new JsonObject();
        err.addProperty("error", message);
        sendResponse(exchange, statusCode, "application/json; charset=UTF-8", gson.toJson(err).getBytes(StandardCharsets.UTF_8));
    }

    private Map<String, String> parseQueryParams(String rawQuery) {
        Map<String, String> params = new HashMap<>();
        if (rawQuery == null || rawQuery.isBlank()) {
            return params;
        }

        String[] pairs = rawQuery.split("&");
        for (String pair : pairs) {
            int idx = pair.indexOf("=");
            if (idx > 0 && idx < pair.length() - 1) {
                String k = URLDecoder.decode(pair.substring(0, idx), StandardCharsets.UTF_8);
                String v = URLDecoder.decode(pair.substring(idx + 1), StandardCharsets.UTF_8);
                params.put(k, v);
            } else if (idx > 0) {
                String k = URLDecoder.decode(pair.substring(0, idx), StandardCharsets.UTF_8);
                params.put(k, "");
            }
        }
        return params;
    }
}
