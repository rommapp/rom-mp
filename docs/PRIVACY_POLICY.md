# Privacy Policy for RomMP

**Last Updated:** September 2026

## Overview

RomMP is an open-source Android music player that plays the game soundtracks
stored on a RomM server you host yourself. It has no account system of its own,
no companion service, and no backend operated by its developers. This policy
describes what the app stores on your device and what it sends over the network.

## Data Sent Off Your Device

RomMP talks to exactly one server: the one whose address you type when you pair
the app. The source contains no hardcoded server addresses, no analytics
endpoint, and no fallback host, so there is nowhere else for your data to go.
Everything below travels only to that server.

### At Pairing

Signing in uses a device-authorization flow. RomMP asks your server to start a
pairing, shows you a QR code and a short code, and waits for you to approve the
pairing from a browser that is already signed in to that server. The pairing
request contains:

- **A client device identifier**, formed as `rommp-` followed by the Android ID
  of your device. Android generates this value separately for each app on each
  device, so it is not your hardware serial number, IMEI, or advertising ID, and
  no other app receives the same value. It exists so your server can list the
  device that is paired and so you can revoke it.
- **A device name**, taken from the manufacturer and model reported by Android,
  for example `Anbernic RG40XXV`. This is what identifies the device in your
  server's list.
- **The client name** `rommp` and the platform `android`.
- **The permissions RomMP is asking for**, which are read access to your
  profile, platforms, ROMs, collections, and assets, plus read and write access
  to playlists.

Your password is never requested, never sent, and never stored. RomMP shows a QR
code but does not read one, and it asks for no camera access.

### While You Use the App

Ordinary API requests to your server carry the access token issued at pairing:
listing games and tracks, streaming audio, reading and changing favorites and
playlists, and downloading albums for offline use. Your server sees these
requests along with the standard information any web server sees, such as your
device's IP address on your network.

The token is attached only to requests whose scheme, host, and port all match the
server you paired with.

## Data Stored On Your Device

None of this is transmitted anywhere by RomMP.

- **Your connection**, meaning the server address, the access token, the device
  identifier your server assigned, and the permissions it granted.
- **Your listening position**, meaning the track list, current track, and
  position of the last queue you played, so playback resumes where you left off.
- **Library metadata**, meaning the game and track details RomMP has seen,
  including titles, platforms, track numbers, durations, and the last known
  favorite state of each track. This is what lets the app show your albums when
  the server is unreachable.
- **Cover art**, cached in the app's private storage and in an image cache of up
  to 256 MB.
- **Downloaded audio**, written into your device's shared Music library under
  `Music/RomM`, organized by platform and game. These are ordinary music files,
  so other apps on your device that read your Music library can see them.
  Deleting an album in RomMP removes them.

Offline downloads require Android 10 or newer. The app requests no storage
permission; downloads go through Android's own media library, which grants access
only to the files RomMP itself created.

## Permissions

RomMP declares five permissions and no others.

- **INTERNET**, to reach your server.
- **ACCESS_NETWORK_STATE**, to notice when the device goes offline so the app can
  fall back to downloaded tracks instead of failing.
- **FOREGROUND_SERVICE** and **FOREGROUND_SERVICE_MEDIA_PLAYBACK**, to keep
  playback running with a media notification and lockscreen controls when the app
  is not in the foreground.
- **WAKE_LOCK**, to keep audio playing and streaming with the screen off.

There is no permission for location, contacts, camera, microphone, storage, or
the list of apps installed on your device, because RomMP uses none of them.

## What RomMP Does Not Do

- No analytics, usage statistics, or telemetry.
- No crash or error reporting to any service.
- No advertising, ad identifiers, or tracking.
- No third-party SDK of any kind. Every library the app ships is either part of
  Android itself or a general-purpose open-source component such as the HTTP
  client, the media player, or the image loader, and none of them report to
  anyone.
- No servers operated by the developers, so there is nothing for them to hold.

## Backup and Device Transfer

The stored access token is excluded from Android cloud backup and from
device-to-device transfer. If you restore a backup or move to a new device, the
credential does not come with it and you pair again, which takes one QR scan.

## Network Security

A self-hosted RomM server is commonly reached over plain HTTP on a local network,
or through a certificate signed by a certificate authority you installed
yourself. RomMP permits both, and trusts certificates you have added in Android
Settings alongside the system ones. Its address is yours and is unknown when the
app is built, so the app cannot narrow this to a specific domain.

The consequence is worth knowing. Over plain HTTP, anything on the network path
can read the traffic, including the access token. On your own network that is
usually acceptable. Over a network you do not control, reach your server through
HTTPS or a VPN.

## Your Control

- **Sign out** deletes the server address, access token, device identifier, and
  granted permissions from the device. Downloaded audio and cached library
  metadata stay until you delete the albums or clear the app's data.
- **Clearing app data or uninstalling** removes everything RomMP keeps in its own
  storage.
- **Your library, favorites, and playlists live on your server**, not in RomMP.
  Removing them, revoking a paired device, or deleting your account are all
  things you do on the server you run. RomMP cannot delete anything there on your
  behalf beyond the favorites and playlist changes you make in the app.

## Children's Privacy

RomMP has no accounts, collects nothing, and sends nothing to its developers, so
there is no data about any user, of any age, for it to hold.

## Changes to This Policy

Changes are reflected in the date at the top of this file and noted in release
notes when they affect what the app does with your data.

## Contact

For privacy questions, open an issue at
https://github.com/rommapp/rom-mp.

## Open Source

RomMP is licensed under the GPL-3.0. Every claim here can be checked against the
source.
