package com.watcher.storage;

import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class CloudRetentionCleanerTest {

    @Test
    public void testCleanOldFoldersDeletesExpiredPrefixes() {
        S3Client mockS3 = mock(S3Client.class);

        CommonPrefix oldPrefix = CommonPrefix.builder().prefix("2026-01-01/").build();
        CommonPrefix newPrefix = CommonPrefix.builder().prefix("2099-01-01/").build();

        ListObjectsV2Response prefixResponse = ListObjectsV2Response.builder()
                .commonPrefixes(List.of(oldPrefix, newPrefix))
                .build();

        when(mockS3.listObjectsV2(any(ListObjectsV2Request.class))).thenAnswer(invocation -> {
            ListObjectsV2Request req = invocation.getArgument(0);
            if ("/".equals(req.delimiter())) {
                return prefixResponse;
            } else if ("2026-01-01/".equals(req.prefix())) {
                return ListObjectsV2Response.builder()
                        .contents(List.of(
                                S3Object.builder().key("2026-01-01/PC1/shot1.jpg").build(),
                                S3Object.builder().key("2026-01-01/PC1/shot2.jpg").build()
                        ))
                        .build();
            }
            return ListObjectsV2Response.builder().contents(Collections.emptyList()).build();
        });

        CloudRetentionCleaner cleaner = new CloudRetentionCleaner(mockS3, "test-bucket");
        int deleted = cleaner.cleanOldFolders(30);

        assertEquals(1, deleted);
        verify(mockS3, times(1)).deleteObjects(any(DeleteObjectsRequest.class));
    }
}
