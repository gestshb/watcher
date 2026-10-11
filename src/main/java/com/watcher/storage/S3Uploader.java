package com.watcher.storage;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.FileReader;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Handles uploading captures to Amazon S3 or S3-compatible cloud storage (Cloudflare R2, MinIO, Wasabi, etc.)
 */
public class S3Uploader implements AutoCloseable {
    private static final Logger logger = LogManager.getLogger(S3Uploader.class);

    private final S3Client s3Client;
    private final String bucketName;
    private final String hostname;

    public S3Uploader(S3Client s3Client, String bucketName, String hostname) {
        this.s3Client = Objects.requireNonNull(s3Client, "s3Client cannot be null");
        this.bucketName = Objects.requireNonNull(bucketName, "bucketName cannot be null");
        this.hostname = hostname != null ? hostname : "UNKNOWN-HOST";
    }

    public S3Uploader(String credentialsPath, String defaultBucket, String defaultRegion,
                      String defaultAccessKey, String defaultSecretKey, String defaultEndpoint,
                      String hostname) throws Exception {
        this.hostname = hostname != null ? hostname : "UNKNOWN-HOST";

        String bucket = defaultBucket;
        String regionStr = defaultRegion != null && !defaultRegion.isBlank() ? defaultRegion : "us-east-1";
        String accessKey = defaultAccessKey;
        String secretKey = defaultSecretKey;
        String endpoint = defaultEndpoint;

        Path credPath = resolveCredentialsPath(credentialsPath);
        if (credPath != null && Files.exists(credPath)) {
            try (FileReader reader = new FileReader(credPath.toFile())) {
                JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                if (json.has("bucket")) bucket = json.get("bucket").getAsString();
                if (json.has("region")) regionStr = json.get("region").getAsString();
                if (json.has("accessKey")) accessKey = json.get("accessKey").getAsString();
                if (json.has("secretKey")) secretKey = json.get("secretKey").getAsString();
                if (json.has("endpoint")) endpoint = json.get("endpoint").getAsString();
                logger.info("Loaded S3 credentials from [{}]", credPath.toAbsolutePath());
            } catch (Exception e) {
                logger.warn("Could not fully parse credentials JSON, falling back to defaults: {}", e.getMessage());
            }
        } else {
            logger.warn("No credentials file found for path [{}]. Falling back to provided defaults.", credentialsPath);
        }

        this.bucketName = Objects.requireNonNull(bucket, "Bucket name must be configured");

        S3ClientBuilder builder = S3Client.builder()
                .httpClientBuilder(UrlConnectionHttpClient.builder())
                .region(Region.of(regionStr));

        if (accessKey != null && !accessKey.isBlank() && secretKey != null && !secretKey.isBlank()) {
            builder.credentialsProvider(StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(accessKey, secretKey)
            ));
        } else {
            builder.credentialsProvider(DefaultCredentialsProvider.create());
        }

        if (endpoint != null && !endpoint.isBlank()) {
            builder.endpointOverride(URI.create(endpoint));
            builder.forcePathStyle(true);
        }

        this.s3Client = builder.build();
        logger.info("S3Uploader initialized for bucket [{}] in region [{}]", this.bucketName, regionStr);
    }

    /**
     * Uploads a screenshot file to the S3 bucket partitioned by date and hostname.
     * Object Key pattern: YYYY-MM-DD/hostname/fileName.jpg
     */
    public boolean uploadScreenshot(Path filePath, String dateStr) {
        if (!Files.exists(filePath)) {
            logger.warn("File does not exist for upload: {}", filePath);
            return false;
        }

        String objectKey = dateStr + "/" + hostname + "/" + filePath.getFileName().toString();

        try {
            PutObjectRequest putRequest = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(objectKey)
                    .contentType("image/jpeg")
                    .build();

            s3Client.putObject(putRequest, RequestBody.fromFile(filePath));
            logger.info("Successfully uploaded [{}] to S3 bucket [{}] with key [{}]",
                    filePath.getFileName(), bucketName, objectKey);
            return true;
        } catch (Exception e) {
            logger.error("Failed to upload [{}] to S3: {}", filePath.getFileName(), e.getMessage());
            return false;
        }
    }

    public static Path resolveCredentialsPath(String configuredPath) {
        return com.watcher.config.PathResolver.resolveCredentials(configuredPath);
    }

    public S3Client getS3Client() {
        return s3Client;
    }

    public String getBucketName() {
        return bucketName;
    }

    public String getHostname() {
        return hostname;
    }

    @Override
    public void close() {
        if (s3Client != null) {
            try {
                s3Client.close();
            } catch (Exception ignored) {
            }
        }
    }
}
