# WiDeck

WiDeck is a personal Stream Deck-style remote for controlling a Windows laptop from an Android phone over the same Wi-Fi network.

## User flow
1. Start the Windows host and keep it running. It displays the laptop's local IP address and a pairing PIN.
2. Open WiDeck on Android, enter the IP and PIN, and connect.
3. After pairing, search the discovered Windows apps and select up to five.
4. The phone deck stays in landscape with a 3-column × 2-row grid: five app keys and a dedicated Settings key.
5. Settings includes volume up/down, mute, change selected apps, and disconnect/reconnect.

## App discovery
The Windows host scans Start Menu and Desktop shortcuts and queries `Get-StartApps` for apps registered with Windows, including supported Microsoft Store apps such as Spotify. The Android picker supports live search. Store apps are launched through their registered AppsFolder ID.

Some portable apps and apps without a Windows Start registration or shortcut may need a shortcut added manually.

## Downloading the Windows build
Download `WiDeck-Windows-v1.0.2.zip` from the official [GitHub Releases](https://github.com/Ayush9791/Wideck/releases). Extract the entire ZIP to a folder, then run `WiDeck.exe` inside the extracted `WiDeck` folder. Keep the adjacent `_internal` folder and files together with the EXE.

### Antivirus and SmartScreen
WiDeck is an unsigned personal utility that runs a local HTTP server and sends media/app-launch commands. Some browser reputation systems or antivirus heuristics may warn about unsigned Python-packaged executables. Version 1.0.2 switches from a PyInstaller one-file executable to a directory-based package, which can reduce some heuristic detections but cannot guarantee that all scanners will trust it. The Windows build is not code-signed, and no warning should be bypassed blindly. Download only from this repository, inspect the file, and submit suspected false positives to the vendor that flagged it.

To establish publisher reputation and materially improve SmartScreen trust, the Windows executable needs a verifiable code-signing certificate and a signed release process. Packaging changes alone cannot create that trust.

## Project layout
- `windows/`: Python desktop host and local HTTP API.
- `android/`: Native Android Java app.
- `.github/workflows/build.yml`: CI builds for the APK and Windows ZIP.

## Build/release
Pushing to `main` builds the Android APK and a directory-based Windows package. After both builds succeed, GitHub Actions publishes the `v1.0.2` GitHub Release.

## Security
Designed for a trusted home LAN only. Do not port-forward the host port to the internet. The pairing PIN/token do not encrypt network traffic.
