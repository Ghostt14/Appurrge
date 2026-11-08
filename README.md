# Appurrge

Select many apps and uninstall them in one go. No ads, no tracking, no bloat.

Appurrge started from the code of [Batch Uninstaller](https://github.com/sarbajitsaha/Batch-Uninstaller)
by Sarbajit Saha and is now its own project. It stays under the same license (GPL-3.0, see `LICENSE`).

## Features
- Opens fast: the last list is cached, and on a first run the newest apps appear before the rest finish loading.
- **User / System** tabs, **search** (left of the top bar) and **sort** (right of the top bar, always reachable).
- Draggable fast-scroll bar (built in, no library); its popup shows a letter, a month or a size depending on the sort.
- A **Size** slider in the sort dialog changes how big the rows are, live.
- Selection is kept when you refresh, search or switch tabs.
- Silent uninstall through **Shizuku** or **root**. Without either, Android's own dialog is used (user apps only).
- With system apps selected, the button reads **ROOT ACCESS** until Shizuku (or root) is connected.
- Search and sort sit together on the right of the top bar, so both work while apps are selected.

System apps are removed for your user only (`pm uninstall -k --user 0`). That is reversible with
`cmd package install-existing <package>` and never deletes files under `/system`.

## Build
JDK 17 and the Android SDK (platform 33):

```
./gradlew testDebugUnitTest assembleRelease
```

The GitHub Actions workflow does this on every push and uploads the APK. Debug and release builds are shrunk the same way,
so both are small. The release APK is signed with the debug key so it can be installed; it is not meant for a store.

Android 6.0+ (Shizuku's minimum). Uses [Shizuku-API](https://github.com/RikkaApps/Shizuku-API) (Apache-2.0).
No AppCompat or Material library: plain Android views and a built-in scroller.
