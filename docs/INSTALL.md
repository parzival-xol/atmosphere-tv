# Build, install and recover

Tested on a Hisense SmartTV 4K FFM (`songshan`), Android 11, firmware `RTT2.220118.001`. Android 8.0 / API 26 is the minimum. Other TVs need their own verification. These instructions leave stock Home enabled.

## Build

Install JDK 17 or 18, Android SDK platform 34 and build-tools 34.0.0. Set `JAVA_HOME` and `ANDROID_HOME`, then run from `launcher/`:

```sh
./gradlew --no-daemon --console=plain testReleaseUnitTest lintRelease assembleRelease
```

On Windows use `gradlew.bat`. The APK is `launcher/app/build/outputs/apk/release/app-release.apk`.

The release variant is minified and signed with the local Android debug keystore for sideloading. No signing key is included here. Another machine normally generates a different key, which cannot update an existing same-package installation. Keep the signing key used for your installation if you intend to maintain updates.

## Back up and connect

Before changing defaults, privately record your original Home component, package states, settings and firmware. Do not copy another TV's disable list. Temporarily enable debugging and accept the authorization prompt on the TV. On some models, USB debugging can interrupt USB Ethernet; check the connection first.

From the repository root on Windows:

```powershell
python -m venv tools/venv
& './tools/venv/Scripts/python.exe' -m pip install -r tools/requirements.txt
$env:TV_HOST = '<TV_IP>'
& './tools/venv/Scripts/python.exe' -I tools/tv.py --probe
& './tools/venv/Scripts/python.exe' -I tools/tv.py 'cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME'
```

On macOS/Linux, use `python3` and `tools/venv/bin/python`. `TV_HOST` can include a port. No TV address or ADB credential is shipped in this repository.

## Install and verify before choosing Home

Adjust the local APK path if using a downloaded release:

```powershell
& './tools/venv/Scripts/python.exe' -I tools/b64push.py launcher/app/build/outputs/apk/release/app-release.apk /data/local/tmp/atmosphere-tv.apk
& './tools/venv/Scripts/python.exe' -I tools/tv.py 'pm install -r /data/local/tmp/atmosphere-tv.apk'
& './tools/venv/Scripts/python.exe' -I tools/tv.py 'am start -W -n com.gathin.tvhome/.MainActivity'
& './tools/venv/Scripts/python.exe' -I tools/tv.py 'rm -f /data/local/tmp/atmosphere-tv.apk'
```

Confirm the launcher appears and D-pad navigation works. To allow Android-provided approximate location, optionally grant:

```powershell
& './tools/venv/Scripts/python.exe' -I tools/tv.py 'pm grant com.gathin.tvhome android.permission.ACCESS_COARSE_LOCATION'
```

Without a recent permitted Android location, weather uses public-IP geolocation. See [PRIVACY.md](PRIVACY.md).

After testing, choose Home through Android's default-app settings if available, or run:

```powershell
& './tools/venv/Scripts/python.exe' -I tools/tv.py 'cmd package set-home-activity --user 0 com.gathin.tvhome/.MainActivity'
& './tools/venv/Scripts/python.exe' -I tools/tv.py 'input keyevent KEYCODE_HOME'
& './tools/venv/Scripts/python.exe' -I tools/tv.py 'dumpsys activity activities'
& './tools/venv/Scripts/python.exe' -I tools/tv.py --screenshot shots/home.png
```

Check the actual focused activity and screen, not only a command's success text. Stock Home stays enabled. Some firmware ignores the selected default or gives the setup wizard priority. Investigate the exact model with the upstream guide before changing Home-handler packages.

The inherited `launcher/build-and-install.ps1` and `.sh` helpers can build and install too. Pass `-StockLauncher none` on Windows or `STOCK_LAUNCHER=none` on Unix to leave stock Home enabled. Those helpers automatically grant fine location; use the manual route above if you want to control location permissions yourself. Remote helpers remain optional: test held-volume behavior before enabling them.

## Verify and recover

Check Home, Back, app launch/return, HDMI, Live TV, physical remote buttons, held volume and a full restart. Injected keys do not establish that a physical button works. Protected video/input screens can produce blank screenshots.

Restore the prior launcher through Android's Home settings, or use the exact component you recorded with `cmd package set-home-activity --user 0 <ORIGINAL_COMPONENT>`. If you separately disabled its package, re-enable that package first. The manual commands above never disable it. Restore any other package changes from your own backup.

When maintenance is complete, revoke debugging authorizations, turn USB/Network/ADB/Wireless debugging off and restart the TV. Check from the computer:

```powershell
& './tools/venv/Scripts/python.exe' -I tools/adb-closed.py '<TV_IP>'
```

An explicit refused connection establishes closure. A timeout is inconclusive even if the helper exits successfully. If you use USB Ethernet, reconnect the adapter and confirm Ethernet is restored.
