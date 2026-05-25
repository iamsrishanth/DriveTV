# Drive TV App

An Android TV optimized native Kotlin application that integrates directly with Google Drive to securely stream video files using a hardware-accelerated ExoPlayer (Media3) player with 1GB disk cache. Look at that D-Pad navigation support go!

## Prerequisites

Before building and running the project, you **must** obtain a Google Cloud Platform (GCP) Service Account `credentials.json` file. This app uses Service Accounts rather than standard OAuth user sign-ins down to its automated design.

### 1. Enable the Google Drive API
1. Navigate to the [Google Cloud Console](https://console.cloud.google.com/).
2. Create a **New Project** or select an existing one.
3. In the sidebar, go to **APIs & Services** > **Library**.
4. Search for "**Google Drive API**" and click **Enable**.

### 2. Create a Service Account
1. In the sidebar, go to **APIs & Services** > **Credentials**.
2. Click **+ CREATE CREDENTIALS** at the top and select **Service account**.
3. Name the service account (e.g., "DriveTV-Bot") and click **Create and Continue**, then **Done**.
4. You will be redirected back to the Credentials tab. Look for the newly created Service Account email address—**copy it** (you will need it later to share folders).

### 3. Generate your `credentials.json`
1. Under the **Service Accounts** section, click on the **Email** of the account you just created.
2. Go to the **KEYS** tab at the top.
3. Click **ADD KEY** > **Create new key**.
4. Select **JSON** and click **Create**. This will download the file to your computer.
5. Rename the downloaded file to exactly `credentials.json`.
6. Place this file inside the Android project at the following path:
   ```
   DriveTVApp/app/src/main/res/raw/credentials.json
   ```

### 4. Share your Google Drive folders with the Service Account
Service accounts are essentially "bots." They **only** see what is explicitly shared with them; they **do not** have access to *your* personal Google Drive by default!

1. Open your regular [Google Drive](https://drive.google.com/) in a browser.
2. Find the folder containing the videos you want the TV app to see.
3. Right-click the folder and select **Share**.
4. Paste the **Service Account Email Address** (from Step 2.4) into the specific people bar.
5. Set the permission to **Viewer** and click **Send**. (Uncheck "Notify people" to avoid an error, as Service Accounts don't have real inboxes).
6. **Important:** The app currently defaults to showing folders at the "root" of the Service Account. Ensure you share the top-level folder!

---

## Building and Running

### System Requirements
*   **Android Studio** Giraffe | 2022.3.1 or newer.
*   **JDK 17** integration.
*   **Minimum API:** Android TV 10 (API level 29+).

### Gradle Sync & Build
1. Open Android Studio and select **Open**. Point it to the `DriveTVApp/` directory.
2. Wait for Android Studio to index the files and download the necessary Gradle wrappers (this repository targets Gradle 8.2).
3. Verify that `DriveTVApp/app/src/main/res/raw/credentials.json` exists in the Project tool window.
4. Click the **Sync Project with Gradle Files** (elephant icon) at the top right.

### Running on an Emulator or Real Device
1. To run on an emulator, set up an **Android TV (1080p or 4K)** AVD instance in the device manager targeting at least API 29.
2. To run on a physical Android TV, enable Developer Options on the TV, enable Network Debugging (or USB Debugging), and connect to the TV via `adb connect <IP_ADDRESS>`.
3. In Android Studio, select your TV emulator/device from the device dropdown.
4. Click the green ▶️ **Run 'app'** button (or use `Shift + F10`).

## Tech Stack
*   **100% Kotlin Compose for TV** (`androidx.tv.material3`) targeting TV D-pads natively.
*   **Media3 ExoPlayer** for high-performance MP4/MKV hardware-accelerated playback on slow generic TV chips, with 1GB LRU disk caching.
*   **Repository Pattern / MVVM** using Kotlin Coroutines for safe UI states.
*   **OkHttp / manual JWT generation** (No heavy dependency on the full bulky Google Java API Client).
