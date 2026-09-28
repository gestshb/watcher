# Implementation Plan: RetentionCleanerService for Purging Expired Images (30 Days)

Implement a dedicated background service (`RetentionCleanerService`) to periodically purge images/folders older than 30 days (`retentionDays`) from both local spool storage and Google Drive cloud storage.

## User Review Checkpoints
- **Task 1**: Implement and verify local spool retention purging in `LocalSpoolManagerTest` and `LocalSpoolManager`.
- **Task 2**: Implement `RetentionCleanerService` with unit tests for local + cloud execution and error resilience.
- **Task 3**: Decouple retention logic from `CaptureScheduler` and integrate `RetentionCleanerService` into `App.java`.
- **Task 4**: Full verification and end-to-end test execution.

---

### Task 1: Add Local Spool Retention Purge (`LocalSpoolManager`)

**Files**:
- Test: `src/test/java/com/watcher/storage/LocalSpoolManagerTest.java`
- Implementation: `src/main/java/com/watcher/storage/LocalSpoolManager.java`

**Steps**:
1. Write unit test in `LocalSpoolManagerTest.java` that creates sample files with modified times older than 30 days and newer than 30 days, verifies `purgeOlderThan(30)` deletes only expired files and returns correct count.
2. Run test to verify it fails (`purgeOlderThan` not found or unhandled).
3. Implement `public synchronized int purgeOlderThan(int retentionDays)` in `LocalSpoolManager.java` utilizing `Files.getLastModifiedTime(path)` against `cutoffMillis`.
4. Run tests with `mvn test -Dtest=LocalSpoolManagerTest` to ensure it passes.

---

### Task 2: Create `RetentionCleanerService` and Unit Tests

**Files**:
- Implementation: `src/main/java/com/watcher/scheduler/RetentionCleanerService.java`
- Test: `src/test/java/com/watcher/scheduler/RetentionCleanerServiceTest.java`

**Steps**:
1. Write unit test `RetentionCleanerServiceTest.java`:
   - Test `runCleanupCycle()` executes local spool purge and records audit event.
   - Test `runCleanupCycle()` handles null uploader safely.
   - Test `start()` and `stop()` lifecycle for background scheduler.
2. Implement `RetentionCleanerService.java`:
   - Single daemon thread `ScheduledExecutorService`.
   - Runs `runCleanupCycle()` every 24 hours (configurable period/initial delay).
   - Coordinates `spoolManager.purgeOlderThan(...)` and `CloudRetentionCleaner.cleanOldFolders(...)`.
   - Audit logging via `SecurityAuditor`.
3. Run tests with `mvn test -Dtest=RetentionCleanerServiceTest` to ensure it passes.

---

### Task 3: Decouple from `CaptureScheduler` and Integrate into `App.java`

**Files**:
- Modify: `src/main/java/com/watcher/scheduler/CaptureScheduler.java`
- Modify: `src/main/java/com/watcher/App.java`

**Steps**:
1. In `CaptureScheduler.java`:
   - Remove lines invoking `CloudRetentionCleaner` during capture ticks, as retention is now owned by `RetentionCleanerService`.
2. In `App.java`:
   - Instantiate `RetentionCleanerService retentionService = new RetentionCleanerService(config, spoolManager, uploader, auditor);`
   - Start the service: `retentionService.start();`
   - Hook into JVM shutdown: `retentionService.stop();`
3. Run all unit tests to confirm nothing is broken.

---

### Task 4: Full Verification

**Steps**:
1. Run `mvn clean test`.
2. Verify all tests pass cleanly.
