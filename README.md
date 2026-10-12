# WiDeck

WiDeck is a personal Stream Deck-style remote for controlling a Windows laptop from an Android phone over the same Wi-Fi network.

## User flow
1. Start the Windows host and keep it running. It displays the laptop's local IP address and a pairing PIN.
2. Open WiDeck on Android, enter the IP and PIN, and connect.
3. After pairing, search the discovered Windows apps and select up to five.
4. The phone deck stays in landscape with a 3-column × 2-row grid: five app keys and a dedicated Settings key.
5. Settings includes volume up/down, mute, change selected apps, and disconnect/reconnect.

## App discovery
The Windows host scans Start Menu and Desktop shortcuts and queries `Get-StartApps` for apps registered with Windows, including supported Microsoft Store apps such as Spotify. The Android picker supports live search so users can find an app in a long list. Store apps are launched through their registered AppsFolder ID.

Some portable apps and apps without a Windows Start registration or shortcut may still need a shortcut added manually.

## Project layout
- `windows/`: Python desktop host and local HTTP API.
- `android/`: Native Android Java app.
- `.github/workflows/build.yml`: CI builds for the APK and EXE and the `v1.0.1` release.

## Build/release
Pushing to `main` builds the Android APK and Windows EXE in GitHub Actions. After both builds succeed, the workflow publishes the `v1.0.1` GitHub Release with versioned APK/EXE assets and creates the matching Git tag.

## Security
Designed for a trusted home LAN only. Do not port-forward the host port to the internet. The pairing PIN/token do not encrypt network traffic.
