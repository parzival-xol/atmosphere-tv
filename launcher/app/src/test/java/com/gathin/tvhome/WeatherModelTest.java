package com.gathin.tvhome;

import org.junit.Test;
import static org.junit.Assert.*;

public class WeatherModelTest {
    @Test public void weatherCodesCoverWetAndFrozenConditions() {
        assertEquals(WeatherModel.CLEAR, WeatherModel.kind(0));
        assertEquals(WeatherModel.PARTLY, WeatherModel.kind(2));
        assertEquals(WeatherModel.CLOUDY, WeatherModel.kind(3));
        assertEquals(WeatherModel.FOG, WeatherModel.kind(48));
        assertEquals(WeatherModel.RAIN, WeatherModel.kind(67));
        assertEquals(WeatherModel.RAIN, WeatherModel.kind(82));
        assertEquals(WeatherModel.SNOW, WeatherModel.kind(86));
        assertEquals(WeatherModel.STORM, WeatherModel.kind(99));
    }

    @Test public void invalidWeatherDoesNotInventConditions() {
        assertEquals(-1, WeatherModel.kind(-1));
        assertEquals(-1, WeatherModel.kind(100));
        assertEquals(-1, WeatherModel.kind(4));
    }

    @Test public void appFocusAlwaysWinsOverWeather() {
        assertFalse(WeatherModel.visible(true, true, true, true));
        assertTrue(WeatherModel.visible(true, false, true, true));
    }

    @Test public void offlineOrStoppedHomeUsesOriginalBackdrop() {
        assertFalse(WeatherModel.visible(true, false, false, true));
        assertFalse(WeatherModel.visible(false, false, true, true));
        assertFalse(WeatherModel.visible(true, false, true, false));
    }

    @Test public void staleOrFutureCacheCannotDriveAnimation() {
        assertTrue(WeatherModel.fresh(1000, 900, 200));
        assertFalse(WeatherModel.fresh(1100, 900, 200));
        assertFalse(WeatherModel.fresh(800, 900, 200));
        assertFalse(WeatherModel.fresh(1000, 0, 200));
    }

    @Test public void clearSkyUsesDayOrNightDescription() {
        assertEquals("Sunny", WeatherModel.description(WeatherModel.CLEAR, true));
        assertEquals("Clear night", WeatherModel.description(WeatherModel.CLEAR, false));
    }
}
