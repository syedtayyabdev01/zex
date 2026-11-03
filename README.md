# ZEX Android V0.2

ZEX is a lightweight Android WebView app for `https://zxcstream.com/`, with a red ZEX identity and a translucent glass control bar. The app respects the site's own sign-in, playback, DRM, provider, and access rules.

## Android and Android TV

- One APK supports phones, tablets, and Android TV. TV launch and touchscreen-optional support are declared in the manifest.
- Use a TV remote's directional pad to focus the glass controls and the site's own controls. Play/pause media keys are passed to the page's active video element.
- The app bar provides Home, Reload, and Back controls with visible remote focus treatment.
- Web video fullscreen is supported. HTTPS-only navigation stays within `zxcstream.com`; other HTTPS links are handed to the system browser.
- In the site's "Stay Connected" popup, ZEX keeps the existing Discord and Telegram links and replaces the Facebook button with the ZEX GitHub repository.
- A low-power daily reminder and GitHub release check run on a 24-hour WorkManager interval. A newer release gets one update alert; installing an app update also shows a confirmation notification. Android may defer daily background work, and notifications require the user's notification permission.

## Build

Requirements: JDK 17, Android SDK Platform 35, and Android Build Tools 35.x.

```powershell
.\gradlew.bat assembleDebug
.\gradlew.bat assembleRelease
```

The debug APK is `app/build/outputs/apk/debug/app-debug.apk`. GitHub Actions builds the APK on pushes and publishes a downloadable APK when the `v0.2` tag is pushed. Unsigned release APKs are for sideloading/testing; sign with your own private keystore before distribution.

## Install on Android TV

Download the debug APK from the GitHub Actions run (or the release APK), then install it using Android Studio/ADB or a trusted sideloading method. In the app, use the TV remote D-pad for navigation. Playback and provider support depend on the website and its embedded players.

## Security and privacy

- Only HTTPS is permitted; cleartext traffic and mixed content are disabled.
- JavaScript and DOM storage are enabled for the website. Cookies are retained by WebView for site sessions.
- File/content access and app backups are disabled. No credentials or signing keys belong in this repository.
- Glass panels use a translucent gradient and thin highlight border without live blur or continuous animation, keeping rendering lightweight.

## Version

Application label: **ZEX Android V0.2** · `versionName`: **0.2** · `versionCode`: **2**
