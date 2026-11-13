<p align="center">
  <img src="icon.svg" width="120" height="120" alt="RuN App Logo" />
</p>

<h1 align="center">RuN</h1>

<p align="center">
  <strong>Offline Focus, App Blocking, Private Notes & Planning</strong>
</p>

<p align="center">
  <a href="https://github.com/Nrajesh0/Ayva/releases/latest"><img src="https://img.shields.io/github/v/release/Nrajesh0/Ayva?style=flat-square&color=black" alt="Latest Release"></a>
  <a href="https://github.com/Nrajesh0/Ayva/releases"><img src="https://img.shields.io/github/downloads/Nrajesh0/Ayva/total?style=flat-square&color=blue" alt="Downloads"></a>
  <a href="LICENSE"><img src="https://img.shields.io/badge/license-GNU%20AGPLv3-blue?style=flat-square" alt="License: AGPLv3"></a>
  <a href="TRADEMARK.md"><img src="https://img.shields.io/badge/trademark-Brand%20Policy-orange?style=flat-square" alt="Trademark Policy"></a>
</p>


## Overview

**RuN** is an Android productivity app that brings app blocking, focus sessions,
tasks, habits, private notes and an offline assistant into one place. The Android
application lives in this repository under the project name **Ayva**.

## Features

| Area | What RuN provides |
| --- | --- |
| App blocking | Manual app rules, scheduled routines, strict/soft blocking and usage-based limits, using a direct system overlay. |
| Focus and planning | Focus countdowns, activity tracking, tasks, reminders, recurring tasks and checklists. |
| Habits | Goals, schedules, reminders and daily progress tracking. |
| Notes and private archive | Rich notes with attachments, encrypted storage and a PIN-protected private archive. Optional PIN-free archiving lets you add protected notes while the vault stays locked; viewing or unarchiving still requires authorization. |
| Backup and restore | Password-protected manual backups, scheduled encrypted backups, a portable recovery file, backup verification and restore progress. Snapshot restore replaces backed-up app data after review; importing extra copies is a separate choice. |
| Ayva assistant | On-device conversation for supported task, habit, focus, routine and settings actions, with clarification, previews and guarded undo where supported. |
| Learning | Bundled dictionary, vocabulary practice and Drill/Blitz exercises. |
| Widgets and appearance | Home-screen widgets for notes, exams and battery health; themes, launcher icons and animated companion artwork. |

Ayva uses authored content and bounded rules. It is not a general-purpose language
model and does not download a model or call a hosted AI service. Private note
contents are outside the assistant's data-access capabilities. See the
[assistant guide](docs/assistant/README.md) for supported scope and limitations.

## Privacy and permissions

RuN removes the Android Internet permission and keeps its core processing and
storage on the device. The project does not integrate analytics or advertising
SDKs. Android's automatic app backup is disabled; RuN provides its own explicit
encrypted backup flows.

Optional speech input uses the device's speech-recognition service. Its processing
and network behavior depend on the selected service and available offline support.
A cloud folder selected through Android's document picker is likewise handled by
that provider. These external services are separate from RuN's own network access.

Permissions and special access support specific features:

| Access | Purpose |
| --- | --- |
| Usage access and installed-app visibility | Identify apps, evaluate usage and apply blocking rules. |
| Display over other apps | Show app-blocking screens and floating tools. |
| Notifications and foreground services | Show reminders, active-service status and backup progress. |
| Exact alarms, when granted | Improve reminder timing; Android scheduling restrictions still apply. |
| Microphone, when granted | Optional speech input. |
| User-selected files/folders | Import/export attachments, recovery files and encrypted backups through Android's document picker. |
| Battery-optimization exemption, when granted | Help background blocking and scheduled work continue. |

Grant relevant access through the app's setup/settings screens. Android and device
manufacturer restrictions can affect background execution and reminder delivery.
For implementation details, findings and verification limits, read the
[security guide](docs/security/README.md).

## Backup and recovery

Automatic setup is under **Settings → Data & recovery → Automatic backups**:
choose a folder, save the recovery file, and turn on the schedule. If a private
vault is configured, authorize access using its PIN when prompted. Manual backup
and restore also support inline vault authorization; disabling the vault is
unnecessary.

Keep the recovery file somewhere accessible independently of the app and separate
from the backup folder. The vault PIN is not a substitute for the backup password
or recovery credential. Anyone with both an archive and its recovery credential
can decrypt that backup.

Scheduled backups can run without opening RuN, but their timing is approximate.
Force-stop, battery restrictions, low storage and an unavailable destination can
delay them. Check the last successful backup in settings. A backup kept only on
the same phone does not protect against losing that phone. See the
[backup and archive references](docs/features/README.md) for recovery behavior.

## Download and installation

Download the **universal APK** from [GitHub Releases](https://github.com/Nrajesh0/Ayva/releases/latest).
It supports **Android 12 and newer (API 31+)** and includes ARM and x86 native
libraries, bundled dictionary content and artwork. The target SDK is Android 15
(API 35); a newer Android release can still run the app.

Install the APK through Android's package installer, allowing installation from
the source you use when prompted. Official updates must retain the same signing
identity to install over an existing copy.

## Build from source

Use **JDK 21**, the checked-in **Gradle 8.9 wrapper**, Android SDK platform **35**
and build-tools **34.0.0**. Set `ANDROID_HOME` to your SDK location. The first build
requires access to dependency repositories.

```sh
./gradlew :app:assembleDebug --no-daemon --max-workers=1
```

The debug APK is written under `app/build/outputs/apk/debug/`. A fresh checkout
can use the standard generated debug key; private release credentials are not
required.

To assemble and package a release APK:

```sh
./gradlew :app:assembleRelease --no-daemon --max-workers=1
python3 tools/package_release_apks.py
```

The packaging script verifies the universal APK's metadata and native
architectures, then writes the APK, manifest and SHA-256 checksum under
`build/release-apks/`. Release assembly is unsigned without a configured keystore;
packaging alone does not sign it or make it an official release. Keep signing keys
and credentials outside the repository.

Run the JVM regression suite separately from APK assembly:

```sh
./gradlew :app:testDebugUnitTest --no-daemon --max-workers=1
```

GitHub validation also runs lint, source guards and native data-safety checks on
Android API 31, 34, 35 and 36 emulators. Development tests do not run when someone
opens the installed app. See the [build and test guide](docs/development/BUILD_AND_TEST.md)
for focused checks, CI scheduling and device-testing limits.

## Documentation

- [Documentation index](docs/README.md)
- [Feature implementation guide](docs/features/README.md)
- [Assistant scope and history](docs/assistant/README.md)
- [Security reviews and evidence](docs/security/README.md)
- [Recorded release notes](docs/releases/CHANGELOG.md)

## License and branding

The project is licensed under the **GNU Affero General Public License v3.0
(AGPLv3)**. See [LICENSE](LICENSE) for the terms, warranty disclaimer and limitation
of liability. Bundled dependencies and datasets retain their applicable licences
and notices; see the relevant source and [dictionary guide](docs/assistant/DICTIONARY_DATA.md).

The **RuN** name, logo, app icons and visual branding are governed separately by
[TRADEMARK.md](TRADEMARK.md). The code licence does not grant unrestricted use of
those brand assets.
