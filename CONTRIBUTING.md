# Contributing to Atmosphere TV

Keep changes focused and describe the behavior a TV user will see. Include the device model, Android version, firmware build and how the change was verified.

For UI changes, capture the real TV if possible. Test D-pad edges, Home, Back, app transitions and the app grid. Do not assume injected keys establish that a physical remote shortcut works. If a helper is involved, test held-volume behavior too.

Run from `launcher/`:

```sh
./gradlew testReleaseUnitTest lintRelease assembleRelease
```

Do not include device backups, serials, account details, Wi-Fi names, location coordinates, ADB credentials, tokens, signing keys or build caches in a contribution. Keep device-specific package disable lists out of generic installation code.

This is a separate fork. The upstream cleanyourtv project accepts large restyles and network-enabled launchers through its showcase, not as a replacement for its small reference launcher. Read https://github.com/Gathin23/cleanyourtv/blob/main/CONTRIBUTING.md before proposing an upstream change.

Contributions to this repository use its MIT license; preserve upstream and third-party notices.
