# Shitu

<img src="docs/icon/ic_launcher_512.png" width="88" alt="Shitu icon">

**Picks up pictures hidden on your phone and moves them into a folder you choose — automatically.**

Download: [latest APK](https://github.com/Landslide3154/shitu-android/releases) · License: GPL-3.0 · Package: `com.landslide.shitu`

---

## What it does

Many apps keep their pictures in `Android/data/<package>/…` — a place normal apps can't open and the gallery never shows.
Shitu does one simple thing: **every few minutes it looks at a folder you pick and moves new pictures into another folder you pick**, so they show up in your gallery.

## Two ways to run it

| | Where it can read/write | What you need |
| --- | --- | --- |
| **Standard mode** | Gallery, Downloads, Pictures, DCIM — normal folders | Turn on "All files access" in Settings → Storage access |
| **Shizuku mode** | Also `Android/data`, where apps hide their pictures | Install [Shizuku](https://shizuku.rikka.app/) and grant access |

You don't have to choose: **Shizuku is used when available, otherwise Shitu falls back to standard mode** — switch any time, no reinstall.
The only difference: without Shizuku, pictures inside `Android/data` can't be reached (the system doesn't allow it).

## Install

1. Open [Releases](https://github.com/Landslide3154/shitu-android/releases) and download the latest `app-release.apk`
2. Tap to install (allow "install unknown apps" the first time)
3. Open Shitu

> For Shizuku mode, install and start Shizuku first. Shitu shows "Shizuku ready" at the top once it can talk to it.

## First run (follow along)

1. Open Shitu and check the card at the top:
   - "**Standard mode**" or "**Shizuku ready**" → you're good to go
   - "not granted / not running" → tap the card and follow the prompt, or open Settings → Storage access and enable "All files access"
2. Tap **`+`** at the bottom right → **Blank rule**
3. Give the rule a **name** (for example "Reader covers")
4. Tap **Choose** next to **Source folder**, start at `/sdcard`, and tap into the folder with the pictures; then "Choose this folder"
5. Do the same for **Target folder**; `DCIM` or `Pictures` is recommended (the gallery shows it right away)
6. Keep mode on **Copy** and tap **Save and start moving**
7. Wait a few minutes and check your gallery. Once you're happy, switch the rule to **Move**

> Why copy first? Copying never touches the originals — the worst case is a duplicate. Switch to move once you're sure.

## The four screens

- **Rules**: one card per rule; **move is green, copy is blue**. The switch enables/disables it; "Run now" moves once immediately;
  long-press for "duplicate / undo / delete". "This week +N" at the top opens a small summary.
- **Logs**: each entry shows a **thumbnail**, what happened, when, and why it failed. **Filter** (top right) narrows by result
  or rule; **Export** saves a CSV. Files skipped as duplicates don't clutter the log — they're only counted.
- **Settings**: **Language** (System / 中文 / English) at the top; run conditions (charging / screen off / night / battery);
  detection speed; appearance; copy records; rule import/export; self-check; check for updates.
- **Edit rule**: six clear cards — name, source folder, target folder, mode, interval, file names. You can limit to
  "recent N days", "at least N KB", add one subfolder per app, and **preview** how many files would move before saving.

## FAQ

**How do I switch the interface language?**
Settings → Language → English (or 中文 / System). No need to change the system language.

**Nothing is being moved.**
Tap the status card to run a self-check and see which item is red; then check the failure reason in Logs.

**The log is empty.**
Nothing has actually been moved yet. Tap "Run now" on a rule to try once.

**Will it delete my pictures?**
No. If a move fails, the source file stays untouched; a failed move is rolled back; existing files are never overwritten.

**How do I know there's a new version?**
Settings → About → Check for updates. It is the only feature that goes online; everything else is fully offline.

**It keeps getting killed in the background.**
On HyperOS/MIUI, enable autostart, set battery saver to "No restrictions", lock the app in Recents, and allow notifications.

**I want `Android/data`, but I don't have Shizuku.**
That folder is only reachable from Shizuku's (or adb's) identity. Use Shizuku mode.

## Privacy

- Everything is offline except "Check for updates", which contacts GitHub
- Pictures are only moved between the two folders you choose; nothing is uploaded or analysed
- Logs and settings stay on the device; exported CSV files contain full paths

## More

- Detailed manual (parameters, detection speed, file naming): [使用手册](docs/使用手册.md) (Chinese)
- What changed in each version: [CHANGELOG](docs/CHANGELOG.md)
- 中文说明: [README.md](README.md)
- Building from source: see the last section of the Chinese manual
