# Weather network behavior

The added weather feature runs only while the launcher activity is resumed. It does not add a background weather service.

| Service | Purpose | Information sent |
|---|---|---|
| Android location provider | Obtain a recent location, when permitted | Managed by Android and the configured location provider |
| `https://ipwho.is/` | Approximate location if no recent Android fix is available | A normal HTTPS request exposes the public IP to the provider; the response stays in process memory |
| `https://api.open-meteo.com/v1/forecast` | Current temperature, WMO condition code and day/night flag | Latitude and longitude, rounded to four decimal places, plus forecast parameters |

Normal weather refresh is about every 30 minutes while Home is visible. When no fresh data exists, two startup failures can retry after 20 seconds; later failures wait five minutes. Requests have bounded timeouts and response size. There is no location history or account integration in this feature, and no telemetry was added for it.

The app-private `home_weather_cache` stores one latest temperature, condition, day/night flag and UTC update time. It stores no coordinates, IP, city or history. Fresh cached data can display once Android reports validated internet; cached weather older than 90 minutes is hidden. Clearing app data removes the cache and also resets saved launcher preferences.

The fallback can identify an ISP or VPN endpoint rather than the house. The current weather feature is built in; there is no on-TV switch to turn off its network requests. Disconnecting the TV hides weather, and the original background remains. If you need a strictly offline launcher, use the upstream reference launcher or remove the weather controller in your own build.

Terms: https://open-meteo.com/en/pricing and https://ipwhois.io/documentation . Providers have their own processing policies and usage limits. Commercial use may need different service access. This document describes the added default weather path, not networking performed independently by Android, installed streaming apps, or optional third-party module cards.
