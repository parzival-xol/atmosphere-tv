# Customizing Atmosphere TV

## Dock and tile order

Open the up/down-arrow Rearrange button on the top bar. Press OK to pick up a tile, move it with the arrows, then OK to place it. Back finishes and saves. Holding OK on a tile also exposes the Move action.

Default dock packages and order are in `launcher/app/src/main/res/values/config.xml`, under `dock_apps`; `columns` controls the number of slots. Missing packages are skipped or filled according to `fill_dock`. Saved on-TV rearrangements take precedence until the configured dock changes.

## Themes

A theme can override supported configuration values and optionally provide `wallpaper.jpg`. See `themes/README.md`. A wallpaper appears when the weather overlay is inactive. The weather renderer itself requires this fork's APK; copying theme JSON into the upstream launcher does not add weather.

## Remote buttons

The tested Hisense remote sends keycode 172 for its TV icon, 284 for Apps, and 178 for Input. The supplied config maps those to Live TV, the app grid, and the input menu. These are observations for one firmware, not universal keycodes. Discover and test your own remote first.

Accessibility helpers are optional. They are not enabled by the safe installation script in this repository. On the tested TV, enabling InputKeyService loses held-volume repeat; repeated volume taps still work. Granting WRITE_SECURE_SETTINGS is only needed if you deliberately use the helper-repair feature. Consult the upstream helper documentation before enabling it:
https://github.com/Gathin23/cleanyourtv/blob/main/docs/TROUBLESHOOTING.md

Live TV uses `live_tv_package` in config.xml. The default MediaTek package fits the tested TV. Preserve the TV input/tuner, HDMI, remote/pairing, keyboard and Android core services on other firmware.

## Weather art and motion

The native renderer is `WeatherBackdropView.java`, the foreground network/cache controller is `WeatherController.java`, and WMO classification/visibility rules are in `WeatherModel.java`, under `launcher/app/src/main/java/com/gathin/tvhome/`. The cloud texture and gradients are cached; avoid per-frame allocations or expensive blur/video backgrounds.
