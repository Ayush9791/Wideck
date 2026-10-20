# WiDeck

WiDeck turns an Android phone into a personal Stream Deck-style remote for a Windows laptop over the same Wi-Fi network.

## What it does

- Pair your Android phone with the Windows host using the host IP and a one-time PIN.
- Search installed Windows apps and choose up to five app shortcuts for a landscape 3×2 deck.
- Customize app tile labels, colours, and order by long-pressing a tile.
- Create macro buttons that launch multiple selected apps and optionally open a website in one tap; rename, recolour, reorder, or delete macros.
- Launch selected apps from the phone.
- Control media playback, volume, and mute from Settings.
- Keep your chosen deck and host address saved between sessions.
- Check GitHub Releases for newer Android versions and show an in-app update banner with an **Update** button. The app downloads the APK and opens Android's package installer; Android still requires the user to approve installation.

## Install and pair

1. Download the Android APK and Windows ZIP from the [GitHub Releases page](https://github.com/Ayush9791/Wideck/releases).
2. Extract the complete Windows ZIP and run `WiDeck.exe` from the extracted `WiDeck` folder. Keep the adjacent `_internal` directory and files together.
3. Keep the Windows host open. It displays the laptop's local IP address and pairing PIN.
4. Open WiDeck on Android, enter the IP and PIN, and connect.
5. Choose up to five apps. Both devices must be connected to the same Wi-Fi network.

## Automatic Android updates

When a newer published GitHub Release contains an asset named `WiDeck-Android-<version>.apk`, WiDeck checks it when the app opens and shows an update banner. Tapping **Update** downloads the APK and opens Android's installer. Android does not permit silent installs for an ordinary sideloaded app; the user must confirm the update.

### One-time signing setup required for reliable updates

Android only installs an update over an existing app when the new APK is signed with the same signing certificate as the installed APK. The earlier debug builds were not configured with a persistent release key, so an update from those builds may require a one-time uninstall/reinstall. App data may be lost during that migration.

For stable future updates, create a private Android release keystore and add these four GitHub Actions repository secrets under **Settings → Secrets and variables → Actions**:

- `ANDROID_KEYSTORE_BASE64`: Base64-encoded keystore file.
- `ANDROID_KEYSTORE_PASSWORD`: Keystore password.
- `ANDROID_KEY_ALIAS`: Key alias.
- `ANDROID_KEY_PASSWORD`: Key password.

Keep a secure offline backup of the keystore. If it is lost or replaced, Android will reject updates signed with the new key. Never commit the keystore or its passwords to the repository. Once the secrets are configured, the workflow signs release APKs with the same key on future releases.

To create the keystore locally with Java's `keytool`:

```bash
keytool -genkeypair -v -keystore wideck-release.jks -alias wideck -keyalg RSA -keysize 2048 -validity 10000
```

To produce the base64 value on Linux/macOS:

```bash
base64 -w 0 wideck-release.jks
```

On Windows PowerShell:

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("wideck-release.jks"))
```

Store that output as `ANDROID_KEYSTORE_BASE64`, then set the other three secrets. Do not share the keystore publicly.

## Build and release

- Pushing to `main` or opening a pull request builds Windows and Android artifacts for verification. Builds do not overwrite or create a release.
- Publishing a GitHub Release triggers a build and attaches versioned Windows and Android assets to that release.
- Release tags should use semantic versions such as `v1.0.3` or `v1.1.0`.
- Android's version code is derived from the numeric version (major × 10,000 + minor × 100 + patch).

## Antivirus and SmartScreen

The Windows build is unsigned. Switching from a single-file executable to a directory-based ZIP may reduce some heuristic detections, but cannot guarantee that all browsers or antivirus products will trust it. Do not blindly bypass warnings. Download only from the official repository and submit suspected false positives to the relevant security vendor. A verifiable Windows code-signing certificate is the durable way to establish publisher identity and reputation.

## Security

WiDeck is intended for a trusted home LAN. Do not port-forward the host port to the internet. The local HTTP traffic is not encrypted, so use it only on a trusted network.
