# TCL Android TV Remote

A simple ad-free Android remote for TCL TVs running Android TV / Google TV.

## Features

- Volume up / down
- Channel up / down
- Mute
- Home
- Back
- Menu / settings
- Pairing through the normal Android TV Remote Service flow

## How to build the APK with GitHub Actions

1. Create a new GitHub repository.
2. Upload every file in this folder to the repository.
3. Open the repository on GitHub.
4. Go to **Actions**.
5. Open **Build Android APK**.
6. Click **Run workflow**.
7. After it finishes, download the artifact named **tcl-android-tv-remote-debug-apk**.

## How to connect to the TV

1. Make sure the phone and TV are on the same Wi‑Fi network.
2. Enter the TV IP address in the app.
3. Tap **Start pairing**.
4. Enter the code shown on the TV.
5. Tap **Finish pairing**.
6. Tap **Connect remote**.

## Notes

- This project now uses the Android TV Remote Service protocol family used by Google TV remote apps.
- It is intended for personal use on your own TV.
- Keyboard text entry will be added after pairing and button control are confirmed on the target TV.
