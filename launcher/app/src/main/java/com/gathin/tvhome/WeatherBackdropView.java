package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.LightingColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.view.View;

/** Anime-inspired native sky: painted clouds, sunlight and layered precipitation. */
final class WeatherBackdropView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint small = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF bounds = new RectF();
    private final Bitmap cloud = makeCloud();
    private Shader sky, glow, disc, shade, mist, readability;
    private LightingColorFilter cloudTint;
    private int kind = WeatherModel.CLEAR;
    private boolean day = true, playing;
    private String temperature = "", description = "";
    private final Runnable frame = () -> { if (playing && isShown()) invalidate(); };

    WeatherBackdropView(Context context) {
        super(context);
        setFocusable(false);
        setClickable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        text.setColor(Color.WHITE);
        small.setColor(0xBAFFFFFF);
        Typeface face = getResources().getFont(R.font.outfit_light);
        text.setTypeface(face);
        small.setTypeface(face);
        setVisibility(GONE);
    }

    void setWeather(int weatherKind, boolean isDay, double degrees) {
        boolean changed = kind != weatherKind || day != isDay;
        kind = weatherKind;
        day = isDay;
        temperature = Math.round(degrees) + "°C";
        description = WeatherModel.description(kind, day);
        if (changed || sky == null) prepareShaders(getWidth(), getHeight());
        invalidate();
    }

    void setPlaying(boolean value) {
        if (playing == value) return;
        playing = value;
        removeCallbacks(frame);
        if (playing) invalidate();
    }

    @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        prepareShaders(w, h);
        text.setTextSize(w * 0.043f);
        small.setTextSize(w * 0.0145f);
    }

    private void prepareShaders(int w, int h) {
        if (w == 0 || h == 0) return;
        boolean heavy = kind >= WeatherModel.FOG;
        int top = day ? (heavy ? 0xFF344B70 : 0xFF337BBD) : 0xFF101A33;
        int horizon = day ? (heavy ? 0xFF8598B5 : 0xFFB7D7E4) : 0xFF414F75;
        sky = new LinearGradient(0, 0, w * 0.12f, h,
                new int[]{top, horizon, day ? 0xFF526C8A : 0xFF161E32}, new float[]{0f, 0.61f, 1f}, Shader.TileMode.CLAMP);
        glow = new RadialGradient(w * (day ? .74f : .62f), h * (day ? .29f : .46f), w * (day ? .38f : .65f),
                new int[]{day ? 0x90FFE5B9 : 0x306E88B2, day ? 0x28F5D4B0 : 0x185E6E9A, Color.TRANSPARENT},
                new float[]{0f, 0.3f, 1f}, Shader.TileMode.CLAMP);
        disc = new RadialGradient(w * 0.735f, h * 0.277f, w * 0.035f,
                new int[]{0xFFFFF9DC, 0xFFFFE6AD, 0x00FFD394},
                new float[]{0f, 0.88f, 1f}, Shader.TileMode.CLAMP);
        shade = new LinearGradient(0, h * 0.20f, 0, h,
                new int[]{Color.TRANSPARENT, 0x25080D15, 0xE8030509}, new float[]{0f, 0.43f, 1f}, Shader.TileMode.CLAMP);
        mist = new RadialGradient(w * 0.65f, h * 0.57f, w * 0.65f,
                day ? 0x227F9FAF : kind == WeatherModel.FOG ? 0x298395B1 : 0x0B8395B1, Color.TRANSPARENT, Shader.TileMode.CLAMP);
        readability = new LinearGradient(0, 0, w * .42f, 0,
                day ? 0x260C2540 : 0x340A1426, Color.TRANSPARENT, Shader.TileMode.CLAMP);
        cloudTint = new LightingColorFilter(day ? 0xFFFFF5EC : 0xFFBCC7ED, 0);
    }

    @Override protected void onDraw(Canvas c) {
        super.onDraw(c);
        if (sky == null) return;
        float w = getWidth(), h = getHeight();
        double seconds = SystemClock.uptimeMillis() / 1000.0;
        paint.setAlpha(255); paint.setShader(sky); c.drawRect(0, 0, w, h, paint);
        paint.setAlpha(day ? (int)(238 + 17 * (.5 + .5 * Math.sin(seconds * .22))) : 255);
        paint.setShader(glow); c.drawRect(0, 0, w, h, paint); paint.setShader(null);
        paint.setAlpha(255);
        if (!day && kind <= WeatherModel.PARTLY) {
            for (int i = 0; i < 24; i++) {
                float x = ((i * 0.173f + 0.06f) % 0.94f) * w;
                float y = (0.07f + (i * 0.119f % 0.42f)) * h;
                paint.setColor(0xFFDCEBFF);
                paint.setAlpha((int)(50 + 42 * (0.5 + 0.5 * Math.sin(seconds * 0.24 + i))));
                c.drawCircle(x, y, w * (i % 3 == 0 ? 0.0008f : 0.0005f), paint);
                if (i % 8 == 0) {
                    paint.setStrokeWidth(w * .00035f);
                    c.drawLine(x - w * .0018f, y, x + w * .0018f, y, paint);
                    c.drawLine(x, y - w * .0018f, x, y + w * .0018f, paint);
                }
            }
        }
        if (day && kind <= WeatherModel.CLOUDY) {
            paint.setAlpha(kind == WeatherModel.CLOUDY ? 100 : 255);
            paint.setShader(disc);
            c.drawCircle(w * 0.735f, h * 0.277f, w * 0.036f, paint);
            paint.setShader(null);
        }
        if (kind != WeatherModel.CLEAR) drawClouds(c, w, h, seconds);
        paint.setAlpha(255); paint.setShader(mist); c.drawRect(0, 0, w, h, paint); paint.setShader(null);
        if (kind == WeatherModel.RAIN || kind == WeatherModel.STORM || kind == WeatherModel.SNOW) {
            drawPrecipitation(c, w, h, seconds);
        }
        paint.setAlpha(255); paint.setShader(shade); c.drawRect(0, 0, w, h, paint); paint.setShader(null);
        paint.setShader(readability); c.drawRect(0, 0, w, h, paint); paint.setShader(null);
        c.drawText(temperature, w * 0.064f, h * 0.48f, text);
        c.drawText(description, w * 0.066f, h * 0.53f, small);
        small.setAlpha(100);
        c.drawText("Weather by Open-Meteo", w * 0.066f, h * 0.578f, small);
        small.setAlpha(186);
        // Display-synchronised scheduling; a 30 fps ceiling keeps the older TV GPU cool.
        if (playing) postOnAnimationDelayed(frame, 25L);
    }

    /** Two depths of hand-drawn streaks/flakes, all time-based so motion stays consistent. */
    private void drawPrecipitation(Canvas c, float w, float h, double seconds) {
        boolean snow = kind == WeatherModel.SNOW;
        paint.setStrokeCap(Paint.Cap.ROUND);
        int count = snow ? 42 : 78;
        for (int i = 0; i < count; i++) {
            boolean near = i % 3 == 0;
            double speed = snow ? (near ? .037 : .019) : (near ? .32 : .21);
            float progress = (float)((i * .137 + seconds * speed) % 1.12);
            float y = (progress - .08f) * h;
            float x = ((i * .217f) % 1.12f - .06f) * w;
            x += snow ? (float)Math.sin(seconds * .35 + i) * w * .018f : -progress * w * .035f;
            paint.setColor(snow ? 0xFFE9EDFF : day ? 0xFFBCE0F5 : 0xFFB3C5EB);
            paint.setAlpha(snow ? (near ? 155 : 85) : (near ? 100 : 45));
            paint.setStrokeWidth(w * (near ? .00085f : .0005f));
            if (snow) {
                float r = w * (near ? .0021f : .0010f);
                if (near) {
                    c.save(); c.rotate((float)(seconds * 8 + i * 41), x, y);
                    for (int arm = 0; arm < 3; arm++) {
                        c.drawLine(x - r, y, x + r, y, paint);
                        c.rotate(60, x, y);
                    }
                    c.restore();
                } else c.drawCircle(x, y, r, paint);
            } else {
                float length = h * (near ? .032f : .018f);
                c.drawLine(x, y, x - length * .22f, y + length, paint);
            }
        }
        paint.setStrokeCap(Paint.Cap.BUTT);
        paint.setAlpha(255);
    }

    private void drawClouds(Canvas c, float w, float h, double seconds) {
        int count = kind == WeatherModel.PARTLY ? 3 : kind == WeatherModel.FOG ? 7 : 5;
        paint.setColorFilter(cloudTint);
        for (int i = 0; i < count; i++) {
            float drift = (float)Math.sin(seconds * (0.023 + (i % 3) * 0.007) + i * 1.7) * w * 0.13f;
            float width = w * (0.44f + (i % 3) * 0.11f);
            float x = w * ((i * 0.31f + 0.19f) % 1.18f - 0.2f) + drift;
            float y = h * (0.12f + (i % 3) * 0.13f) + (float)Math.sin(seconds * 0.022 + i) * h * 0.012f;
            float height = width * (kind == WeatherModel.FOG ? 0.16f : 0.46f);
            paint.setAlpha((kind == WeatherModel.FOG ? 90 : kind == WeatherModel.PARTLY ? 150 : kind == WeatherModel.STORM ? 145 : 180) + (i % 2) * 24);
            bounds.set(x, y, x + width, y + height);
            if (i % 2 == 1) {
                c.save(); c.scale(-1, 1, bounds.centerX(), bounds.centerY());
                c.drawBitmap(cloud, null, bounds, paint); c.restore();
            } else c.drawBitmap(cloud, null, bounds, paint);
        }
        paint.setColorFilter(null); paint.setAlpha(255);
    }

    /** A cached painted silhouette with broad cel shadows, never a per-frame blur or filter. */
    private static Bitmap makeCloud() {
        Bitmap bitmap = Bitmap.createBitmap(512, 256, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bitmap);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        Path outline = new Path();
        outline.moveTo(43, 177);
        outline.cubicTo(8, 175, 8, 131, 49, 127);
        outline.cubicTo(43, 91, 75, 66, 110, 82);
        outline.cubicTo(123, 20, 205, 20, 234, 67);
        outline.cubicTo(279, 32, 343, 55, 350, 98);
        outline.cubicTo(385, 77, 424, 99, 421, 129);
        outline.cubicTo(477, 121, 499, 174, 466, 190);
        outline.cubicTo(386, 218, 111, 215, 43, 177);
        outline.close();
        p.setShader(new LinearGradient(0, 45, 0, 215,
                new int[]{0xFFF9F6F1, 0xFFE8E9EE, 0xFFABB9D3}, new float[]{0, .60f, 1}, Shader.TileMode.CLAMP));
        c.drawPath(outline, p);
        c.save(); c.clipPath(outline);
        Path shadow = new Path();
        shadow.moveTo(12, 152);
        shadow.cubicTo(74, 177, 105, 121, 151, 139);
        shadow.cubicTo(197, 157, 220, 129, 265, 150);
        shadow.cubicTo(325, 177, 352, 136, 395, 153);
        shadow.cubicTo(443, 176, 473, 152, 504, 162);
        shadow.lineTo(512, 256); shadow.lineTo(0, 256); shadow.close();
        p.setShader(new LinearGradient(0, 132, 0, 220, 0xFFC6CEDF, 0xFF98ABC9, Shader.TileMode.CLAMP));
        c.drawPath(shadow, p);
        c.restore();
        return bitmap;
    }

    @Override protected void onDetachedFromWindow() { setPlaying(false); super.onDetachedFromWindow(); }
}
