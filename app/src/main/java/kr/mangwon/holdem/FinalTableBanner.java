package kr.mangwon.holdem;

import android.animation.ValueAnimator;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.SystemClock;
import android.view.MotionEvent;
import org.json.JSONObject;

/** Tournament consolidation cue. The saved rules clock owns its lifetime. */
final class FinalTableBanner {
    private static final int DURATION_MS = 2720;
    private final Arena arena;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final RectF titleBounds = new RectF();
    private final RadialGradient bloom = new RadialGradient(0, 0, 1,
            new int[]{0x9c9e7536, 0x394d572c, 0x0016241d},
            new float[]{0, .54f, 1}, Shader.TileMode.CLAMP);
    private Bitmap title;
    private boolean loading, swallowing;
    private int remaining = -1;
    private long sampledAt;

    FinalTableBanner(Arena arena) { this.arena = arena; }

    void observe(JSONObject game) {
        int next = game == null ? -1 : game.optInt("finalTableMs", -1);
        if (remaining != next) {
            sampledAt = SystemClock.uptimeMillis();
            remaining = next;
        }
        if (game == null || game.optBoolean("cash") || title != null || loading) return;
        if (next < 0 && game.optInt("left") > game.optInt("capacity", 9) + 4) return;
        loading = true;
        Thread loader = new Thread(() -> {
            Bitmap loaded = arena.image("final-table-title.webp");
            arena.post(() -> {
                title = loaded;
                arena.invalidate();
            });
        }, "FinalTableArt");
        loader.setDaemon(true);
        loader.start();
    }

    boolean showing() { return remaining >= 0 && "table".equals(arena.screen) && arena.panel.isEmpty(); }

    boolean touch(MotionEvent event) {
        if (!swallowing && !showing()) return false;
        int action = event.getActionMasked();
        swallowing = action != MotionEvent.ACTION_UP && action != MotionEvent.ACTION_CANCEL;
        arena.held = "";
        return true;
    }

    private static float ease(float t) {
        t = Math.min(1, Math.max(0, t));
        return t * t * (3 - 2 * t);
    }

    void draw(Canvas canvas, long now) {
        int w = canvas.getWidth(), h = canvas.getHeight();
        float t = DURATION_MS - remaining + Math.min(180, Math.max(0, now - sampledAt));
        float opacity = Math.min(ease(t / 240), ease((DURATION_MS - t) / 340));
        if (opacity <= 0) return;
        boolean animate = ValueAnimator.areAnimatorsEnabled();
        float enter = animate ? ease(t / 570) : 1;
        float exit = animate ? 1 - ease((DURATION_MS - t) / 340) : 0;
        float cx = w * .5f, cy = h * (.477f + .031f * (1 - enter) - .013f * exit);
        canvas.drawARGB((int) (217 * opacity), 8, 19, 16);
        canvas.save();
        canvas.translate(cx, cy);
        canvas.scale(w * .66f, h * .65f);
        paint.setShader(bloom);
        paint.setAlpha((int) (228 * opacity));
        canvas.drawCircle(0, 0, 1, paint);
        paint.setShader(null);
        canvas.restore();

        if (title != null) {
            float ratio = title.getWidth() / (float) title.getHeight();
            float width = Math.min(w * .86f, h * .72f * ratio) * (.94f + .06f * enter);
            float halfH = width / (2 * ratio);
            titleBounds.set(cx - width / 2, cy - halfH, cx + width / 2, cy + halfH);
            paint.setColor(0xffffffff);
            paint.setAlpha((int) (255 * opacity));
            canvas.drawBitmap(title, null, titleBounds, paint);
        }
    }
}
