# Watcher - Background Monitoring Daemon

[![Java Version](https://img.shields.io/badge/Java-17%2B-ED8B00?logo=openjdk&logoColor=white)](https://openjdk.org/)
[![Build Tool](https://img.shields.io/badge/Maven-3.9%2B-C71A36?logo=apachemaven&logoColor=white)](https://maven.apache.org/)
[![Platform](https://img.shields.io/badge/Platform-Windows%20%7C%20macOS%20%7C%20Linux-blue)](https://github.com/)
[![Storage](https://img.shields.io/badge/Storage-S3%20%7C%20Cloudflare%20R2-orange)](https://aws.amazon.com/s3/)
[![Encryption](https://img.shields.io/badge/Security-AES--256--GCM-green)](https://en.wikipedia.org/wiki/Galois/Counter_Mode)

> **Watcher** is a stealth, self-protecting background monitoring daemon designed for parents to supervise children's study and computer activity. It periodically captures multi-monitor screenshots, compresses them, and silently uploads them to **S3-compatible cloud storage** (such as **Cloudflare R2** free tier or **Amazon AWS S3**). It includes a built-in local web viewer and administration CLI.

📖 **Language**: [English](README.md) | [العربية (Arabic Guide)](README.ar.md)

---

## 📑 Table of Contents
1. [🌟 Key Features](#1--key-features)
2. [🛡️ Anti-Tampering & Security Requirements](#2-️-anti-tampering--security-requirements)
3. [☁️ Cloud Storage Setup (Cloudflare R2 / AWS S3)](#3-️-cloud-storage-setup-cloudflare-r2--aws-s3)
4. [🔨 Building the Project](#4--building-the-project)
5. [🚀 Installation & Deployment](#5--installation--deployment)
   - [Windows](#a-windows)
   - [macOS](#b-macos)
   - [Linux](#c-linux)
6. [🎮 Manual Control & Verification Scripts](#6--manual-control--verification-scripts)
7. [🌐 Built-in Web Viewer Dashboard](#7--built-in-web-viewer-dashboard)
8. [🛠️ Administration CLI](#8-️-administration-cli)
9. [🔄 Automatic Retention Cleaner](#9--automatic-retention-cleaner)
10. [📊 Data Consumption Estimates](#10--data-consumption-estimates)
11. [📂 Project Structure](#11--project-structure)

---

## 1. 🌟 Key Features

- 🖥️ **Full Multi-Monitor Screen Capture**:
  - Automatically detects and captures all connected monitors via `java.awt.Robot`.
  - Configurable JPEG compression (~100–200 KB per capture).
- ☁️ **Silent S3 Cloud Synchronization**:
  - Direct API key uploads without requiring browser authentication or OAuth prompts.
  - Full compatibility with **Cloudflare R2** (10 GB free monthly, zero egress fees), **AWS S3**, and **MinIO**.
  - Hierarchical storage partition: `bucket/YYYY-MM-DD/Hostname/screenshot_...jpg`.
- 🛡️ **Hardened Anti-Tamper & Stealth**:
  - **Mutual Watchdog Guard**: Secondary supervisor process monitors the daemon; if killed in Task Manager, the watchdog relaunches it instantly.
  - **Stealth Disguise**: Runs under the system maintenance profile `System Health Diagnostics (syshealthd)`.
  - **Encrypted Settings**: Configuration file `config.dat` is secured with **`AES-256-GCM`** using keys derived via **`PBKDF2WithHmacSHA256`** (100,000 salt iterations).
  - **Independent Security Audit**: Logs tamper attempts and service events into an encrypted log.
- 📴 **Offline Spooling Queue**:
  - When Internet access is unavailable, captures are spooled locally in a FIFO directory (500 MB limit).
  - Automatically flushes and purges upon network reconnection.
- 🌐 **Embedded Web Viewer**:
  - Self-hosted dark-theme dashboard with date range filters, machine selectors, batch deletion, and fullscreen lightbox modal.

---

## 2. 🛡️ Anti-Tampering & Security Requirements

For optimal protection against bypass or tampering:
1. **Child User Account**: The child must use a **Standard User** OS account (not Administrator / sudoer).
2. **Parent Account**: The parent holds the Administrator account with exclusive privileges to inspect or alter system files.

---

## 3. ☁️ Cloud Storage Setup (Cloudflare R2 / AWS S3)

### Option A: Cloudflare R2 (Recommended - Free 10GB/Month)
1. Log in to [Cloudflare Dashboard](https://dash.cloudflare.com/) and navigate to **R2**.
2. Click **Create bucket** and name it (e.g. `watcher-screenshots`).
3. Under **Manage R2 API Tokens**, click **Create API Token**:
   - Permissions: **Object Read & Write**
   - Bucket: Select your bucket (`watcher-screenshots`)
4. Copy the credentials:
   - **Access Key ID**
   - **Secret Access Key**
   - **Endpoint**: `https://<ACCOUNT_ID>.r2.cloudflarestorage.com`
5. Place them in `credentials.json` in the application directory:
   ```json
   {
     "bucket": "watcher-screenshots",
     "region": "auto",
     "accessKey": "YOUR_ACCESS_KEY_ID",
     "secretKey": "YOUR_SECRET_ACCESS_KEY",
     "endpoint": "https://<ACCOUNT_ID>.r2.cloudflarestorage.com"
   }
   ```

### Option B: Amazon AWS S3
1. Create an S3 Bucket in [AWS Console](https://console.aws.amazon.com/) (e.g., `my-watcher-bucket`).
2. Create an IAM user with `AmazonS3FullAccess` or restricted bucket permissions.
3. Generate Access Keys and populate `credentials.json`:
   ```json
   {
     "bucket": "my-watcher-bucket",
     "region": "us-east-1",
     "accessKey": "AKIAXXXXXXXXXXXXXXXX",
     "secretKey": "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY",
     "endpoint": ""
   }
   ```
   *(Leave `endpoint` blank for standard AWS S3).*

---

## 4. 🔨 Building the Project

### Prerequisites
- **Java Development Kit (JDK) 17+**
- **Apache Maven 3.9+**

### Compile & Package
```bash
# Build shaded executable Fat JAR (skipping tests)
mvn clean package -DskipTests

# Run full test suite and package
mvn clean package
```

The compiled binary will be placed at:
```text
target/syshealthd.jar
```

---

## 5. 🚀 Installation & Deployment

Deploying the service requires copying three files to the target machine:
1. `syshealthd.jar`
2. `credentials.json`
3. Platform-specific install script (`install-windows.bat`, `install-mac.sh`, or `install-linux.sh`).

### A) Windows:
1. Copy the files to the machine.
2. Right-click `install-windows.bat` and select **Run as Administrator**.
3. The script automatically:
   - Installs files into `C:\ProgramData\SystemHealth`.
   - Restricts read/modify permissions using `icacls`.
   - Adds automatic startup entry in Windows Registry.

### B) macOS:
1. Open Terminal in the deployment directory.
2. Run installation with superuser permissions:
   ```bash
   sudo chmod +x install-mac.sh
   sudo ./install-mac.sh
   ```
3. **Screen Recording Permission**: Go to **System Settings -> Privacy & Security -> Screen Recording** and grant permission to Java.

### C) Linux:
1. Run installation script:
   ```bash
   sudo chmod +x install-linux.sh
   sudo ./install-linux.sh
   ```
2. Automatically registers with XDG Autostart for the graphical user session.

---

## 6. 🎮 Manual Control & Verification Scripts

Control scripts are available in `scripts/` and `dist-windows/`:

| Action | Windows | macOS | Linux |
|---|---|---|---|
| **Start in background** | `.\scripts\start-windows.bat` | `./scripts/start-mac.sh` | `./scripts/start-linux.sh` |
| **Check running status** | `.\scripts\status-windows.bat` | `./scripts/status-mac.sh` | `./scripts/status-linux.sh` |
| **Stop service cleanly** | `.\scripts\stop-windows.bat` | `./scripts/stop-mac.sh` | `./scripts/stop-linux.sh` |
| **Combined manager** | `.\scripts\manage-windows.bat {start\|status\|stop}` | `./scripts/manage-mac.sh {start\|status\|stop}` | `./scripts/manage-linux.sh {start\|status\|stop}` |

> ⚠️ Always use `stop-*.bat` / `stop-*.sh` to stop the daemon, as it terminates the watchdog supervisor before killing the daemon process.

---

## 7. 🌐 Built-in Web Viewer Dashboard

The daemon includes an integrated HTTP web viewer allowing parents to browse and manage cloud screenshots directly from their browser:

### Launching the Web Viewer
```bash
# Using CLI directly
java -jar target/syshealthd.jar -p "SecretParent123" --viewer --viewer-port 8080

# Using Windows helper script
.\scripts\viewer-windows.bat "SecretParent123" 8080
```

### Dashboard URL
👉 **[http://localhost:8080](http://localhost:8080)**

### Dashboard Capabilities:
- 📅 **Date Range Filter**: Filter screenshots across start and end dates.
- 🖥️ **Device Selector**: View captures for a specific machine or aggregate all devices.
- ☑️ **Batch Selection & Deletion**: Select multiple images to permanently delete from cloud storage.
- 🔍 **Lightbox Modal**: Inspect captures in full resolution with single delete, download, and arrow navigation.

---

## 8. 🛠️ Administration CLI

The daemon runs silently with no desktop window. Configure and inspect it using the password-protected CLI:

```bash
java -jar syshealthd.jar -p <PASSWORD> [OPTIONS]
```

### Options Reference:

| Option | Description | Example |
|---|---|---|
| `-p`, `--password` | **Required**: Admin password for decrypting settings | `-p "SecretParent123"` |
| `-s`, `--status` | Print status, queue statistics, and active config | `-s` |
| `-i`, `--set-interval` | Set capture interval in minutes (min: 1) | `-i 5` |
| `-q`, `--set-quality` | Set JPEG compression quality (`0.1` to `1.0`) | `-q 0.6` |
| `-r`, `--set-retention` | Set cloud retention period in days | `-r 30` |
| `-b`, `--set-bucket` | Set S3 bucket name | `-b "my-bucket"` |
| `--set-region` | Set S3 region (e.g. `us-east-1`, `auto`) | `--set-region "auto"` |
| `--set-endpoint` | Set S3 custom endpoint override (Cloudflare R2 / MinIO) | `--set-endpoint "https://<id>.r2.cloudflarestorage.com"` |
| `-c`, `--change-password` | Update admin password | `-c "NewPassword456"` |
| `-v`, `--viewer` | Launch local web viewer dashboard | `-v` |
| `--viewer-port` | Port for the web viewer (default: 8080) | `--viewer-port 8080` |
| `--uninstall` | Wipe local spool, credentials, and configuration | `--uninstall` |

---

## 9. 🔄 Automatic Retention Cleaner

The `RetentionCleanerService` background component automatically manages screenshot lifecycle:
1. **Periodic Evaluation**: Runs 1 minute after service boot, then every 24 hours.
2. **Cloud S3 / R2 Purge**: Identifies date folders (`YYYY-MM-DD/`) older than the retention threshold (default: 30 days) and deletes expired objects.
3. **Local Cleanup**: Spooled local images are immediately purged upon upload. Stale queue items are pruned if offline for over 30 days.

---

## 10. 📊 Data Consumption Estimates

| Interval | Captures / Hour | Daily Size (8 hours) | Monthly Size (30 days) | Cloudflare R2 Cost |
|---|---|---|---|---|
| **Every 10 min** (Default) | 6 captures | ~7.2 MB | **~216 MB** | **$0.00** (Free tier) |
| **Every 5 min** | 12 captures | ~14.4 MB | **~432 MB** | **$0.00** (Free tier) |
| **Every 3 min** | 20 captures | ~24.0 MB | **~720 MB** | **$0.00** (Free tier) |

*Calculated with ~150 KB per 1080p capture at quality 0.6.*

---

## 11. 📂 Project Structure

```text
watcher/
├── README.md                                    # Primary English documentation
├── README.ar.md                                 # Arabic documentation (دليل الاستخدام)
├── credentials.json                             # S3 / Cloudflare R2 credentials (user created)
├── credentials.example.json                     # Template credentials file
├── pom.xml                                      # Maven build configuration
├── dist-windows/                                # Ready-to-deploy Windows bundle
├── scripts/                                     # Cross-platform administration scripts
│   ├── install-windows.bat / install-mac.sh / install-linux.sh
│   ├── start-windows.bat / start-mac.sh / start-linux.sh
│   ├── status-windows.bat / status-mac.sh / status-linux.sh
│   ├── stop-windows.bat / stop-mac.sh / stop-linux.sh
│   └── viewer-windows.bat / viewer-mac.sh
└── src/
    ├── main/
    │   ├── java/com/watcher/
    │   │   ├── App.java                         # Service main entrypoint
    │   │   ├── cli/AdminCli.java                # Password-protected admin CLI
    │   │   ├── capture/ScreenCapture.java       # Multi-monitor capture engine
    │   │   ├── storage/S3Uploader.java          # S3 / Cloudflare R2 client
    │   │   ├── storage/CloudRetentionCleaner.java # S3 lifecycle cleaner
    │   │   ├── viewer/ViewerServer.java         # Embedded web viewer server
    │   │   └── protection/Watchdog.java         # Mutual watchdog supervisor
    │   └── resources/
    │       └── viewer.html                      # Single-page web dashboard
    └── test/java/com/watcher/                   # JUnit 5 & Mockito test suite
```

---

## 📜 License & Ethical Use

This software is strictly intended for parental supervision and academic monitoring of minors by their legal guardians. Users must comply with all relevant local computer privacy and monitoring laws.
