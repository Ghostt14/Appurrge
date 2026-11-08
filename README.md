# Batch Uninstaller

Select many apps and uninstall them in one go. No ads, no tracking.

This was inspired by
[sarbajitsaha/Batch-Uninstaller](https://github.com/sarbajitsaha/Batch-Uninstaller).

## What stands out

**Faster**
- Opens straight into the list (no splash activity) and loads apps off the main thread.
- One PackageManager query instead of four per app; icons load lazily as rows scroll into view.

**Interface**
- **User / System tabs** instead of the filter button.
- **Search** by app name or package name.
- **Sort and Filter chips pinned** under the tabs, so they never scroll away.
- **Draggable fast-scroll bar** on the right. Its popup shows a letter (sorted by name), a month
  (sorted by date) or a size (sorted by size).
- **Pull-to-refresh needs a longer pull**, and your selection is kept when the list refreshes.
- Light/dark follows the system. The three-dot menu (night mode, profiles, about, rate, feedback) is gone.

**Uninstalling**, in order of preference:
1. **Shizuku**: silent uninstall of user *and* system apps, no root needed.
2. **Root** (`su`): same, silent.
3. **Android's own dialog**, one app at a time (user apps only).

System apps are removed **for your user only** (`pm uninstall -k --user 0`). That is reversible
(`cmd package install-existing <package>`) and cannot leave the phone unbootable. The old root code
that also deleted files under `/system` has been removed.

## Building
JDK 17 and the Android SDK (platform 33). Then:

```
./gradlew testDebugUnitTest assembleDebug
```


Minimum Android version is 6.0 (required by Shizuku).

## License
Uses [Shizuku-API](https://github.com/RikkaApps/Shizuku-API) and
[AndroidFastScroll](https://github.com/zhanghai/AndroidFastScroll) (both Apache-2.0).