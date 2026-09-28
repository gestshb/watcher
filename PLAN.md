# Watcher (مراقب دراسة الأولاد) - Detailed Implementation Plan & Specification (v2.0)

---

## 1. Overview & Objective
**Watcher** is a stealth, highly resilient cross-platform background monitoring daemon written in **Java 17 LTS** designed to monitor computer study sessions on **Windows, macOS, and Linux**.

### Primary Objectives:
1. **Periodic Multi-Monitor Screen Capturing**: Takes full screenshots across all active displays at configurable intervals (e.g., 3, 5, or 10 minutes) using `java.awt.Robot` with high-efficiency JPEG compression.
2. **Automated Cloud Sync**: Uploads captures to Google Drive via Google Drive API v3 using a Google Cloud Service Account (no browser login required).
3. **Session-Aware Architecture (Hybrid Design)**: Solves the OS "Session 0 Isolation / Headless Daemon" dilemma:
   - **User Session Agent (Watcher Worker + Watchdog Guard)**: Executes inside the active interactive user desktop session to access display buffers.
   - **System Service Supervisor**: Runs with `SYSTEM`/`root` authority to guarantee the desktop agent is continuously running and immediately restored if tampered with.
4. **Hardened Anti-Tamper Protection**:
   - Dual-process mutual watchdog heartbeat.
   - Files owned by `SYSTEM`/`root`, locked via strict ACLs; non-admin users have zero write or delete permissions.
   - Disguised process and service identities (`WinSystemHealthService` / `system-health-monitor`).
   - Configuration encrypted with `AES-256-GCM` using keys derived via PBKDF2 (HMAC-SHA256, 100,000 rounds).
   - Tamper and crash audit logging synced to Google Drive.
   - Admin-only CLI with password authentication for maintenance, pausing, and uninstallation.

---

## 2. Hybrid Dual-Tier Architecture

To bypass **Windows Session 0 Isolation**, **macOS WindowServer Sandbox**, and **Linux X11/Wayland display permissions**, the architecture is split into two coordinated tiers:

```
+===============================================================================+
|                   TIER 1: System Service (SYSTEM / root)                      |
|  - Windows Service (WinSW) / macOS LaunchDaemon / Linux systemd               |
|  - Owned by root/SYSTEM; cannot be stopped by standard user                   |
|  - Monitors Tier 2: Ensures Login Item / Autostart entry is NEVER removed      |
|  - If child removes autostart registry/plist, Tier 1 restores it immediately  |
+===============================================================================+
                                      │ Supervises persistence & file ACLs
                                      ▼
+===============================================================================+
|               TIER 2: Interactive Desktop Session (Child Account)             |
|                                                                               |
|   ┌───────────────────────────────┐   Socket / Pipe Ping  ┌────────────────┐  |
|   │    Main Worker Process        │ <===================> │ Watchdog Guard │  |
|   │ (Screen Capture & Drive Sync) │                       │ (Process Guard)│  |
|   └──────────────┬────────────────┘                       └───────┬────────┘  |
|                  │ (If Main killed)                               │ (If Guard |
|                  └────────────── Watchdog restarts Main ◄─────────┘   killed) |
|                  ┌────────────── Main restarts Watchdog ◄─────────────────────┘
+──────────────────┼────────────────────────────────────────────────────────────+
                   │ Has full access to Desktop Display Buffer
                   ▼
+────────────────────────────────────────────────────────────────---------------+
|                          Core Functional Pipeline                             |
|                                                                               |
|  ┌───────────────────┐    ┌────────────────────┐    ┌──────────────────────┐  |
|  | ScreenCapture     |    | Local Spool Buffer |    | GoogleDriveUploader  |  |
|  | (java.awt.Robot)  |==> | (Encrypted /       |==> | (Drive API v3,       |  |
|  | Multi-monitor     |    | Max Disk Bound)    |    | Service Account)     |  |
|  └───────────────────┘    └────────────────────┘    └──────────────────────┘  |
|                                                                               |
|  ┌───────────────────────────────────────────────┐    ┌────────────────────┐  |
|  | ConfigManager & Encryptor                     |    | Security Audit Log |  |
|  | - AES-256-GCM / PBKDF2 derived                |    | - Tamper attempts  |  |
|  | - Protected by admin password                 |    | - Uploaded to Drive|  |
|  └───────────────────────────────────────────────┘    └────────────────────┘  |
+-------------------------------------------------------------------------------+
```

---

## 3. Technology Stack & Dependencies

| Component | Library / Version | Purpose |
|---|---|---|
| Language | **Java 17 LTS** | Cross-platform runtime, modern ProcessHandle & crypto APIs |
| Build Tool | **Apache Maven** | Build automation & `maven-shade-plugin` for Fat JAR |
| Cloud Storage | `com.google.apis:google-api-services-drive:v3-rev20240502-2.0.0` | Official Google Drive API client |
| Cloud Auth | `com.google.auth:google-auth-library-oauth2-http:1.24.0` | Service Account JSON authentication |
| CLI Framework | `info.picocli:picocli:4.7.6` | Admin CLI command & option parsing |
| Logging | `org.apache.logging.log4j:log4j-core:2.23.1` | Asynchronous file and security audit logging |
| Cryptography | Java Standard Library (`javax.crypto`) | `AES-256-GCM`, `PBKDF2WithHmacSHA256` |
| Testing | `org.junit.jupiter:junit-jupiter:5.10.2` & `org.mockito:mockito-core:5.11.0` | Comprehensive unit and mock tests |

---

## 4. Module Specifications

### 4.1. Screen Capture Module (`com.watcher.capture.ScreenCapture`)
- **Technology**: `java.awt.GraphicsEnvironment`, `java.awt.GraphicsDevice`, `java.awt.Robot`.
- **Multi-Monitor Logic**:
  - Queries `GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices()`.
  - Captures each screen device independently into buffered images.
- **Image Compression (`ImageCompressor`)**:
  - Compresses captures to JPEG using `ImageWriteParam.MODE_EXPLICIT`.
  - Configurable compression quality (`0.1` to `1.0`, default `0.6` / 60%).
  - Average frame size: 100 KB - 250 KB per monitor at 1080p.
- **Naming Pattern**:
  - `screenshot_{HOSTNAME}_{YYYY-MM-DD_HH-mm-ss}_display{INDEX}.jpg`

### 4.2. Local Spool & Offline Queue (`com.watcher.storage.LocalSpoolManager`)
- **Offline Resilience**: When internet access is disconnected, captured images remain in an encrypted/protected local directory:
  - Windows: `C:\ProgramData\SystemHealth\spool\`
  - macOS: `/Library/Application Support/SystemHealth/spool/`
  - Linux: `/var/lib/systemhealth/spool/`
- **FIFO Disk Bounds**:
  - Configurable spool limit (default `500 MB`).
  - If the limit is exceeded during extended offline periods, the oldest files are dropped first to prevent disk exhaustion.
- **Auto-Purge**: Files are safely unlinked immediately after Google Drive reports a 200 OK upload response (`capture.delete.after.upload=true`).

### 4.3. Google Drive Storage Module (`com.watcher.storage.GoogleDriveUploader`)
- **Authentication**:
  - Service Account authentication via private JSON key (`credentials.json`).
  - Scope: `https://www.googleapis.com/auth/drive.file`.
- **Folder Hierarchy**:
  - Top-level root: Configurable via `drive.folder.name` (default: `WatcherScreenshots`).
  - Daily partition: `WatcherScreenshots/{YYYY-MM-DD}/{HOSTNAME}/`.
  - Daily folder IDs are cached in memory to minimize API queries.
- **Cloud Retention & Cleanup Policy**:
  - Configurable `drive.retention.days` (default: `30` days).
  - Background worker runs once daily to query and delete folders older than the retention threshold.
- **Retry Logic**:
  - Exponential backoff (3 attempts: 2s, 4s, 8s) for handling transient network failures.

### 4.4. Security Audit Log (`com.watcher.security.SecurityAuditor`)
- **Tracked Security Events**:
  - Service started / stopped.
  - Watchdog heartbeat loss & process resurrection count.
  - Failed admin CLI password attempts.
  - Offline buffer queue overflow events.
- **Cloud Synchronization**:
  - Audit log is uploaded once daily (or when reaching 100 KB) as `audit_{HOSTNAME}_{YYYY-MM-DD}.log` inside the machine's cloud folder.

### 4.5. Anti-Tamper & Protection (`com.watcher.protection`)
1. **Interactive Dual-Process Watchdog**:
   - `App.java` (Worker) spawns `Watchdog.java` as a detached child process with parent PID.
   - Communication via a local loopback TCP port or named pipe / OS process handle check (`ProcessHandle.of(pid).isAlive()`).
   - If either process is terminated, the survivor relaunches the partner within 3 seconds.
2. **Tier-1 System Supervisor (Service Auto-Healing)**:
   - Runs as `SYSTEM`/`root`.
   - Periodically verifies that the Tier-2 user-session processes are active and that autostart entries (Run keys, LaunchAgents) remain intact.
   - If a child deletes registry autostart or plist files, the Tier-1 service regenerates them immediately.
3. **Encrypted Configuration & Admin CLI (`com.watcher.cli.AdminCli`)**:
   - Configuration is stored as `config.dat` encrypted with **AES-256-GCM** with a salt-derived key using PBKDF2 (100,000 iterations).
   - Only the parent can decrypt and adjust settings via CLI:
     ```bash
     java -jar syshealthd.jar --admin --password "<SECRET>" [OPTIONS]
     ```
   - Supported CLI Options:
     - `--status`: Display daemon status, last capture time, offline queue size.
     - `--set-interval <minutes>`: Update capture interval.
     - `--set-retention <days>`: Update cloud retention period.
     - `--change-password <new>`: Re-encrypt config with new admin password.
     - `--pause <minutes>`: Temporarily suspend captures (e.g., for privacy).
     - `--stop`: Authorize daemon stop.
     - `--uninstall`: Completely remove services, autostarts, and delete files.

### 4.6. OS Platform Integration & Permissions
- **Windows**:
  - Tier 1: Windows Service managed via WinSW (`WinSystemHealthService`).
  - Tier 2: Registry Run key under `HKEY_LOCAL_MACHINE\Software\Microsoft\Windows\CurrentVersion\Run` or Task Scheduler triggered "At log on of any user".
  - File ACL:
    ```cmd
    icacls "C:\ProgramData\SystemHealth" /inheritance:r /grant:r "SYSTEM":(OI)(CI)F /grant:r "Administrators":(OI)(CI)F /deny "Users":(OI)(CI)W
    ```
- **macOS**:
  - Tier 1: `LaunchDaemon` in `/Library/LaunchDaemons/com.system.healthmonitor.plist` (`KeepAlive=true`, runs as `root`).
  - Tier 2: `LaunchAgent` in `/Library/LaunchAgents/com.system.healthagent.plist` (runs in child's user session).
  - **Screen Recording Permission**: Handled during parent installation via `tccutil` / macOS Security & Privacy prompt.
  - Permissions: `chmod 700 /Library/Application Support/SystemHealth`, owned by `root:wheel`.
- **Linux**:
  - Tier 1: `systemd` service `/etc/systemd/system/system-health-monitor.service` (`Restart=always`).
  - Tier 2: XDG Autostart `/etc/xdg/autostart/syshealth.desktop`.
  - Permissions: `chmod 700 /var/lib/systemhealth`, owned by `root:root`.

---

## 5. File & Directory Layout

```
watcher/
├── pom.xml
├── PLAN.md
├── README.md
├── scripts/
│   ├── install-windows.bat             # Tier 1 & 2 installer (run as Admin)
│   ├── install-mac.sh                  # macOS installer (run with sudo)
│   ├── install-linux.sh                # Linux installer (run with sudo)
│   └── uninstall.sh / .bat             # Uninstaller requiring admin password
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/
│   │   │       └── watcher/
│   │   │           ├── App.java                        # Main worker entry point
│   │   │           ├── cli/
│   │   │           │   └── AdminCli.java               # Password-protected Admin CLI
│   │   │           ├── config/
│   │   │           │   ├── AppConfig.java              # Configuration POJO
│   │   │           │   ├── ConfigEncryptor.java        # AES-256-GCM & PBKDF2 engine
│   │   │           │   └── ConfigManager.java          # Encrypted load/save manager
│   │   │           ├── capture/
│   │   │           │   ├── ScreenCapture.java          # Multi-screen Robot capture
│   │   │           │   └── ImageCompressor.java        # JPEG compression engine
│   │   │           ├── storage/
│   │   │           │   ├── GoogleDriveUploader.java    # Drive API v3 client
│   │   │           │   ├── LocalSpoolManager.java      # Spool buffer & FIFO limits
│   │   │           │   └── CloudRetentionCleaner.java  # Deletes captures > N days
│   │   │           ├── scheduler/
│   │   │           │   ├── CaptureScheduler.java       # ScheduledExecutor loop
│   │   │           │   └── RetentionCleanerService.java # Background retention purge (local & cloud)
│   │   │           ├── security/
│   │   │           │   └── SecurityAuditor.java        # Audit logging of tamper events
│   │   │           └── protection/
│   │   │               ├── ProcessGuard.java           # Cross-process supervisor logic
│   │   │               ├── Watchdog.java               # Detached watchdog daemon
│   │   │               └── SystemSupervisor.java       # Tier-1 system service daemon
│   │   └── resources/
│   │       ├── log4j2.xml                              # Rolling file logging config
│   │       ├── winsw-template.xml                      # WinSW service configuration
│   │       ├── com.system.healthmonitor.plist          # macOS LaunchDaemon template
│   │       ├── com.system.healthagent.plist            # macOS LaunchAgent template
│   │       └── system-health-monitor.service           # Linux systemd template
│   └── test/
│       └── java/
│           └── com/
│               └── watcher/
│                   ├── config/
│                   │   └── ConfigEncryptorTest.java    # AES-GCM & password validation
│                   ├── capture/
│                   │   └── ImageCompressorTest.java    # Compression ratio tests
│                   ├── storage/
│                   │   ├── LocalSpoolManagerTest.java  # FIFO limit & purge tests
│                   │   └── GoogleDriveUploaderTest.java# Mocked upload & retry tests
│                   └── protection/
│                       └── ProcessGuardTest.java       # Heartbeat & recovery logic
└── docs/
    ├── google-cloud-setup.md                           # Step-by-step Service Account guide
    └── user-manual.md                                  # Parent manual for CLI & setup
```

---

## 6. Implementation Phases & Milestones

### Phase 1: Project Setup, Crypto & Config
- Initialize Maven `pom.xml` with dependencies (Drive v3, Picocli, Log4j2, JUnit 5).
- Build `ConfigEncryptor.java` with AES-256-GCM encryption and PBKDF2 key derivation.
- Build `ConfigManager.java` to handle persistent encrypted reading/writing.
- Write unit tests in `ConfigEncryptorTest.java`.

### Phase 2: Capture Engine, Spool Buffer & Retention
- Implement `ScreenCapture.java` supporting single & multi-monitor configurations using `java.awt.Robot`.
- Implement `ImageCompressor.java` with configurable JPEG quality.
- Build `LocalSpoolManager.java` with FIFO disk limits and offline persistence.
- Implement `CloudRetentionCleaner.java` for auto-purging old Drive files.
- Unit tests in `ImageCompressorTest.java` and `LocalSpoolManagerTest.java`.

### Phase 3: Cloud Synchronization & Audit Logging
- Build `GoogleDriveUploader.java` supporting Google Cloud Service Account credentials.
- Implement folder creation and hierarchical date structure: `WatcherScreenshots/{YYYY-MM-DD}/{HOSTNAME}/`.
- Add retry logic with exponential backoff.
- Implement `SecurityAuditor.java` for tracking system starts, restarts, and tamper attempts.
- Integration tests with Mockito in `GoogleDriveUploaderTest.java`.

### Phase 4: Anti-Tamper, Dual Watchdog & Tier-1 Supervisor
- Implement `Watchdog.java` as an autonomous sibling process.
- Implement mutual heartbeat supervision in `ProcessGuard.java`.
- Implement `SystemSupervisor.java` for the Tier-1 root daemon to monitor autostart files and revive Tier-2 agents.
- Tests in `ProcessGuardTest.java`.

### Phase 5: Admin CLI & Control System
- Build `AdminCli.java` with Picocli and password authentication.
- Implement commands: `--status`, `--set-interval`, `--set-retention`, `--pause`, `--stop`, `--change-password`, `--uninstall`.

### Phase 6: Packaging & OS Installers
- Configure `maven-shade-plugin` to assemble executable Fat JAR `syshealthd.jar`.
- Write `install-windows.bat` configuring WinSW service and registry Run keys with strict ACLs.
- Write `install-mac.sh` generating LaunchDaemon + LaunchAgent with permissions guide.
- Write `install-linux.sh` generating systemd service + XDG autostart.
- Write `uninstall` scripts requiring admin password verification.
- Author documentation: `docs/google-cloud-setup.md` and `docs/user-manual.md`.

---

## 7. Storage Estimation & Operational Notes

| Setting | Value | Notes |
|---|---|---|
| Interval | 10 minutes | 6 captures / hour |
| Active Hours | 8 hours / day | 48 captures / day |
| Average Size | 150 KB / image | ~7.2 MB / day |
| Monthly Usage | 30 days | **~216 MB / month** (well within Google Drive 15 GB free tier) |
| 3-Minute Interval | 20 captures / hour | ~24 MB / day (~720 MB / month) |

### Parent Prerequisites:
1. **Google Cloud Console**:
   - Create project, enable **Google Drive API**.
   - Create **Service Account**, download `credentials.json`.
   - Create Google Drive folder (e.g., `WatcherScreenshots`), share with the Service Account email as **Editor**.
2. **Child Account Setup**:
   - Child's computer account must be a **Standard User** (not Administrator / sudoer).
3. **macOS Specifics**:
   - On macOS, open **System Settings > Privacy & Security > Screen Recording**, and grant permission to Java / the launcher agent.
