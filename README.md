# CloneKu

Run multiple independent copies of the same Android app on one device, without root.

Free and unrestricted: **no ads, no subscription, no limit on the number of clones.**

## Install

Grab the latest APK from [Releases](../../releases).

- **`CloneKu_*_arm64-v8a-release.apk`** — use this on virtually any modern phone.
- `CloneKu_*_universal-release.apk` — fallback if the above refuses to install.
- `CloneKu_*_armeabi-v7a-release.apk` — very old 32-bit devices only.

### Automatic updates via Obtainium

Add this repository's URL in [Obtainium](https://github.com/ImranR98/Obtainium) and
it will track new releases automatically.

Android's Play Protect flags app-cloning engines as a category. You will likely have
to tap **More details → Install anyway**.

## Setup after install

1. Grant **All files access** when prompted. Cloned apps cannot write their data
   without it, and large downloads inside clones will fail.
2. Tap **+** to clone an app. Swipe sideways to reach another space and clone the
   same app again for a second independent copy.
3. For apps needing Google sign-in: **⋮ → Setting → Others → GMS Manager**, then
   install Google services into that space.

## Credits

CloneKu is a fork of [ALEX5402/NewBlackbox](https://github.com/ALEX5402/NewBlackbox),
a maintained continuation of the BlackBox virtual engine. All of the hard
virtualization work belongs to those projects.

Licensed under the Apache License 2.0 — see [LICENSE](LICENSE). Modifications made by
this fork are listed in [NOTICE](NOTICE) and explained in [MAXCLONER.md](MAXCLONER.md).

## What this fork changes

The upstream engine is already free and ungated — there was no paywall to remove. The
substantive change is **split APK (Android App Bundle) support**:

Modern Play Store apps ship as a base APK plus separate split APKs, and for a bundle
app the base APK contains **no native code at all** — every `.so` lives in
`split_config.<abi>.apk`. Upstream cloned only the base APK, so cloned bundle apps
received zero native libraries and no split resources. This fork installs every split
and extracts native libraries from all of them.

Everything else is rebranding. Details in [MAXCLONER.md](MAXCLONER.md).

## Building

Requires **JDK 21** (not 17, despite what upstream docs say — the build targets Java 21),
Android SDK 35, and NDK `29.0.13846066`.

```bash
./gradlew assembleDebug
```

Release builds read signing credentials from `keystore.properties` (gitignored); without
it they fall back to the debug key so a fresh clone still builds. CI builds are signed
from repository secrets — see [.github/workflows/release.yml](.github/workflows/release.yml).

## Known limitations

These are properties of userspace virtualization, not bugs in this fork, and they affect
commercial cloners equally:

- Apps enforcing Play Integrity / SafetyNet — banking, payments, some messaging — detect
  the sandbox and refuse to run. No userspace engine reliably defeats this.
- Google sign-in inside a clone often fails even with GMS installed, because Google
  validates the calling app's signature and device integrity.
- Android 15 support is recent and not thoroughly tested; individual apps may crash.
- `targetSdkVersion` is pinned to 28 so the engine can reach the hidden APIs it needs.
  Raising it breaks the engine, and it also means this cannot be published on Google
  Play. Sideload only.

## Warning

Cloned apps run inside CloneKu's sandbox, so the host can observe what they do. Do not
put banking, payment, or primary email accounts inside it. Services such as WhatsApp and
Snapchat can suspend accounts used in cloned environments — test with an account you can
afford to lose. Uninstalling CloneKu deletes every clone and all data inside them.
