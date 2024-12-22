# Project update 22 Dec '24

Hi everyone!

A huge thank you to everyone who uses and contributes to this app—I’m amazed nearly 600 people use Lissen every day!

I have a New Year and Christmas tradition of visiting a new country each year, and this time I’m going to Seoul. I’ll be away from December 28 to January 10 and won’t be able to respond or work on the app during that period. I’ll be back in January to tackle any pending issues.

Happy holidays, whatever you’re celebrating!


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

### Demo Environment

You can connect to a demo [Audiobookshelf](https://github.com/advplyr/audiobookshelf) instance through the Lissen app:

```
URL: https://demo.lissenapp.org

Username: demo
Password: demo
```

This instance contains only Public Domain audiobooks from [LibriVox](https://librivox.org/) and is intended solely for demonstrating the client’s functionality.

## License
Lissen is open-source and licensed under the MIT License. See the LICENSE file for more details.
