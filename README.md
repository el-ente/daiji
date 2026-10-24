# Daiji

A fast, games-first home screen for Android TV and Google TV. It shows your
retro games and your TV apps in one place, and launches games in RetroArch or
standalone emulators.

Native Kotlin with Compose for TV. No artwork scraping, analytics or network
access, so it starts fast and stays light on 2 GB devices like the Chromecast
with Google TV.

## Install

1. Open the latest run under **Actions → Build APK** and download the
   `daiji-apk` artifact. Unzip it to get the APK.
2. Install it over ADB:

   ```sh
   adb connect <tv-ip>
   adb install -r app-release.apk
   ```

3. Give Daiji access to your ROMs. On Google TV the settings screen for this
   is often missing, so use ADB:

   ```sh
   adb shell appops set --uid io.github.elente.daiji MANAGE_EXTERNAL_STORAGE allow
   ```

4. Make it the home screen by disabling the Google TV launcher:

   ```sh
   adb shell pm disable-user --user 0 com.google.android.apps.tv.launcherx
   adb shell pm disable-user --user 0 com.google.android.tungsten.setupwraith
   ```

   To undo, run the same commands with `pm enable` instead of
   `pm disable-user --user 0`. System updates sometimes re-enable the stock
   launcher, so you may need to repeat this step.

## ROMs

Put each system in its own folder inside a `ROMs` folder:

```
/storage/emulated/0/ROMs/
  snes/Chrono Trigger (USA).sfc
  psx/Crash Bandicoot (USA).chd
  gba/...
```

Daiji finds a `ROMs`, `Roms` or `Games` folder on internal storage, SD cards
and USB drives by itself. You can also browse to any folder with the remote
in Settings → ROMs folder.
Region and dump tags like `(USA)` or `[!]` are hidden from titles. Discs
listed in an `.m3u` playlist are hidden behind the playlist.

Folder names follow the ES-DE and RetroArch conventions: `nes`, `snes`, `n64`,
`gb`, `gbc`, `gba`, `nds`, `gc`, `wii`, `mastersystem`, `genesis` or
`megadrive`, `segacd`, `sega32x`, `gamegear`, `saturn`, `dreamcast`, `psx`,
`ps2`, `psp`, `pcengine`, `atari2600`, `ngp`, `wonderswan`, `neogeo`,
`arcade` and `mame`.

### Artwork

Daiji does not download artwork. It shows covers and screenshots you already
have, matched by file name:

- `ROMs/<system>/media/covers/<game>.png` and `media/screenshots/<game>.png`
- ES-DE's `ES-DE/downloaded_media/<system>/covers` and `screenshots`
- RetroArch's `RetroArch/thumbnails/<Platform>/Named_Boxarts` (covers),
  `Named_Titles` and `Named_Snaps` (screenshots), as downloaded by its
  Playlist Thumbnails Updater. Thumbnails are matched through the game's label
  in the RetroArch playlist, so your ROM files can be named anything. Needs the
  retroarch.com build, whose folder is `/storage/emulated/0/RetroArch`.

Games without artwork get a colored tile with their title.

## Random pick and favourites

- **Random** in the game panel shows a random game from your whole library. Press it again for another.
- **☆ Favourite** saves the shown game; it turns to ★ when saved. Holding OK on a cover does the same.
- Saved games appear in a **Favourites** shelf, right after Recent.

## Emulators

| Emulator | Systems |
| --- | --- |
| RetroArch | Everything with a libretro core |
| PPSSPP | PSP (default when installed) |
| Dolphin | GameCube, Wii |
| DuckStation | PlayStation |
| NetherSX2 | PlayStation 2 |
| Ask Android | Any system, using Android's app picker |

For each system Daiji uses the first installed emulator, preferring the
standalone one where it is listed as the default. Change it per system in
Settings, or with the button next to **Play**.

RetroArch needs the matching core downloaded in RetroArch's own core
downloader, for example `snes9x` for SNES. The core per system is in
`app/src/main/java/io/github/elente/daiji/data/Systems.kt`.

## Building

CI builds a signed release APK on every push. To build locally you need the
Android SDK and JDK 17:

```sh
./gradlew assembleRelease
```

Release builds are minified with R8. Install release builds when judging
performance, because Compose debug builds are much slower.

`app/dev.keystore` is a development signing key, committed so that every
build can update the previous install. Replace it before publishing the app
anywhere.
