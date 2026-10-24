package com.gathin.tvhome;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.content.SharedPreferences;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Consumer;

/** Foreground-only, bounded weather requests. No background service, location history or polling loop. */
final class WeatherController implements AutoCloseable {
    private static final String TAG = "HomeWeather";
    private final Activity activity;
    private final WeatherBackdropView view;
    private final Consumer<Boolean> visibility;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final ConnectivityManager connectivity;
    private final LocationManager locations;
    private final SharedPreferences cache;
    private final boolean reducedMotion;
    private boolean resumed, appFocused = true, online, fetching, registered, showing, locationResolved;
    private int generation, kind = -1;
    private boolean day;
    private double temperature;
    private long lastSuccess, lastAttempt, lastDataUtc, retryAfter;
    private int failures;
    private Future<?> request;
    private volatile HttpURLConnection connection;
    private LocationListener locationListener;
    private final Runnable locationTimeout = this::useIpLocation;
    private final Runnable refresh = this::refreshIfDue;
    private final ConnectivityManager.NetworkCallback callback = new ConnectivityManager.NetworkCallback() {
        @Override public void onAvailable(Network network) { connectivityChanged(); }
        @Override public void onLost(Network network) { connectivityChanged(); }
        @Override public void onCapabilitiesChanged(Network n, NetworkCapabilities capabilities) { connectivityChanged(); }
    };

    WeatherController(Activity activity, WeatherBackdropView view, boolean reducedMotion, Consumer<Boolean> visibility) {
        this.activity = activity;
        this.view = view;
        this.reducedMotion = reducedMotion;
        this.visibility = visibility;
        connectivity = activity.getSystemService(ConnectivityManager.class);
        locations = activity.getSystemService(LocationManager.class);
        cache = activity.getSharedPreferences("home_weather_cache", Activity.MODE_PRIVATE);
        try {
            int cachedKind = cache.getInt("condition", -1);
            double cachedTemperature = cache.getFloat("temperature_c", Float.NaN);
            if (cachedKind >= 0 && cachedKind <= WeatherModel.STORM && Double.isFinite(cachedTemperature)) {
                kind = cachedKind; temperature = cachedTemperature;
                day = cache.getBoolean("day", true);
                lastDataUtc = cache.getLong("updated_utc", 0);
                view.setWeather(kind, day, temperature);
            }
        } catch (RuntimeException ignored) { kind = -1; }
    }

    void start() {
        if (resumed) return;
        resumed = true;
        if (connectivity != null) {
            try { connectivity.registerDefaultNetworkCallback(callback, main); registered = true; }
            catch (RuntimeException ignored) { }
        }
        connectivityChanged();
    }

    void setAppFocused(boolean focused) { appFocused = focused; render(); }

    private void connectivityChanged() {
        if (!resumed) return;
        NetworkCapabilities caps = null;
        try {
            if (connectivity != null) caps = connectivity.getNetworkCapabilities(connectivity.getActiveNetwork());
        } catch (RuntimeException ignored) { }
        online = caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
        if (!online) cancelRequest();
        render();
        if (online) refreshIfDue();
    }

    private void refreshIfDue() {
        main.removeCallbacks(refresh);
        if (!resumed || !online) return;
        long now = SystemClock.elapsedRealtime();
        long due = retryAfter > 0 ? retryAfter : Math.max(lastAttempt + 5 * 60_000L,
                kind >= 0 ? lastSuccess + WeatherModel.REFRESH_MS : 0);
        if (!fetching && (lastAttempt == 0 || now >= due)) {
            fetching = true;
            lastAttempt = now;
            retryAfter = 0;
            generation++;
            locationResolved = false;
            resolveLocation();
        }
        main.postDelayed(refresh, 60_000L); // a local timer; network requests remain capped at 30 minutes
    }

    @SuppressLint("MissingPermission")
    private void resolveLocation() {
        boolean permission = activity.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || activity.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        if (locations != null && permission) {
            try {
                Location recent = null;
                for (String provider : locations.getProviders(true)) {
                    Location candidate = locations.getLastKnownLocation(provider);
                    if (candidate != null && (recent == null || candidate.getElapsedRealtimeNanos() > recent.getElapsedRealtimeNanos())) recent = candidate;
                }
                if (recent != null && SystemClock.elapsedRealtimeNanos() >= recent.getElapsedRealtimeNanos()
                        && SystemClock.elapsedRealtimeNanos() - recent.getElapsedRealtimeNanos() < 10 * 60_000_000_000L) {
                    fetchAt(recent.getLatitude(), recent.getLongitude());
                    return;
                }
                if (locations.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                    locationListener = new LocationListener() {
                        @Override public void onLocationChanged(Location value) {
                            if (!resumed || !fetching) return;
                            clearLocationRequest();
                            fetchAt(value.getLatitude(), value.getLongitude());
                        }
                        @Override public void onProviderEnabled(String provider) { }
                        @Override public void onProviderDisabled(String provider) { useIpLocation(); }
                        @Override public void onStatusChanged(String provider, int status, Bundle extras) { }
                    };
                    locations.requestSingleUpdate(LocationManager.NETWORK_PROVIDER, locationListener, Looper.getMainLooper());
                    main.postDelayed(locationTimeout, 8_000L);
                    return;
                }
            } catch (RuntimeException ignored) { clearLocationRequest(); }
        }
        useIpLocation();
    }

    @SuppressLint("MissingPermission")
    private void clearLocationRequest() {
        main.removeCallbacks(locationTimeout);
        if (locationListener != null && locations != null) {
            try { locations.removeUpdates(locationListener); } catch (RuntimeException ignored) { }
            locationListener = null;
        }
    }

    private void useIpLocation() {
        clearLocationRequest();
        if (!resumed || !online || !fetching || locationResolved) return;
        locationResolved = true;
        final int run = generation;
        request = worker.submit(() -> {
            try {
                // Approximate location when the TV has no network-location fix. No account, key or saved IP.
                JSONObject geo = getJson("https://ipwho.is/");
                if (!geo.optBoolean("success", false)) throw new java.io.IOException("Location lookup unavailable");
                double latitude = geo.getDouble("latitude"), longitude = geo.getDouble("longitude");
                fetchWeather(run, latitude, longitude);
            } catch (Exception failure) { failed(run, failure); }
        });
    }

    private void fetchAt(double latitude, double longitude) {
        clearLocationRequest();
        if (locationResolved || !resumed || !online || !fetching) return;
        locationResolved = true;
        final int run = generation;
        request = worker.submit(() -> {
            try { fetchWeather(run, latitude, longitude); }
            catch (Exception failure) { failed(run, failure); }
        });
    }

    private void fetchWeather(int run, double latitude, double longitude) throws Exception {
        if (!Double.isFinite(latitude) || !Double.isFinite(longitude) || Math.abs(latitude) > 90 || Math.abs(longitude) > 180) {
            throw new IllegalArgumentException("Invalid weather coordinates");
        }
        if (Thread.currentThread().isInterrupted()) return;
        String endpoint = String.format(Locale.US,
                "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f&current=temperature_2m,weather_code,is_day&forecast_days=1&timezone=auto",
                latitude, longitude);
        JSONObject current = getJson(endpoint).getJSONObject("current");
        int nextKind = WeatherModel.kind(current.getInt("weather_code"));
        boolean nextDay = current.getInt("is_day") == 1;
        double nextTemperature = current.getDouble("temperature_2m");
        if (nextKind < 0 || !Double.isFinite(nextTemperature)) throw new IllegalArgumentException("Invalid weather response");
        main.post(() -> {
            if (!resumed || generation != run) return;
            kind = nextKind; day = nextDay; temperature = nextTemperature;
            lastSuccess = SystemClock.elapsedRealtime(); fetching = false;
            lastDataUtc = System.currentTimeMillis(); failures = 0; retryAfter = 0;
            // One latest conditions snapshot only: no coordinates, IP address, city or location history.
            cache.edit().putInt("condition", kind).putFloat("temperature_c", (float)temperature)
                    .putBoolean("day", day).putLong("updated_utc", lastDataUtc).apply();
            main.removeCallbacks(refresh);
            main.postDelayed(refresh, WeatherModel.REFRESH_MS);
            view.setWeather(kind, day, temperature);
            Log.i(TAG, "Current weather updated; condition=" + kind + ", day=" + day);
            render();
        });
    }

    private JSONObject getJson(String endpoint) throws Exception {
        HttpURLConnection active = (HttpURLConnection)new URL(endpoint).openConnection();
        connection = active;
        try {
            active.setConnectTimeout(6_000); active.setReadTimeout(6_000);
            active.setRequestProperty("Accept", "application/json");
            active.setRequestProperty("User-Agent", "HisensePersonalHome/1.0");
            int status = active.getResponseCode();
            if (status != 200) throw new java.io.IOException("HTTP " + status + " at " + active.getURL().getHost());
            try (InputStream in = active.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[2048]; int read;
                while ((read = in.read(buffer)) != -1) {
                    if (Thread.currentThread().isInterrupted()) throw new java.io.InterruptedIOException();
                    if (out.size() + read > 65_536) throw new java.io.IOException("Weather response too large");
                    out.write(buffer, 0, read);
                }
                return new JSONObject(out.toString(StandardCharsets.UTF_8.name()));
            }
        } finally { active.disconnect(); if (connection == active) connection = null; }
    }

    private void failed(int run, Exception failure) {
        main.post(() -> {
            if (!resumed || generation != run) return;
            fetching = false;
            failures++;
            boolean freshData = kind >= 0 && WeatherModel.fresh(System.currentTimeMillis(), lastDataUtc, WeatherModel.MAX_AGE_MS);
            long delay = !freshData && failures <= 2 ? 20_000L : 5 * 60_000L;
            retryAfter = SystemClock.elapsedRealtime() + delay;
            main.removeCallbacks(refresh);
            main.postDelayed(refresh, delay);
            String reason = failure.getMessage();
            if (reason == null || !reason.startsWith("HTTP ")) reason = failure.getClass().getSimpleName();
            Log.i(TAG, "Weather unavailable; preserving the existing backdrop (" + reason + ")");
            render();
        });
    }

    private void render() {
        if (!resumed) {
            view.animate().cancel();
            view.setAlpha(0f);
            view.setVisibility(View.GONE);
            view.setPlaying(false);
            if (showing) { showing = false; visibility.accept(false); }
            return;
        }
        boolean active = WeatherModel.visible(resumed, appFocused, online,
                kind >= 0 && WeatherModel.fresh(System.currentTimeMillis(), lastDataUtc, WeatherModel.MAX_AGE_MS));
        if (showing == active) return;
        showing = active;
        visibility.accept(active);
        view.animate().cancel();
        if (reducedMotion || !resumed) {
            view.setAlpha(active ? 1f : 0f);
            view.setVisibility(active ? View.VISIBLE : View.GONE);
            view.setPlaying(false);
            return;
        }
        if (active) {
            if (view.getVisibility() != View.VISIBLE) view.setAlpha(0f);
            view.setVisibility(View.VISIBLE);
            view.setPlaying(true);
        }
        // Keep the underlying app glow visible: fading the weather itself mixes both backdrops.
        // Cancellation retains the current opacity, including during rapid focus changes.
        view.animate().alpha(active ? 1f : 0f).setDuration(active ? 900L : 700L)
                .setInterpolator(new AccelerateDecelerateInterpolator()).withEndAction(() -> {
                    if (!showing) { view.setVisibility(View.GONE); view.setPlaying(false); }
                }).start();
    }

    private void cancelRequest() {
        if (fetching) lastAttempt = kind < 0 ? 0 : Math.min(lastAttempt, lastSuccess);
        generation++; fetching = false; locationResolved = true;
        clearLocationRequest();
        if (request != null) { request.cancel(true); request = null; }
        HttpURLConnection active = connection;
        if (active != null) { try { active.disconnect(); } catch (RuntimeException ignored) { } }
    }

    void stop() {
        resumed = false;
        main.removeCallbacks(refresh);
        cancelRequest();
        if (registered && connectivity != null) {
            try { connectivity.unregisterNetworkCallback(callback); } catch (RuntimeException ignored) { }
            registered = false;
        }
        render();
    }

    @Override public void close() { stop(); worker.shutdownNow(); }
}
