# QuestPhone

> [!NOTE]
> **Fork Information**: This `main` branch is a customized, lightweight fork of QuestPhone. It has been stripped of default server, cloud sync, and backend integrations to allow for streamlined, private offline experimentation and local improvements.
> 
> If you are looking for the full project codebase containing the clean, fully-featured, and upstream-compatible contribution set (including parallel sync fixes, AppBlockerService battery optimizations, and the inline engine selection UI), please refer to the **[clean-pr](https://github.com/Afrasyaab-GH/QuestPhone/tree/clean-pr)** branch.

**QuestPhone** is a powerful, open-source Android productivity utility and minimal launcher designed to help users combat screen addiction by gamifying habit-forming and app-blocking workflows. Distracting apps remain blocked until you successfully complete your real-life quests.

> [!CAUTION]
> This project is experimental and under active development.

---

## 🚀 Key Features

- **Minimalist Launcher**: Minimizes screen clutter and keeps your focus where it belongs.
- **Gamified Habits**: Level up, build streaks, earn coins, collect objects, and track progress.
- **Strict Real-Life Verification**: Uses hardware-accelerated local AI or external integrations to verify that you actually completed your quests.
- **On-Device Privacy First**: Local-first architecture with optional secure end-to-end sync.
- **AES-256 Backup Encryption**: Export and import your data with high-grade PBKDF2 password-derived AES-256-CBC encryption.
- **Habit Privacy Mode**: Hide sensitive quest titles (e.g. medical or personal habits) behind a `"🔒 Hidden Quest"` mask, unlocked via Android Biometrics (Fingerprint/Face/PIN/Pattern).
- **Background Auto-Backups**: Periodically saves local rolling backups of your profile and quests in the background via Android `WorkManager`.
- **Sync Migrator**: Seamlessly uploads and merges offline anonymous statistics and quests to your online account upon registration or login.

---

## 📸 Screenshots

Click on any image to enlarge.

<table>
  <tr>
    <td><img src='fastlane/metadata/android/en-US/images/phoneScreenshots/1.png' width='120'></td>
    <td><img src='fastlane/metadata/android/en-US/images/phoneScreenshots/2.png' width='120'></td>
    <td><img src='fastlane/metadata/android/en-US/images/phoneScreenshots/3.png' width='120'></td>
    <td><img src='fastlane/metadata/android/en-US/images/phoneScreenshots/4.png' width='120'></td>
    <td><img src='fastlane/metadata/android/en-US/images/phoneScreenshots/5.png' width='120'></td>
    <td><img src='fastlane/metadata/android/en-US/images/phoneScreenshots/6.png' width='120'></td>
  </tr>
</table>

---

## 🛠️ Tech Stack & Architecture

- **UI & State**: Jetpack Compose, Material Design 3, Dagger Hilt.
- **Database & Sync**: Room (SQLite), Supabase Auth & Database Sync.
- **On-Device AI Engine**:
  - **SentencePiece JNI**: Custom JNI native C++ wrapper compiled via CMake.
  - **SigLIP ONNX Runtime**: Local zero-shot vision classification for image features.
  - **Gemini Nano (AICore)**: Local LLM reasoning utilizing Google's system-level `AICore` client API.
  - **Fallback Chain**: Local Gemini Nano ➔ Private Gemini API Key ➔ Cloud Server Validation ➔ Local SigLIP.

---

## 📦 Build & Development Instructions

### Prerequisites
1. **JDK 17** (OpenJDK 17 recommended)
2. **Android SDK** (API 34)
3. **NDK & CMake** (Install via Android Studio SDK Manager)

### Build Commands

To build and compile both build variants:

```powershell
# Compile Kotlin and generate release APKs
.\gradlew.bat assemblePlayDebug assembleFdroidDebug
```

### Build Gotchas & Fixes

> [!TIP]
> **Windows Gradle Multi-Drive Root Limitation**:
> If your project resides on a secondary drive (e.g. `D:`) while the default Gradle cache folder is on `C:`, KSP will throw a `different roots` exception. Bypass this by setting the Gradle user home directory on the same drive:
> ```powershell
> .\gradlew.bat --gradle-user-home D:\PROJECTS\QuestPhone\.gradle_home assemblePlayDebug assembleFdroidDebug
> ```

> [!NOTE]
> **JNI Symlink Limitation on Windows**:
> Standard Git checkouts on Windows map symlinks as text redirect files. The build's JNI configuration bypasses this by compiling SentencePiece sources directly from their actual directories:
> [CMakeLists.txt](file:///d:/PROJECTS/QuestPhone/ai/src/main/cpp/CMakeLists.txt) references `src/` and `src/builtin_pb/` explicitly to ensure correct native builds.

---

## 📄 License

Licensed under the [Apache License 2.0](LICENSE).
