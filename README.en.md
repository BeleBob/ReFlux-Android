<div align="center">
  <img src="design/logo/icon.svg" width="112" alt="ReFlux logo">
  <h1>ReFlux for Android</h1>
  <p>The client of ReFlux servers: an encrypted tunnel through Mail.ru and Yandex documents.</p>
  <p>
    <a href="https://github.com/BeleBob/ReFlux-Android/releases/latest"><img src="https://img.shields.io/github/v/release/BeleBob/ReFlux-Android?display_name=tag&amp;sort=semver&amp;style=flat-square&amp;color=EA1A1A" alt="Latest release"></a>
    <a href="https://github.com/BeleBob/ReFlux-Android/actions/workflows/ci.yml"><img src="https://github.com/BeleBob/ReFlux-Android/actions/workflows/ci.yml/badge.svg" alt="CI status"></a>
    <a href="LICENSE"><img src="https://img.shields.io/badge/license-GPL--3.0--or--later-blue?style=flat-square" alt="GPL-3.0-or-later license"></a>
    <img src="https://img.shields.io/badge/Android-8.0%2B-3DDC84?style=flat-square&amp;logo=android&amp;logoColor=white" alt="Android 8 or newer">
  </p>
  <p>
    <a href="https://github.com/BeleBob/ReFlux"><img src="https://img.shields.io/badge/core-ReFlux-455a64?style=flat-square" alt="ReFlux core"></a>
    <img src="https://img.shields.io/badge/ABI-ARM64%20%7C%20ARMv7%20%7C%20x86__64%20%7C%20x86-455a64?style=flat-square" alt="Supported Android architectures">
    <img src="https://img.shields.io/badge/Java-17-ED8B00?style=flat-square&amp;logo=openjdk&amp;logoColor=white" alt="Java 17">
    <img src="https://img.shields.io/badge/IPv4%20%2F%20TCP%20%2B%20UDP-supported-f59e0b?style=flat-square" alt="IPv4: TCP and UDP">
  </p>
  <p><a href="README.md">Русский</a> · <strong>English</strong></p>
</div>

ReFlux for Android connects a phone to a [ReFlux](https://github.com/BeleBob/ReFlux)
server. Traffic travels through a document co-editing session instead of a
VPN protocol, encrypted with AES-256-GCM. The server's owner hands out the
channel as an `openflux://` link or a QR code.

**[Download the latest release](https://github.com/BeleBob/ReFlux-Android/releases/latest)**

```text
App (VPN or SOCKS5) -> Mail.ru / Yandex document -> ReFlux node -> the server's tunnels -> Internet
```

## Contents
- [Features](#features)
- [Limitations](#limitations)
- [Install](#install)
- [Connect](#connect)
- [Build from source](#build-from-source)
- [Core and updates](#core-and-updates)
- [Layout](#layout)
- [Origin and license](#origin-and-license)

## Features
- **Connect by link or QR code.** A profile comes from a `reflux://` or `openflux://` link
  (opened or pasted after copying) or a QR code (camera or a photo from the
  gallery); nothing to type.
- **Several documents per channel.** In Session mode the app keeps every
  document of the channel up: traffic goes over the first working one and
  over the next when it fails.
- **The connection recovers by itself.** On start the app keeps trying until
  it connects; a document connection the network dropped (Wi-Fi to mobile
  data, a NAT timeout) is replaced in about a minute.
- **Kill switch.** While the channel recovers, no traffic leaves outside the
  tunnel (on by default).
- **Encryption.** AES-256-GCM with a scrypt-derived key; the document link and
  the key are kept in the Android Keystore.
- **Per-app routing**: an allow list or a deny list.
- **SOCKS5 proxy mode** instead of the system VPN, optionally reachable from
  the local network with authentication.
- **A notification** with the speed up and down and a disconnect button.
- **Updates from inside the app.** When a new version is out the app offers
  it by itself: it downloads the APK for this phone, checks it against the
  release's checksums and opens the system installer; the connection comes
  back by itself after the install. The offer opens again from the version
  in the header or in About.
- **Language and theme.** Russian or English (the phone's by default); a
  light, dark or the phone's theme, with a black background for AMOLED
  screens.
- **An event log** with search, an errors filter, copying and sharing as a
  file.
- **Transports**: Mail.ru Docs (the main one for ReFlux servers), Yandex Docs
  and Volga, Cups.online.

The original app's other features stay: installing a node on a VPS from the
app, the "no server" mode with a PHP node, signed JS transports.

## Limitations
- IPv4 is tunneled (TCP and UDP, DNS included); IPv6 is not.
- The document service sees metadata: connection times and traffic volume.
  The content is encrypted.
- This is experimental software, not an audited replacement for WireGuard.
  Use it only on networks where you are allowed to.

## Install
1. Open the [latest release](https://github.com/BeleBob/ReFlux-Android/releases/latest)
   and download an APK:
   - most phones (2018 or newer) → `ReFlux-android-arm64-v8a-release.apk`;
   - older 32-bit ones → `ReFlux-android-armeabi-v7a-release.apk`;
   - not sure → `ReFlux-android-universal-release.apk`.
2. Install it, allowing installs from that source.

The app installs next to other OpenFlux clients and leaves their settings
alone. Releases are signed with the project's permanent certificate, so new
versions install over the old one and keep the profiles. From 1.2.0 the app
updates itself: on the first update Android asks once whether ReFlux may
install apps.

## Connect
1. Get an `openflux://` link or a QR code from the server's owner. A server
   with a client bot sends them on its own once a request is approved.
2. In the app open "Profiles" and scan the QR code (the QR button) or paste
   the link.
3. Switch the connection on and accept the system's VPN request.

If the owner replaces the channel's document, the bot sends a new link:
import it the same way.

## Build from source

### Requirements
- Java 17 or 21 (JDK);
- Android SDK with platform 35, Build Tools 35.0.0 and NDK 27.0.12077973;
- Gradle 8.14.3;
- Go (the version in `core/go.mod`) and `gomobile`.

### Build
```bash
git clone --recursive https://github.com/BeleBob/ReFlux-Android.git
cd ReFlux-Android
go install golang.org/x/mobile/cmd/gomobile@v0.0.0-20260908204917-8b95e45f8d3e
go install golang.org/x/mobile/cmd/gobind@v0.0.0-20260908204917-8b95e45f8d3e
gomobile init
export ANDROID_HOME=~/Android/Sdk          # the SDK path
./build_android_app.sh                      # a debug build
```

The APKs land in `dist/`: one per architecture and a universal one. A
release build is signed with the project's key:

```bash
BUILD_TYPE=release \
ANDROID_KEYSTORE_FILE=... ANDROID_KEYSTORE_PASSWORD=... \
ANDROID_KEY_ALIAS=... ANDROID_KEY_PASSWORD=... \
./build_android_app.sh
```

An APK signed with another certificate does not update an installed app: it
has to be uninstalled first, with its settings.

## Core and updates
The tunnel core is the [ReFlux](https://github.com/BeleBob/ReFlux)
repository, as the `core/` submodule: OpenFlux with ReFlux's fixes. To update
it:

```bash
git -C core fetch origin && git -C core checkout origin/main
./build_android_app.sh
git add core && git commit -m "core: update to ReFlux <commit>"
```

A `vX.Y.Z` tag makes a release: GitHub Actions signs the APKs and publishes
them with `SHA256SUMS`.

**An important update.** The line `<!-- important -->` (or `<!-- важное -->`)
in a release's notes (`.github/release-notes/vX.Y.Z.md`) marks it important;
the release page does not show it. A published release is marked or unmarked
by editing its description on GitHub: the app reads it from there. The app,
1.2.0 and newer, offers such an update at every start, shows the version in
the header in red, and with the tunnel on reminds of it in a notification
every 12 hours until it is installed. Any marked release newer than the
installed one counts, also when a plain one came out after it.

## Layout
```
ReFlux-Android/
  core/                 # the ReFlux core (submodule): transports, tunnel, mobile/ bridge
  android/              # the Android app
  build_android_app.sh  # builds the APKs on top of core/mobile
  design/               # logo and icons
  docs/                 # the original app's documentation
```

## Origin and license
ReFlux for Android is a modified version of
[OpenFlux-Android](https://github.com/damnurmum/OpenFlux-Android) (damnurmum),
which is based on [OpenFlux](https://github.com/p1neappleXpress/OpenFlux)
(p1neappleXpress). Since October 2026 the app's name and id
(`io.github.belebob.reflux`), its core (ReFlux instead of OpenFlux) and its
behavior on a lost connection have changed; the commit history lists the
changes.

It is distributed under the GNU GPL v3.0 or any later version, like the
projects it comes from: see [LICENSE](LICENSE), [COPYRIGHT](COPYRIGHT) and
[NOTICE](NOTICE). The project is not affiliated with or endorsed by Yandex or
Mail.ru.

The ReFlux name and logo are not covered by this license: the GPL grants no
rights to trade names or marks (section 7e). Take and change the code under
the GPL, but please give a modified version you release on its own another
name.
