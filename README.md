# Offline Tracker (Android)

Offline-first calorie and weight tracking app. Two Gradle modules:

- `:logic` — pure Kotlin, no Android/SDK dependency. Rolling calorie/weight
  averages, plateau detection, and a dependency-free `.xlsx` writer (no Apache
  POI — it writes the OOXML zip directly, since POI is unreliable on Android's
  runtime). Fully unit tested; runs with plain Gradle/JVM.
- `:app` — the Android app itself: Room database, four screens (Log Food, Log
  Weight, Dashboard, Settings), custom Canvas trend charts (no chart library
  dependency), and XLSX/PDF export through the system share sheet.

## Building

This needs a real Android SDK (compileSdk 34, build-tools) on the machine or
in `ANDROID_HOME` — that's not something Gradle can substitute for, and it
could not be installed in the sandbox this project was authored in (see
below). With an SDK available:

```
./gradlew :app:assembleDebug
```

The debug APK lands in `app/build/outputs/apk/debug/app-debug.apk`.

To run just the tested, Android-independent business logic (works anywhere
with a JDK, no SDK needed):

```
./gradlew :logic:test
```

## Why this wasn't compiled to an APK in-session

The sandbox this project was built in blocks `dl.google.com` (and
`android.googlesource.com`, `jitpack.io`) at the network egress policy level.
That host is where both the Android Gradle Plugin and the SDK platform/
build-tools packages are distributed from, so neither can be fetched here —
confirmed by `./gradlew :app:tasks` failing to even resolve the
`com.android.application` plugin. Everything else needed to build (Gradle
itself, Kotlin, AndroidX/Room from Maven Central and Google's Maven mirror)
was reachable.
