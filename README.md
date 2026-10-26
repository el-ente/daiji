# Daiji

A fast, games-first home screen for Android TV and Google TV. It shows your
retro games and your TV apps in one place, and launches games in RetroArch or
standalone emulators.

Native Kotlin with Compose for TV. No analytics, so it starts fast and stays
light on 2 GB devices like the Chromecast with Google TV. The network is used
only when you ask for it: **Settings → Set up RetroArch** downloads RetroArch,
its cores, box art and game info from libretro.

## Install

1. Download the APK from the latest release under **Releases**. Later
   versions install from **Settings → Check for updates**.
2. Install it over ADB:

   ```sh
   adb connect <tv-ip>
   adb install -r daiji-v0.1.0.apk
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

## Home

Home is the first screen, and Back always returns to it, to the element you left.

- **Header:** greeting, clock and date, and pills for the network (opens Wi-Fi settings) and Daiji's settings.
  The Wi-Fi name needs location access, which a TV never asks for:
  `adb shell pm grant io.github.elente.daiji android.permission.ACCESS_FINE_LOCATION`. Without it the pill says Wi-Fi.
- **Dock:** up to 6 pinned apps; the focused app's name shows above it, over a glow in its banner's colour.
  Until you pin one, the first four TV apps show; the rest are in the Apps rows below.
- **Games:** a row in the dock's style: up to three games played last (▶), your favourites (★) and a random
  pick (🎲), then All games. The line above names the focused game, its system and when it was last played.
  Holding OK on the random pick picks another; on the others it adds or removes the favourite.
- **All games:** every game in a grid. The pills on top (All, ★ Favourites, one per system with more than one
  game) switch on OK. Holding OK on a game adds or removes it from favourites.
- **Apps:** every app not pinned to the dock, smaller, below the games. Other home screens (a previous launcher, the
  Settings app's fallback home) are left out. Holding OK on any app pins it to the dock, unpins
  it, or opens its info page.

Remotes with a Menu key can use it wherever holding OK works.

## Remote buttons

Settings › Remote buttons maps any remote button to an app, a TV input (HDMI, AV) or an action: close all apps
(goes Home, then stops every app in the background), Home, Back, recent apps, mute, sleep or Android settings. A
button can do something else when held. Add one by pressing it; the arrows, OK, Back, Home and volume can't be mapped.

The buttons work from any app through an accessibility service, which Android makes you turn on once:

```sh
adb shell settings put secure enabled_accessibility_services io.github.elente.daiji/.remote.RemoteKeyService
adb shell settings put secure accessibility_enabled 1
```

That replaces the whole list; add other services separated by `:`. Android switches the service off when Daiji is
updated. To let Daiji switch it back on by itself, grant it once:

```sh
adb shell pm grant io.github.elente.daiji android.permission.WRITE_SECURE_SETTINGS
```

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

## Releasing

Push a tag named after the version, and CI builds the APK and publishes the
release:

```sh
git tag v0.2.0
git push origin v0.2.0
```

Use plain `major.minor.patch` numbers: the version code is computed from them,
and it must go up for an update to install.
