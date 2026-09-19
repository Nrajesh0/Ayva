# Focus by Rj

Focus by Rj is a privacy-first, fully offline App Locker & Focus Tracker.

## F-Droid Compatibility
This application is fully compatible with F-Droid and meets all F-Droid Inclusion Requirements:
- **100% Free and Open Source** (MIT Licensed)
- **No Proprietary Dependencies** (No Google Play Services, Firebase, etc.)
- **No Trackers or Analytics** (Completely offline architecture)
- **Fastlane Metadata Included** (Available in `fastlane/metadata/android/en-US/`)

### Build Instructions
To build the application locally:
```bash
./gradlew assembleRelease
```

To submit to F-Droid, this repository can be included in `fdroiddata` as it contains no proprietary blobs and uses standard Gradle build tools.
