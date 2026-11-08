# Appurrge

Select many apps and uninstall them in one go. No ads, no tracking, no bloat.

Appurrge started from the code of [Batch Uninstaller](https://github.com/sarbajitsaha/Batch-Uninstaller)
by Sarbajit Saha and is now its own project. It stays under the same license (GPL-3.0, see `LICENSE`).

## Features
- Opens fast: the last list is cached, and on a first run the newest apps appear before the rest finish loading.
- **User / System** tabs, **search** (left of the top bar) and **sort** (right of the top bar, always reachable).
- Draggable fast-scroll bar; its popup shows a letter, a month or a size depending on the sort.
- Selection is kept when you refresh, search or switch tabs.
- Silent uninstall through **Shizuku** or **root**. Without either, Android's own dialog is used (user apps only).
- With system apps selected, the button reads **ROOT ACCESS** until Shizuku (or root) is connected.

System apps are removed for your user only (`pm uninstall -k --user 0`). That is reversible with
`cmd package install-existing <package>` and never deletes files under `/system`.

## Build
JDK 17 and the Android SDK (platform 33):

```
./gradlew testDebugUnitTest assembleRelease
```

The GitHub Actions workflow does this on every push and uploads two APKs: the small release build and a debug
build as a fallback. The release APK is signed with the debug key so it can be installed; it is not meant for a store.

Android 6.0+ (needed by Shizuku). Uses [Shizuku-API](https://github.com/RikkaApps/Shizuku-API) and
[AndroidFastScroll](https://github.com/zhanghai/AndroidFastScroll), both Apache-2.0.
