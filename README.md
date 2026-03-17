# FakeCall

<div align="center">

<img src="https://github.com/user-attachments/assets/c36243df-659f-4ac5-8c63-a1344e8b876a" width=20% title="logo" />

<p>

[![GitHub stars](https://img.shields.io/github/stars/DDOneApps/FakeCall?style=for-the-badge)](https://github.com/DDOneApps/FakeCall/stargazers) [![GitHub forks](https://img.shields.io/github/forks/DDOneApps/FakeCall?style=for-the-badge)](https://github.com/DDOneApps/FakeCall/network) [![GitHub issues](https://img.shields.io/github/issues/DDOneApps/FakeCall?style=for-the-badge)](https://github.com/DDOneApps/FakeCall/issues)

[![GitHub license](https://img.shields.io/badge/license-GPL%20v3-red?style=for-the-badge)](LICENSE) <!-- TODO: Add a LICENSE file and update badge -->

</p>

**An open-source Android application to simulate incoming calls, featuring a modern Material 3 UI with dynamic Monet support.**


</div>

## Overview
Ever wanted to get [that Feature of old Samsung phones](https://www.youtube.com/watch?v=OKV3Eei5JNE) to simulate an incoming call with audio on the originial phone app?
Introducing FakeCall. Unlike other apps that merely mock a UI, this app integrates directly with the Android Telecom Framework to provide an indistinguishable calling experience. It has many features to make the call as real as possible.

<p align=center>
  
## Features

</p>

-  **Original Dialer:** FakeCall uses your real Phone app to simulate the incoming Call by creating [a Phone Account in android's TelecomManager](https://developer.android.com/reference/android/telecom/TelecomManager)
-  **Customizable:** The app lets you customize the name of the fake phone account in the settings to match your real service provider.
-  **Schedule:** Set exact Timers for when the call should come in
-  **Audio Support:** You can upload audio files that play when the call is answered
-  **Call History:** Simulated calls are being shown in call history
-  **IVR Mode:** assign audio files to keys and make sub-menus
-  **Recording:** record microphone audio of a Fake call

## Screenshots

![Screenshot 1](https://github.com/DDOneApps/FakeCall/blob/main/Screenshots/Screenshot_20260308-211426_Fake%20Call.png)
_Main screen_

![Screenshot 3](https://github.com/DDOneApps/FakeCall/blob/main/Screenshots/Screenshot_20260308-212114_Telefon.png)
_Call interface_

## Tech Stack

**Mobile Development:**

[![Kotlin](https://img.shields.io/badge/Kotlin-0095D5?style=for-the-badge&logo=kotlin&logoColor=white)](https://kotlinlang.org/)

[![Android](https://img.shields.io/badge/Android-3DDC84?style=for-the-badge&logo=android&logoColor=white)](https://developer.android.com/)

[![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-4285F4?style=for-the-badge&logo=jetpack%20compose&logoColor=white)](https://developer.android.com/jetpack/compose)

**Build System:**

[![Gradle](https://img.shields.io/badge/Gradle-02303A?style=for-the-badge&logo=gradle&logoColor=white)](https://gradle.org/)

**UI/UX:**

[![Material 3](https://img.shields.io/badge/Material%203-0057B7?style=for-the-badge&logo=materialdesign&logoColor=white)](https://m3.material.io/)

[![Dynamic Color](https://img.shields.io/badge/Monet-8BC34A?style=for-the-badge&logo=android&logoColor=white)](https://developer.android.com/design/color/dynamic-color)


## Star History

<a href="https://www.star-history.com/?repos=DDOneApps%2FFakeCall&type=date&legend=top-left">
 <picture>
   <source media="(prefers-color-scheme: dark)" srcset="https://api.star-history.com/image?repos=DDOneApps/FakeCall&type=date&theme=dark&legend=top-left" />
   <source media="(prefers-color-scheme: light)" srcset="https://api.star-history.com/image?repos=DDOneApps/FakeCall&type=date&legend=top-left" />
   <img alt="Star History Chart" src="https://api.star-history.com/image?repos=DDOneApps/FakeCall&type=date&legend=top-left" />
 </picture>
</a>

##  Project Structure

```
FakeCall/
├── .github/          # GitHub related files (e.g., issue templates, workflows) - TODO: If applicable
├── .idea/            # IntelliJ/Android Studio project configuration files
├── app/              # Main Android application module
│   ├── build.gradle.kts # Module-level Gradle build script (Kotlin DSL)
│   ├── src/          # Source code and resources
│   │   ├── main/     # Main source set (Kotlin code, resources, manifest)
│   │   └── androidTest/ # Android UI test source set
│   └── ...           # Other module files
├── build.gradle.kts  # Top-level Gradle build script (Kotlin DSL)
├── gradle/           # Gradle wrapper files
├── gradlew           # Gradle wrapper script (for Unix/macOS)
├── gradlew.bat       # Gradle wrapper script (for Windows)
├── gradle.properties # Global Gradle properties
├── settings.gradle.kts # Gradle settings file (defines project modules)
├── .gitignore        # Specifies intentionally untracked files to ignore
└── README.md         # This README file
```

## Contributing

We welcome contributions to FakeCall!


## License

This Project is licenced under GNU General Public License.
Read it [Here](https://raw.githubusercontent.com/DDOneApps/FakeCall/refs/heads/main/LICENSE)

## Acknowledgments

-   AI for helping me with my poor kotlin knowledge.
-   [NLL Apps](https://github.com/NLLAPPS) for telling me how this project could be implemented. ([Reddit](https://www.reddit.com/r/fossdroid/comments/1rj26ty/foss_alternative_to_fake_call_app_using_real/))

## Support

-   If you encounter any bugs or have feature requests, please open an issue on [GitHub Issues](https://github.com/DDOneApps/FakeCall/issues).

---

<div align="center">

**⭐ Star this repo if you find it helpful!**

Made with ❤️(and AI 🤖)

</div>

