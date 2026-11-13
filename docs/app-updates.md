# Publishing StudentCC app updates

StudentCC checks the latest published stable GitHub release when the app opens and when it returns to the foreground after an hour. Users see the release notes, choose Download update, and approve installation in Android. If Android asks to allow installs from StudentCC, users grant that permission and return to StudentCC to open the installer. A download that finishes while the app is closed resumes its installer handoff the next time the app opens.

Each release must use a greater Android `versionCode` and the same application ID and signing key as the previous installation. Increase `versionName` from the previous public release as well. The final release uses `21` / `1.6.1`, following public version `1.6.0` and phone test build `20`. Android uses the version code to decide whether an APK can update an existing installation and cannot install an update signed with a different key.

The build workflow runs on pushes to `main` and can also be run manually with GitHub Actions. It tests the app, builds the signed release APK, and creates a new stable release when its version tag does not already exist. It validates the new version against the latest release. If the tag already exists, it uploads build artifacts without changing the existing release. Update both version fields to publish a new version.

The app defaults to `https://studentcc-statistics.salmanmalvasi-studentcc.workers.dev`. Set the repository Actions variable `STUDENTCC_STATS_BASE_URL` to override this with a different deployed HTTPS statistics API URL. The workflow passes this value into the release build. The existing Firebase Actions secrets must also be configured.

Release signing uses a private keystore outside the checkout. Local builds read
`STUDENTCC_KEYSTORE_PATH`, `STUDENTCC_KEYSTORE_PASSWORD`, `STUDENTCC_KEY_ALIAS` and
`STUDENTCC_KEY_PASSWORD`, or the private user Gradle properties
`studentccKeystorePath`, `studentccKeystorePassword`, `studentccKeyAlias` and
`studentccKeyPassword`. Keep those values out of repository files.

The trusted main release job restores its key from the protected Actions secrets
`ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS` and
`ANDROID_KEY_PASSWORD`. Production Firebase generation requires
`FIREBASE_API_KEY`, `FIREBASE_PROJECT_ID`, `FIREBASE_PROJECT_NUMBER`, `FIREBASE_APP_ID`
and `FIREBASE_STORAGE_BUCKET`; missing or placeholder values stop the release.
Pull requests and branch verification use mock Firebase settings and a debug build,
without access to release signing credentials. Backend implementation and
administrative files are maintained outside this app repository.

Generated assets include:

- `StudentCC.apk`, the signed app.
- `about.json`, containing the exact version code, version name, tag, APK URL and SHA-256 checksum.
- `StudentCC.apk.sha256`, a checksum for manual verification.

The app gets the release and its APK from the same tag, verifies the file checksum when available, and checks the downloaded APK's actual application ID and Android version before opening the installer. GitHub's release description provides the notes shown in the app.

For a release created manually in GitHub, attach the signed APK and the matching `about.json` from the build artifacts, use a numeric stable tag such as `v1.6.2`, and mark it as the latest stable release. Drafts and prereleases do not trigger stable app updates. A manually uploaded release with no `about.json` is also supported when it has a single APK, or an APK named `StudentCC.apk`, and a newer numeric version tag. Its actual Android version code is still checked after download.

Keep `about.json` with the first public update after local testing. An earlier test APK displayed `1.7.0` with version code `13`; metadata lets that installation correctly recognize `1.6.1` with version code `21` as newer despite the display name. The numeric-tag fallback alone would not offer that particular update. Build `21` also updates local test installations through build `20`.

Existing installed versions keep using the old updater until users install this version. Older versions already support releases that contain a matching `about.json` with an increased version code. Keep including this metadata so those installations can discover the first update.
