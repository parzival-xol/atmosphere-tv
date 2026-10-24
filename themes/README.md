# Theme overrides

Supported theme keys are described in the upstream guide: https://github.com/Gathin23/cleanyourtv/blob/main/themes/README.md . This fork includes a minimal five-column example in `themes/five-app-dock/config.json`.

Push a theme with the bundled `tools/theme.ps1` or `tools/theme.sh` after configuring its ADB client environment. A theme contains `config.json` and can contain `wallpaper.jpg`. Keep keys and TV backups outside the theme folder. Wallpapers show where the weather overlay is inactive.

Weather, neutral Home/Back navigation and inline top-bar labels are fork code; they are not portable theme-only features.
