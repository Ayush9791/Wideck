# WiDeck

WiDeck is a personal Stream Deck-style remote for launching Windows applications and controlling media volume from an Android phone over a shared Wi-Fi network.

## User flow
1. Start the Windows host and keep it running. It shows the laptop's local IP address and a pairing PIN.
2. Open WiDeck on Android, enter the IP and PIN, and connect.
3. After pairing, choose up to five Windows applications from discovered Start Menu/Desktop shortcuts.
4. The phone opens a landscape 3-column × 2-row deck. Five slots are for selected apps and the sixth is Settings.
5. Settings provides volume controls, app selection, and connection controls.

## Project layout
- `windows/`: Python desktop host and local HTTP API.
- `android/`: Native Android Java app.
- `.github/workflows/build.yml`: GitHub Actions build and tag-based release workflow.

## Release builds
Pushing a version tag such as `v1.0.0` runs Android APK and Windows EXE builds. If both builds succeed, GitHub Actions creates a GitHub Release and attaches versioned `.apk` and `.exe` files.

## Security
Designed for a trusted home LAN only. Do not port-forward the host port to the internet. The pairing PIN/token do not encrypt network traffic.
