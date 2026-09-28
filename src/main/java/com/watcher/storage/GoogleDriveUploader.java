package com.watcher.storage;

import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.FileContent;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles communication with Google Drive v3 API using either OAuth2 Desktop Flow or Service Account.
 */
public class GoogleDriveUploader {
    private static final Logger logger = LogManager.getLogger(GoogleDriveUploader.class);
    private static final String APPLICATION_NAME = "WatcherDiagnostics";
    private static final DateTimeFormatter DATE_DIR_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final String TOKENS_DIRECTORY_PATH = "tokens";

    private final Drive driveService;
    private final String rootFolderName;
    private final String hostname;
    private final Map<String, String> folderCache = new ConcurrentHashMap<>();

    public GoogleDriveUploader(String credentialsPath, String rootFolderName, String hostname) throws Exception {
        this.rootFolderName = rootFolderName;
        this.hostname = hostname;
        this.driveService = initializeDriveService(credentialsPath);
    }

    public GoogleDriveUploader(Drive driveService, String rootFolderName, String hostname) {
        this.driveService = driveService;
        this.rootFolderName = rootFolderName;
        this.hostname = hostname;
    }

    private Drive initializeDriveService(String credentialsPath) throws Exception {
        Path credPath = Path.of(credentialsPath);
        if (!Files.exists(credPath)) {
            throw new IOException("Google credentials file not found at: " + credentialsPath);
        }

        String content = Files.readString(credPath);
        NetHttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();
        GsonFactory jsonFactory = GsonFactory.getDefaultInstance();

        if (content.contains("\"installed\"") || content.contains("\"web\"")) {
            logger.info("Detected OAuth 2.0 Client credentials. Initializing OAuth flow...");
            GoogleClientSecrets clientSecrets;
            try (FileReader reader = new FileReader(credPath.toFile())) {
                clientSecrets = GoogleClientSecrets.load(jsonFactory, reader);
            }

            GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
                    httpTransport,
                    jsonFactory,
                    clientSecrets,
                    Collections.singletonList(DriveScopes.DRIVE_FILE)
            )
                    .setDataStoreFactory(new FileDataStoreFactory(new java.io.File(TOKENS_DIRECTORY_PATH)))
                    .setAccessType("offline")
                    .build();

            LocalServerReceiver receiver = new LocalServerReceiver.Builder().setPort(8888).build();
            com.google.api.client.auth.oauth2.Credential credential =
                    new AuthorizationCodeInstalledApp(flow, receiver).authorize("user");

            return new Drive.Builder(httpTransport, jsonFactory, credential)
                    .setApplicationName(APPLICATION_NAME)
                    .build();
        } else {
            logger.info("Detected Service Account credentials. Initializing Service Account flow...");
            try (FileInputStream serviceAccountStream = new FileInputStream(credPath.toFile())) {
                GoogleCredentials credentials = GoogleCredentials.fromStream(serviceAccountStream)
                        .createScoped(Collections.singletonList(DriveScopes.DRIVE_FILE));

                HttpRequestInitializer requestInitializer = new HttpCredentialsAdapter(credentials);
                return new Drive.Builder(httpTransport, jsonFactory, requestInitializer)
                        .setApplicationName(APPLICATION_NAME)
                        .build();
            }
        }
    }

    public String uploadFile(Path localFile, String mimeType) throws Exception {
        if (!Files.exists(localFile)) {
            throw new IOException("File does not exist: " + localFile);
        }

        String targetFolderId = resolveTargetFolderId();

        File fileMetadata = new File();
        fileMetadata.setName(localFile.getFileName().toString());
        fileMetadata.setParents(Collections.singletonList(targetFolderId));

        FileContent mediaContent = new FileContent(mimeType, localFile.toFile());

        int maxAttempts = 3;
        int delayMs = 2000;
        Exception lastException = null;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                File uploaded = driveService.files().create(fileMetadata, mediaContent)
                        .setFields("id, name, size")
                        .execute();
                logger.info("Uploaded '{}' to Google Drive (File ID: {})", localFile.getFileName(), uploaded.getId());
                return uploaded.getId();
            } catch (Exception e) {
                lastException = e;
                logger.warn("Upload attempt {} failed for {}: {}", attempt, localFile.getFileName(), e.getMessage());
                if (attempt < maxAttempts) {
                    Thread.sleep(delayMs);
                    delayMs *= 2;
                }
            }
        }

        throw new IOException("Failed to upload " + localFile.getFileName() + " after " + maxAttempts + " attempts", lastException);
    }

    private synchronized String resolveTargetFolderId() throws IOException {
        String today = LocalDate.now().format(DATE_DIR_FORMAT);
        String cacheKey = rootFolderName + "/" + today + "/" + hostname;

        String cachedId = folderCache.get(cacheKey);
        if (cachedId != null) {
            return cachedId;
        }

        String rootId = getOrCreateFolder(rootFolderName, null);
        String dateFolderId = getOrCreateFolder(today, rootId);
        String hostFolderId = getOrCreateFolder(hostname, dateFolderId);

        folderCache.put(cacheKey, hostFolderId);
        return hostFolderId;
    }

    public String getOrCreateFolder(String folderName, String parentId) throws IOException {
        StringBuilder query = new StringBuilder("mimeType = 'application/vnd.google-apps.folder' and trashed = false and name = '")
                .append(folderName.replace("'", "\\'"))
                .append("'");

        if (parentId != null) {
            query.append(" and '").append(parentId).append("' in parents");
        }

        FileList result = driveService.files().list()
                .setQ(query.toString())
                .setSpaces("drive")
                .setFields("files(id, name)")
                .execute();

        List<File> files = result.getFiles();
        if (files != null && !files.isEmpty()) {
            return files.get(0).getId();
        }

        File folderMetadata = new File();
        folderMetadata.setName(folderName);
        folderMetadata.setMimeType("application/vnd.google-apps.folder");
        if (parentId != null) {
            folderMetadata.setParents(Collections.singletonList(parentId));
        }

        File created = driveService.files().create(folderMetadata)
                .setFields("id")
                .execute();

        logger.info("Created Google Drive folder: '{}' (ID: {})", folderName, created.getId());
        return created.getId();
    }

    public Drive getDriveService() {
        return driveService;
    }
}
