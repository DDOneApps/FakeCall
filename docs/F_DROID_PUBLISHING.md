# F-Droid Publishing Guide

This repository now contains the app-store metadata F-Droid expects in `metadata/en-US/`:

- `title.txt`
- `short_description.txt`
- `full_description.txt`
- `changelogs/<versionCode>.txt`
- `images/phoneScreenshots/*.png`

F-Droid documents this source-repository metadata layout as valid at `metadata/<locale>/` and `fastlane/metadata/android/<locale>/`. Keeping the metadata in this repo is preferred because F-Droid can import the text and screenshots from the released source tag.

## Current App Coordinates

- Package ID: `com.upnp.fakeCall`
- Version name: `2.5`
- Version code: `25`
- Source repository: `https://github.com/DDOneApps/FakeCall`
- License field for F-Droid: `GPL-3.0-only`
- Suggested category: `Phone & SMS`
- AutoName detected by fdroidserver: `Fake Call`
- Gradle project path: repository root with Android module `app`
- Upstream binary URL: `https://github.com/DDOneApps/FakeCall/releases/download/v%v/app-release-full.apk`
- APK signing certificate SHA-256: `bfa2a6906d3c2f650717c4e0c2f5ffc47c60fa7202bc3a2c7429f8a9e59bde80`

If you later add explicit source-file license headers saying "GPL-3.0-or-later", update the F-Droid license field accordingly.

## Before You Submit

1. Make sure the public GitHub repository contains the exact source code for the release.
2. Make sure no release-only changes exist outside source control.
3. Confirm that dependencies are free software and buildable from command-line Gradle.
4. Confirm that all screenshots and icons are your own work or are under a F-Droid-compatible free license.
5. Decide how to handle the in-app GitHub update checker. The app currently requests `INTERNET` and checks GitHub releases. F-Droid reviewers may accept it, request an Anti-Feature note, or ask for an F-Droid build variant where the update checker is disabled.
6. Keep the signing key safe. The file `D:\Android certs\Zert` appears to be a PKCS#12/private signing container, not a public certificate. Do not commit it, upload it, send it to F-Droid, or paste its password into public places. Keep at least two encrypted backups in places you control. Losing this key means future APK updates signed by you may no longer upgrade over previous APKs.
7. Create and push a release tag that matches the metadata template:

```bash
git tag -a v2.5 -m "FakeCall 2.5"
git push origin v2.5
```

If you use a different release commit, update `fdroid/metadata/com.upnp.fakeCall.yml` before copying it into `fdroiddata`. F-Droid reviewers prefer the immutable full commit hash in each build entry instead of a tag name. For version `2.5`, the current build commit is `517acbf0862472ee08e6bd3e822c7ea22d72cc1b`.

## Recommended Submission Path

The quickest route is a merge request to the official `fdroiddata` repository.

1. Create a GitLab account if you do not already have one.
2. Fork `https://gitlab.com/fdroid/fdroiddata`.
3. Clone your fork:

```bash
git clone https://gitlab.com/<your-user>/fdroiddata.git
cd fdroiddata
```

4. Copy this repository's template into the fork:

```bash
mkdir -p metadata
cp /path/to/Phony/fdroid/metadata/com.upnp.fakeCall.yml metadata/com.upnp.fakeCall.yml
```

On Windows PowerShell:

```powershell
New-Item -ItemType Directory -Force metadata
Copy-Item D:\Users\TestUser\AndroidStudioProjects\Phony\fdroid\metadata\com.upnp.fakeCall.yml metadata\com.upnp.fakeCall.yml
```

5. Install fdroidserver locally. The common development setup is Python plus fdroidserver from your distribution packages or pipx:

```bash
pipx install fdroidserver
```

6. Validate the metadata:

```bash
fdroid readmeta
fdroid rewritemeta com.upnp.fakeCall
fdroid lint com.upnp.fakeCall
```

7. Test the build:

```bash
fdroid build -v -l com.upnp.fakeCall
```

8. Commit and push the fdroiddata change:

```bash
git add metadata/com.upnp.fakeCall.yml
git commit -m "Add FakeCall"
git push origin HEAD
```

9. Open a merge request against `fdroid/fdroiddata` on GitLab.

In the merge request description, include:

- App name: FakeCall
- Package ID: `com.upnp.fakeCall`
- Source repository URL
- Release tag being built, for example `v2.5`
- Short note that the app uses Android Telecom to simulate incoming calls
- Any review note about the GitHub update checker, if you keep it enabled

## Alternative Submission Queue

If you do not want to maintain the fdroiddata metadata yourself, open an issue in the F-Droid Submission Queue and include the same details. This is easier, but usually slower.

## Updating After Inclusion

For future releases:

1. Increase `versionCode` and `versionName` in `app/build.gradle.kts`.
2. Add `metadata/en-US/changelogs/<newVersionCode>.txt`.
3. Commit the release.
4. Tag the release, for example `v2.6`.
5. Push the branch and tag.
6. After F-Droid's update checker sees the tag, it can add the next build automatically if `AutoUpdateMode: Version` and `UpdateCheckMode: Tags` continue to match the release pattern.

The metadata template also includes `UpdateCheckData` so fdroidserver reads `versionCode` and `versionName` from `app/build.gradle.kts`.

If `fdroid rewritemeta` or `checkupdates` changes the copied `metadata/com.upnp.fakeCall.yml`, commit the rewritten result. The template in this repository already includes the currently expected fdroidserver formatting: unquoted `UpdateCheckData` and `AutoName: Fake Call`.

## Reproducible Build Fields

The F-Droid metadata includes:

```yaml
Binaries: https://github.com/DDOneApps/FakeCall/releases/download/v%v/app-release-full.apk
AllowedAPKSigningKeys: bfa2a6906d3c2f650717c4e0c2f5ffc47c60fa7202bc3a2c7429f8a9e59bde80
```

`Binaries` points fdroidserver at the upstream signed APK. `%v` is replaced with the build version name, so version `2.5` resolves to:

```text
https://github.com/DDOneApps/FakeCall/releases/download/v2.5/app-release-full.apk
```

`AllowedAPKSigningKeys` is the lower-case SHA-256 fingerprint of the APK signing certificate. It was verified from the public `v2.5` `app-release-full.apk` using Android's APK signature parser. If you ever intentionally rotate the signing key, update this value and explain the key change in the F-Droid merge request.

To verify the value yourself with Android SDK build-tools:

```bash
apksigner verify --print-certs app-release-full.apk
```

Look for `Signer #1 certificate SHA-256 digest`, remove the colons if your tool prints them, and make it lower-case.

## Useful Official Documentation

- Inclusion How-To: https://f-droid.org/en/docs/Inclusion_How-To/
- Inclusion Policy: https://f-droid.org/en/docs/Inclusion_Policy/
- Build Metadata Reference: https://f-droid.org/en/docs/Build_Metadata_Reference/
- Descriptions, Graphics, and Screenshots: https://f-droid.org/en/docs/All_About_Descriptions_Graphics_and_Screenshots/
