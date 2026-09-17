# Nill Tracker

A dark study-time tracker with:
- Windows system-tray timer (`.exe` built by GitHub Actions)
- Android app (`.apk` built by GitHub Actions)
- One Google Sheet as the shared data store through Google Apps Script
- Offline session queue on Windows
- Today, weekly total, streak, and session history on Android

## Architecture

Windows tray app / Android app -> Google Apps Script Web App -> your Google Sheet

No Google Cloud project or Android SDK is required on your computer.

## 1. Set up the Google Sheet backend

1. Create a blank Google Sheet in the Google account you want to use.
2. Open **Extensions -> Apps Script**.
3. Replace the default code with `google-apps-script/Code.gs`.
4. Change `CONFIG.TOKEN` to a long random secret, for example 32+ characters.
5. Save.
6. Click **Deploy -> New deployment**.
7. Select **Web app**.
8. Set **Execute as:** Me.
9. Set **Who has access:** Anyone.
10. Deploy and authorize the script if Google asks.
11. Copy the Web App URL. It normally ends in `/exec`.

The script creates a `Sessions` sheet automatically.

Keep the token private. The Web App URL and token are what the two apps use.

## 2. Build everything without installing tools

Push this folder to a GitHub repository. GitHub Actions will build:
- `NillTracker-Windows.zip`
- `NillTracker-Android-debug.apk`

### Beginner GitHub steps

1. Go to https://github.com and sign in.
2. Click the **+** button -> **New repository**.
3. Repository name: `NillTracker`
4. Choose **Public** or **Private**.
5. Click **Create repository**.
6. On the new repository page, click **Add file -> Upload files**.
7. Upload every file/folder from this project, preserving the folder structure.
8. Click **Commit changes**.
9. Open the **Actions** tab.
10. Click the workflow named **Build Nill Tracker**.
11. Wait for the green check mark.
12. Open the completed workflow run.
13. Scroll to **Artifacts**.
14. Download both artifacts.

No Android Studio, JDK, Gradle, Python, or Visual Studio is needed on your PC for this cloud build.

## 3. Configure Windows

1. Extract `NillTracker-Windows.zip`.
2. Run `NillTracker.exe`.
3. Right-click the tray icon -> **Settings**.
4. Paste your Apps Script Web App URL.
5. Paste the same secret token.
6. Enter a device name such as `Windows PC`.
7. Save.
8. Left-click the tray icon to start/stop studying.

## 4. Configure Android

1. Install the APK from the GitHub Actions artifact on your Android phone.
2. Open Nill Tracker.
3. Open **Settings**.
4. Paste the same Web App URL and token.
5. Enter a device name such as `Pixel 7`.
6. Save.
7. The dashboard will load shared totals/history.

## Notes

- The Windows timer continues while the app is running in the tray.
- Windows stores unsent sessions locally and retries them when the app starts.
- Android currently keeps the active timer on-device and uploads the completed session.
- The backend stores times as ISO timestamps and duration in seconds.
- Streak means consecutive calendar days with at least one logged session, counting today if today has activity.
