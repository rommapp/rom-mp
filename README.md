# RomMP

[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](https://www.gnu.org/licenses/gpl-3.0)
[![Android](https://img.shields.io/badge/Android-8.0+-green.svg)](https://developer.android.com/about/versions/oreo)

**A gamepad-first Android music player for the game soundtracks in your [RomM](https://github.com/rommapp/romm) library.**

Your RomM server already stores the soundtracks attached to your games. RomMP plays them. Browse by game, build playlists that live on the server rather than on one device, and take albums offline for the commute.

## Features

### Native RomM Client
Reads the soundtrack corpus through RomM's music API, so albums arrive with the game's cover art and platform already attached. No separate library to maintain and no metadata to re-tag.

### Pairing Without a Password
Sign in by device authorization. Scan the QR from a phone that is already signed in, or open the verification URL in this device's browser. RomMP stores a scoped client token and never asks for or keeps your password.

### Favorites and Playlists
Favorites and playlists are backed by RomM collections, so they follow your account to every device that talks to the same server rather than living in local storage.

### Offline Downloads
Download an album or a single track into the device's Music library. Downloaded tracks play from local storage when the server is unreachable, and the player switches between local and streamed sources as connectivity changes.

### Background Playback
A media session keeps playback running when the app leaves the foreground, with lockscreen, notification, Bluetooth, and wired remote controls.

### Built for Handhelds
Full D-pad and face-button navigation, shoulder buttons mapped to track skip, and a touch lock for pockets. Designed for the same class of device as Anbernic, Retroid Pocket, and Odin hardware.

## Requirements

- Android 8.0 or newer
- A RomM server exposing the music API, with soundtracks attached to your games

## Building

```
./gradlew assembleDebug
```

Release builds are signed from a `keystore.properties` at the repository root, alongside the keystore it names:

```
storeFile=release.keystore
storePassword=...
keyAlias=...
keyPassword=...
```

Both files are gitignored. `assembleRelease` and `bundleRelease` fail with an explanation when `keystore.properties` is absent, rather than falling back to the debug signing key.

## Connecting to a Self-Hosted Server

RomM is commonly served over plain HTTP on a LAN, or behind a certificate signed by a CA you installed yourself. RomMP permits both, since the server address is yours and is unknown when the app is built. Certificates installed through Android Settings are trusted alongside the system roots.

## License

GPL-3.0. See [LICENSE](LICENSE).
