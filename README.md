# Lissen - Clean Audiobookshelf Player
[![Build Lissen App](https://github.com/GrakovNe/lissen-android/actions/workflows/build_app.yml/badge.svg)](https://github.com/GrakovNe/lissen-android/actions/workflows/build_app.yml)

<p align="center"> 
  <a href="https://play.google.com/store/apps/details?id=org.grakovne.lissen"><img src="https://upload.wikimedia.org/wikipedia/commons/7/78/Google_Play_Store_badge_EN.svg" alt="Get it on Google Play" height="60"></a>&nbsp;&nbsp;&nbsp;<!--
  --><a href="https://f-droid.org/packages/org.grakovne.lissen"><img src="https://upload.wikimedia.org/wikipedia/commons/a/a3/Get_it_on_F-Droid_%28material_design%29.svg" alt="Get it on F-Droid" height="60"></a>
</p>

### Features

  * Beautiful Interface: Intuitive design that makes browsing and listening to your audiobooks easy and enjoyable.
  * Cloud Sync: Automatically syncs your audiobook progress across devices, keeping everything up to date no matter where you are.
  * Streaming Support: Stream your audiobooks directly from the cloud without needing to download them first.
  * Offline Listening: Download audiobooks to listen offline, ideal for those who want to access their collection without an internet connection.

### Screenshots

<p align="center">
  <img src="https://github.com/GrakovNe/lissen-android/raw/main/metadata/en-US/images/phoneScreenshots/1.png" alt="Screenshot 1" width="200">
  <img src="https://github.com/GrakovNe/lissen-android/raw/main/metadata/en-US/images/phoneScreenshots/2.png" alt="Screenshot 2" width="200">
  <img src="https://github.com/GrakovNe/lissen-android/raw/main/metadata/en-US/images/phoneScreenshots/3.png" alt="Screenshot 3" width="200">
  <img src="https://github.com/GrakovNe/lissen-android/raw/main/metadata/en-US/images/phoneScreenshots/4.png" alt="Screenshot 4" width="200">
</p>

### Disclaimer

Lissen is not a clone of the official Audiobookshelf app and does not aim to replicate all of its features. 
The goal of this project is to provide a minimalistic interface and a seamless experience for listening to audiobooks and podcasts.

If there’s a feature you feel is missing or would significantly improve your experience, feel free to open an issue and share your suggestion. 
While not every feature request will be implemented, all ideas are welcome and will be thoughtfully considered.

### Building

1. Clone the repository:
```
git clone https://github.com/grakovne/lissen.git
```

2. Setup the SDK into your local.properties file
```
nano local.properties
```

3. Open the project in Android Studio or build it manually
```
./gradlew assembleDebug # Debug Build
./gradlew assembleRelease # Release Build
```
5. Build and run the app on an Android device or emulator.

### Localization

Help us translate Lissen into more languages! We use [Weblate](https://hosted.weblate.org/engage/lissen/) to manage translations.

Current localization status:

<a href="https://hosted.weblate.org/engage/lissen/">
<img src="https://hosted.weblate.org/widget/lissen/android-app/multi-auto.svg" alt="Translation status" />
</a>

To contribute:
1. Visit the [Lissen translation project](https://hosted.weblate.org/engage/lissen/).
2. Sign up or log in to Weblate.
3. Start translating or reviewing existing translations for your preferred language.

### Release policy

- **Current state:**  
  - The **main branch** is the latest stable state at every moment with every feature available at that time.  
  - The dedicated [`release/stable`](https://github.com/GrakovNe/lissen-android/tree/release/stable) contains the latest stable full-feature build.  
  - The dedicated [`release/google-play-store`](https://github.com/GrakovNe/lissen-android/tree/release/google-play-store) branch contains the Google Play Store–compliant build.

- **Every release contains:**  
  - **Play Store APK** – identical to the Play Store version. It disables some non-production-ready features, such as Android Auto support.
  - **Stable APK** – fully supported and feature-equivalent.

### Demo Environment

You can connect to a demo [Audiobookshelf](https://github.com/advplyr/audiobookshelf) instance through the Lissen app:

URL: [https://demo.lissenapp.org/](https://demo.lissenapp.org/)
```
Username: demo
Password: demo
```

This instance is contains only Public Domain audiobooks from [LibriVox](https://librivox.org/)

## License
Lissen is open-source and licensed under the MIT License. See the LICENSE file for more details.
