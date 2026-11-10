# Appurrge

**Fast launch and minimal size app uninstaller for Android.**

Select apps and remove them in one go. No ads, no tracking, no unnecessary permissions.

## Requirements

- Android 7.0+ (API 24)
- Optional: [Shizuku](https://shizuku.rikka.app/) For silent batch uninstalls with no user input & system-app uninstalls

[![GitHub downloads](https://img.shields.io/github/downloads/Ghostt14/Appurrge/total?style=for-the-badge)](https://github.com/Ghostt14/Appurrge/releases)
[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg?style=for-the-badge)](https://www.gnu.org/licenses/gpl-3.0)
[![Repository size](https://img.shields.io/github/repo-size/Ghostt14/Appurrge?style=flat-square)](https://github.com/Ghostt14/Appurrge)
[![GitHub release](https://img.shields.io/github/v/release/Ghostt14/Appurrge?style=for-the-badge)](https://github.com/Ghostt14/Appurrge/releases)
[![F-Droid](https://img.shields.io/badge/F--Droid-coming_soon-green?style=for-the-badge&logo=f-droid)](https://f-droid.org)
[![Obtainium](https://img.shields.io/badge/Obtainium-ready-blue?style=for-the-badge)](https://github.com/ImranR98/Obtainium)

## Features

- **Extremely fast startup** — last list is cached; newest apps appear first on cold start
- User / System tabs to separate apps
- Instant search + sort (always reachable, even while selecting)
- Built-in draggable fast-scroller (letter / month / size popup)
- Selection survives refresh, search, and tab switches
- Silent uninstall via **Shizuku** or root  
  (falls back to Android’s normal confirmation dialog for user apps)
- System apps are only removed for the current user (`pm uninstall -k --user 0`) — fully reversible and never touches `/system`
- Tiny APK (heavily shrunk with R8 + resource shrinking on every build)

## Comparison

|            | **Appurrge** | Batch Uninstaller | Splend Apps - Uninstaller |
| ---------- | ------------ | ----------------- | ------------------------- |
| APK size   | **216kb**    | 2.48mb            | 15mb                      |
| App size   | **682kb**    | 6.37mb            | 27.53mb                   |
| Cold Start | **~214ms**   | ~1.81s            | ~366ms (read below)       |

> Information were averages taken from S22+, SA15.
> Actual cold-start numbers vary by device and number of installed apps.
> 
> Splend Apps - Uninstaller has a load screen once the app launches, which based on screen recording can end up being slower or equal to Batch Uninstaller