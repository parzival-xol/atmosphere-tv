package com.gathin.tvhome;

/** Pure weather classification and visibility rules; no Android or network work. */
final class WeatherModel {
    static final int CLEAR = 0, PARTLY = 1, CLOUDY = 2, FOG = 3, RAIN = 4, SNOW = 5, STORM = 6;
    static final long REFRESH_MS = 30 * 60_000L;
    static final long MAX_AGE_MS = 90 * 60_000L;
    static int kind(int code) {
        switch (code) {
            case 0: return CLEAR;
            case 1: case 2: return PARTLY;
            case 3: return CLOUDY;
            case 45: case 48: return FOG;
            case 51: case 53: case 55: case 56: case 57:
            case 61: case 63: case 65: case 66: case 67:
            case 80: case 81: case 82: return RAIN;
            case 71: case 73: case 75: case 77: case 85: case 86: return SNOW;
            case 95: case 96: case 99: return STORM;
            default: return -1;
        }
    }

    static boolean fresh(long now, long fetched, long maxAge) {
        return fetched > 0 && now >= fetched && now - fetched < maxAge;
    }

    static boolean visible(boolean resumed, boolean appFocused, boolean internet, boolean fresh) {
        return resumed && !appFocused && internet && fresh;
    }

    static String description(int kind, boolean day) {
        switch (kind) {
            case CLEAR: return day ? "Sunny" : "Clear night";
            case PARTLY: return "Partly cloudy";
            case CLOUDY: return "Cloudy";
            case FOG: return "Foggy";
            case RAIN: return "Rainy";
            case SNOW: return "Snowy";
            case STORM: return "Thunderstorms";
            default: return "Local weather";
        }
    }

    private WeatherModel() { }
}
