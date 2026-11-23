package kr.mangwon.holdem;

import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ComposeShader;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.Shader;

/** Lights the illustrated tubes on the facade in the original street coordinates. */
final class PubNeon {
    private static final int LEFT = 763, TOP = 222, WIDTH = 422, HEIGHT = 163;
    private static final int FRONT = 62;
    private final Paint dim = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Paint light = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final LinearGradient gradient = new LinearGradient(0, 0, FRONT, 0,
            Color.TRANSPARENT, Color.WHITE, Shader.TileMode.CLAMP);
    private final Matrix sweep = new Matrix();

    PubNeon(Bitmap scene) {
        // Capture the highlights of the painted glass, leaving the metal rim, brick and
        // spade lamp untouched. This runs once on the opening's artwork loader thread.
        int[] pixels = new int[WIDTH * HEIGHT];
        int[] cores = new int[pixels.length];
        scene.getPixels(pixels, 0, WIDTH, LEFT, TOP, WIDTH, HEIGHT);
        for (int x = 0; x < WIDTH; x++) {
            float along = LEFT + x - 780;
            float upper = 304 - .176f * along, lower = 378 - .140f * along;
            for (int y = 0; y < HEIGHT; y++) {
                int i = y * WIDTH + x;
                int pixel = pixels[i];
                float edge = Math.min(Math.min(x, WIDTH - 1 - x),
                        Math.min(TOP + y - upper, lower - TOP - y));
                float inside = clamp(edge / 7);
                float red = Color.red(pixel), green = Color.green(pixel);
                int shadow = Math.round(245 * inside * clamp((red - 42) / 100));
                pixels[i] = Color.argb(shadow, 32, 25, 24);
                int tube = Math.round(235 * inside * clamp((red - 165) / 78)
                        * clamp((green - 70) / 102));
                cores[i] = Color.argb(tube, 255, 227, 161);
            }
        }
        BitmapShader unlit = shader(pixels);
        dim.setShader(new ComposeShader(unlit, gradient, PorterDuff.Mode.DST_IN));
        light.setShader(shader(cores));
    }

    private static BitmapShader shader(int[] pixels) {
        Bitmap bitmap = Bitmap.createBitmap(pixels, WIDTH, HEIGHT, Bitmap.Config.ARGB_8888);
        bitmap.prepareToDraw();
        return new BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
    }

    private static float clamp(float n) { return Math.max(0, Math.min(1, n)); }

    void draw(Canvas canvas, long streetMs) {
        int saved = canvas.save();
        canvas.translate(LEFT, TOP);
        float t = clamp((streetMs - 590) / 1250f);
        if (t < 1) {
            float eased = t * t * (3 - 2 * t);
            sweep.setTranslate(-FRONT + (WIDTH + FRONT) * eased, 0);
            gradient.setLocalMatrix(sweep);
            canvas.drawRect(0, 0, WIDTH, HEIGHT, dim);
        } else {
            // A low, continuous change in the hot cores keeps the resting sign alive.
            double phase = (streetMs - 1840) * .00165;
            light.setAlpha(Math.round(14 * (1 - (float) Math.cos(phase))));
            canvas.drawRect(0, 0, WIDTH, HEIGHT, light);
        }
        canvas.restoreToCount(saved);
    }
}
