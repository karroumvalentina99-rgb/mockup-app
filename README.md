# Mockup Upload (Android)

An Android app that sends screenshots to your Mockup Studio gallery — the mobile
counterpart of the "Mockup Screenshot" Chrome extension.

## How you use it

Take a normal screenshot on your phone, tap **Share → Mockup Upload**, adjust the
file name if you like, and hit **Upload**. The image lands in the gallery and its
URL is copied to your clipboard.

Open the app and use **Settings** to point it at your server and fill in the
connection fields, then tap **Test connection** to confirm it works.

## Building the APK (no Android Studio needed)

This repo builds itself in the cloud via GitHub Actions.

1. Push this folder to a GitHub repository.
2. GitHub → **Actions** tab → the **Build APK** run (starts on every push; can also
   be triggered manually with **Run workflow**).
3. When it's green, open the run → **Artifacts** → download **`mockup-upload-apk`**.
   Inside is `app-debug.apk`.

### Install on your phone

1. Transfer `app-debug.apk` to the phone (Google Drive, email, or USB).
2. Tap it and allow installing from this source when prompted.
3. Install. The app appears as **Mockup Upload** and in every image **Share** sheet.

The APK is debug-signed, which is fine for personal/internal installs.

## Building locally (with Android Studio)

Open the folder in Android Studio and **Run**, or from a terminal with the Android
SDK + JDK 17 installed:

```bash
gradle assembleDebug
```

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`.
