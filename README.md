# WorkPlace Camera

WorkPlace Camera is a lightweight Android camera application built for taking workplace or sample photos quickly, tagging them with a batch name, and saving them in a dedicated gallery folder for easy review and sharing.

## APK Location

The current APK generated for this project is located at:

- app/build/outputs/apk/debug/app-debug.apk

This is a debug APK build and is ready to be installed on an Android device for testing.

## Features

- Camera preview with live focus and zoom support
- Torch/flash toggle for low-light photo capture
- Multiple aspect ratio modes: 4:3, 1:1, and 16:9
- Batch name field to label captured images
- Automatic timestamp and optional batch tag overlay on each photo
- Saves images into the device gallery under Pictures/WorkPlace
- In-app photo gallery viewer for browsing saved images
- Share and delete options inside the gallery viewer
- Works with volume buttons as a quick shutter shortcut

## Why This App

This application is useful for workplace documentation, sample capture, inspection images, and quick daily photo logging. It helps you take and organize images in a simple flow without needing a complicated camera app.

## Installation on Android Devices

Follow these steps to install the app on a device:

1. Copy the APK file from your project folder to the Android device.

   - Source path: app/build/outputs/apk/debug/app-debug.apk
2. Open the APK file on the device using the file manager or browser.
3. If your Android device asks for permission, allow installation from unknown sources.

   - Go to Settings > Security or Privacy > Install unknown apps
   - Enable permission for the app you are using to open the APK file
4. Tap Install.
5. After installation is complete, tap Open to launch the app.
6. The app will request camera permission. Grant it so the camera can work.
7. Once the permission is granted, the camera preview will open and you can start capturing photos.

## Step-by-Step Usage

1. Open the app.
2. Allow Camera permission.
3. Choose an aspect ratio if needed.
4. Enter a batch name in the text field for tagging your images.
5. Tap the capture button or use the volume buttons to take a photo.
6. The image is automatically saved in Pictures/WorkPlace.
7. Tap the gallery thumbnail to review, delete, or share saved photos.

## Build from Source

If you want to build the APK yourself from the project source, run the following command in the project root:

```bash
./gradlew assembleDebug
```

After the build completes, the APK will be generated at:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Requirements

- Android device with a rear camera
- Android 9 or newer recommended
- Camera permission enabled

## License

This project is licensed under the MIT License. Please see the [LICENSE](LICENSE) file for details.

## Project Ownership

This project remains the intellectual property of the project owner while allowing others to use, modify, and install it under the terms of the license.
