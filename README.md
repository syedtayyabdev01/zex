# ZXCStream for Android

A native Kotlin Android application that opens `https://zxcstream.com/` in a
WebView. It does not bypass authentication, DRM, paywalls, provider restrictions,
or other website access controls.

## Requirements

- JDK 17
- Android SDK Platform 35 and Android SDK Build-Tools 35.x
- Gradle 8.9 (the Gradle wrapper is the recommended entry point)

Open the project in Android Studio and allow it to install the required SDK
components, or set `ANDROID_HOME`/`ANDROID_SDK_ROOT` to an installed SDK.

## Build

From the project root:

```powershell
.\gradlew.bat assembleDebug
.\gradlew.bat assembleRelease
.\gradlew.bat bundleRelease
```

Outputs are written under `app\build\outputs\apk\` and
`app\build\outputs\bundle\`. A release build made without signing credentials
is unsigned and must be signed before distribution.

## Signing a release

Create a private Android upload/release keystore and keep it outside source
control. Set these environment variables in the build shell:

```powershell
keytool -genkeypair -v -keystore 'C:\secure\zxcstream-upload.jks' -alias zxcstream -keyalg RSA -keysize 2048 -validity 10000
$env:ZXCSTREAM_STORE_FILE = 'C:\secure\zxcstream-upload.jks'
$env:ZXCSTREAM_STORE_PASSWORD = 'your-store-password'
$env:ZXCSTREAM_KEY_ALIAS = 'your-key-alias'
$env:ZXCSTREAM_KEY_PASSWORD = 'your-key-password'
```

With all four variables set, the Gradle release variants are automatically
signed. If no variables are set, release outputs are unsigned. Do not commit
credentials or keystores. Play App Signing is recommended for Google Play
distribution.

## Security and behavior

- Only HTTPS is allowed for the site; HTTP navigation to `zxcstream.com` is
  upgraded to HTTPS and cleartext networking is disabled.
- Top-level navigation remains on `zxcstream.com` and its subdomains. Other
  HTTP(S) links open with an installed external handler; unsafe schemes are
  rejected.
- JavaScript and DOM storage are enabled for the site. Cookies, including
  third-party cookies needed by embedded providers, are retained by WebView.
- App data backup and device transfer are disabled to protect session data.
- File/content access, mixed content, and unrequested permissions are disabled.
- SSL certificate errors are always cancelled.
- HTML5 video uses WebView's media support, with immersive fullscreen and
  landscape orientation when requested by the page.

## Validation checklist

Use an emulator/device to verify website loading, login/session persistence,
search and site navigation, playback/provider behavior, fullscreen video,
back navigation and exit confirmation, offline/retry behavior, slow connections,
background/foreground resume, and portrait/landscape/tablet layouts. Playback
and provider availability remain subject to the site's own support and policies.
