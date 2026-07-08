# SmartFuel Android

Native Android client built with Kotlin and Jetpack Compose.

The development build calls:

```text
http://127.0.0.1:4000
```

For a real phone connected over USB, run:

```bash
adb reverse tcp:4000 tcp:4000
```

Then install the debug APK.

The app caches the last successful dashboard response in `SharedPreferences` and shows it if the backend is temporarily unavailable.
