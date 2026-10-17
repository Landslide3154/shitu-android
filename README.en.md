# Shitu

<img src="docs/icon/ic_launcher_512.png" width="88" alt="Shitu icon">

**Picks up pictures hidden on your phone and moves them into a folder you choose — automatically.**

[![License](https://img.shields.io/badge/license-GPL--3.0-blue.svg)](LICENSE)
[![Release](https://img.shields.io/github/v/release/Landslide3154/shitu-android)](https://github.com/Landslide3154/shitu-android/releases)

Download: [latest APK](https://github.com/Landslide3154/shitu-android/releases) · Package: `com.landslide.shitu`

---

## What it does

Many apps keep their pictures in `Android/data/<package>/…` — a place normal apps can't open and the gallery never shows.
Shitu looks at a folder you pick and moves new pictures into another folder you pick, so they show up in your gallery.

## Two ways to run it

| | Where it can read/write | What you need |
| --- | --- | --- |
| **Standard mode** | Gallery, Downloads, Pictures, DCIM — normal folders | Turn on "All files access" in Settings → Storage access |
| **Shizuku mode** | Also `Android/data`, where apps hide their pictures | Install [Shizuku](https://shizuku.rikka.app/) and grant access |

Shizuku is used when available; otherwise Shitu falls back to standard mode. No reinstall, no rule changes.
Without Shizuku, pictures inside `Android/data` can't be reached (the system doesn't allow it).

## Screenshots

<table>
  <tr>
    <td><img src="docs/screenshots/rules.png" width="250" alt="Rules"></td>
    <td><img src="docs/screenshots/edit.png" width="250" alt="Edit rule"></td>
    <td><img src="docs/screenshots/logs.png" width="250" alt="Logs"></td>
  </tr>
  <tr>
    <td align="center">Move is green, copy is blue</td>
    <td align="center">Six clear cards</td>
    <td align="center">Thumbnails and filters</td>
  </tr>
  <tr>
    <td><img src="docs/screenshots/settings.png" width="250" alt="Settings"></td>
    <td><img src="docs/screenshots/picker.png" width="250" alt="Choose folder"></td>
    <td></td>
  </tr>
  <tr>
    <td align="center">Run conditions, backup</td>
    <td align="center">Choose a folder</td>
    <td></td>
  </tr>
</table>

## Install

1. Open [Releases](https://github.com/Landslide3154/shitu-android/releases) and download the latest `shitu-<version>.apk`
2. Tap to install (allow "install unknown apps" the first time)
3. Open Shitu

## First run

1. Check the card at the top: "**Standard mode**" or "**Shizuku ready**" means you're good to go
2. Tap **`+`** → **Blank rule**
3. Name it, choose a **source folder** and a **target folder** (both start at `/sdcard`)
4. Keep mode on **Copy**, tap **Save and start moving** — it shows how many files would move before saving
5. Wait a few minutes and check your gallery; once happy, switch the rule to **Move**

## Features

- **Rules**: one card per rule, switch to enable/disable, tap to edit, long-press for duplicate / undo / delete.
  "This week +N" opens a summary; move is green, copy is blue.
- **Edit rule**: name, source, target, mode, frequency, file names — plus a **dry-run preview**,
  "only last N days", "at least N KB", one subfolder per app, and **rename by content (15-char code)** so the same photo is never stored twice.
- **Logs**: thumbnails, green/grey/blue result tags, filter panel, CSV export, and a search box that suggests paths used by your rules (✕ clears it).
- **Settings**: run conditions (charging / screen on / screen off / low battery), storage access, detection timing,
  copy records (pick and clear), rule + settings backup, appearance & language, self-check, check for updates.

## FAQ

**Nothing is being moved.** Tap the status card to run a self-check, then check the failure reason in Logs.

**Will it delete my pictures?** No. Failed moves leave the source untouched, a bad move is rolled back, and existing files are never overwritten.

**How do I switch the interface language?** Settings → Appearance & language → English / 中文 / System.

**Only one feature goes online** — "Check for updates" (GitHub Releases). Everything else is offline.

## More

- Detailed manual (Chinese): [使用手册](docs/使用手册.md)
- Changelog: [CHANGELOG](docs/CHANGELOG.md)
- 中文说明: [README.md](README.md)

---

[![](https://img.shields.io/badge/powered_by-dsh-4D6BFE?style=flat-square&logo=deepseek&logoColor=white)](https://github.com/deepseek-ai/deepseek-harness)
