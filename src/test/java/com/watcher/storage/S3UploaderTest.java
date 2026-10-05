package com.watcher.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class S3UploaderTest {

    @Test
    public void testUploadScreenshotSuccess(@TempDir Path tempDir) throws Exception {
        S3Client mockS3 = mock(S3Client.class);
        when(mockS3.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());

        S3Uploader uploader = new S3Uploader(mockS3, "test-bucket", "STUDY-PC");

        Path sampleFile = tempDir.resolve("screenshot_STUDY-PC_2026-09-28_10-00-00_display0.jpg");
        Files.write(sampleFile, new byte[]{1, 2, 3, 4});

        boolean uploaded = uploader.uploadScreenshot(sampleFile, "2026-09-28");
        assertTrue(uploaded);

        ArgumentCaptor<PutObjectRequest> captor = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(mockS3, times(1)).putObject(captor.capture(), any(RequestBody.class));

        PutObjectRequest sentRequest = captor.getValue();
        assertEquals("test-bucket", sentRequest.bucket());
        assertEquals("2026-09-28/STUDY-PC/screenshot_STUDY-PC_2026-09-28_10-00-00_display0.jpg", sentRequest.key());
        assertEquals("image/jpeg", sentRequest.contentType());
    }

    @Test
    public void testUploadNonExistentFileReturnsFalse() {
        S3Client mockS3 = mock(S3Client.class);
        S3Uploader uploader = new S3Uploader(mockS3, "test-bucket", "STUDY-PC");

        boolean uploaded = uploader.uploadScreenshot(Path.of("non_existent_file.jpg"), "2026-09-28");
        assertFalse(uploaded);
        verify(mockS3, never()).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }
}
