package kr.mangwon.holdem;

import android.animation.ValueAnimator;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.MotionEvent;

/** A cold entrance into the same illustrated room used by the lobby. */
final class LobbyIntro {
    static final int DURATION_MS = 2540;
    private final Arena a;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final CardBack cards = new CardBack();
    private final Rect glassSource = new Rect(106, 96, 325, 335);
    private final RectF target = new RectF();
    private final RadialGradient amber = new RadialGradient(0, 0, 1, 0xffe9a54f, 0x00da8b36, Shader.TileMode.CLAMP);
    private Bitmap spade;
    private boolean pending, playing, swallowUp, sounded;
    private long began;

    LobbyIntro(Arena arena) { a = arena; }

    void prepare(boolean eligible) { pending = eligible; }

    void ready(boolean hasSession) {
        if (!pending) return;
        pending = false;
        if (hasSession || !a.host.active || !ValueAnimator.areAnimatorsEnabled()) return;
        spade = BitmapFactory.decodeResource(a.getResources(), R.drawable.launcher_spade);
        playing = true;
        a.invalidate();
    }

    void finish() {
        pending = playing = false;
        spade = null;
        a.hits.clear();
        a.invalidate();
    }

    boolean blocking() { return pending || playing; }

    boolean touch(MotionEvent event) {
        int action = event.getActionMasked();
        if (!blocking() && !swallowUp) return false;
        if (action == MotionEvent.ACTION_DOWN) {
            swallowUp = true;
            finish();
        }
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) swallowUp = false;
        return true;
    }

    private static float progress(long time, int start, int end) {
        float x = Math.max(0, Math.min(1, (time - start) / (float)(end - start)));
        return x * x * (3 - 2 * x);
    }

    private void glow(Canvas canvas, float x, float y, float width, float height, int alpha) {
        if (alpha <= 0) return;
        canvas.save();
        canvas.translate(x,y);
        canvas.scale(width,height);
        paint.setShader(amber);
        paint.setColor(Color.WHITE);
        paint.setAlpha(alpha);
        canvas.drawCircle(0,0,1,paint);
        paint.setShader(null);
        canvas.restore();
    }

    private void card(Canvas canvas, float x, float y, float angle, int alpha) {
        canvas.save();
        canvas.translate(x,y);
        canvas.rotate(angle);
        canvas.scale(2.18f,2.18f);
        cards.drawPocket(canvas,-Arena.POCKET_WIDTH/2,-Arena.POCKET_HEIGHT/2,4,alpha);
        canvas.restore();
    }

    boolean draw(Canvas canvas, long now) {
        if (!blocking()) return false;
        if (pending) {
            canvas.drawColor(0xff121916);
            return true;
        }
        if (began == 0) began = now;
        long ms = now - began;
        if (ms >= DURATION_MS) {
            finish();
            return false;
        }
        int w = a.getWidth(), h = a.getHeight();
        a.scale = Math.min(w / 1080f, h / 520f);
        a.offX = (w - 1080 * a.scale) * .5f;
        a.offY = (h - 520 * a.scale) * .5f;
        a.hits.clear();
        canvas.drawColor(a.ink);

        float reveal = progress(ms,0,1560), handoff = progress(ms,1630,DURATION_MS);
        float zoom = 1 + .035f * (1 - progress(ms,0,DURATION_MS));
        canvas.save();
        canvas.scale(zoom,zoom,w*.67f,h*.38f);
        a.pubMenu.backdrop(w,h,false);
        canvas.restore();
        canvas.drawColor(Color.argb(Math.round((198-98*reveal)*(1-handoff)),10,16,14));

        canvas.save();
        canvas.translate(a.offX,a.offY);
        canvas.scale(a.scale,a.scale);
        float light = progress(ms,150,780) * (1-progress(ms,1090,2380));
        glow(canvas,763,24,230,130,Math.round(50*light));
        float crestExit = progress(ms,1410,1920);
        float crest = progress(ms,160,680) * (1-crestExit);
        if (crest > 0) {
            float fade = 1 - crestExit;
            float first = progress(ms,350,850), second = progress(ms,445,985);
            glow(canvas,241,180,129,130,Math.round(47*crest));
            card(canvas,136+53*first,242-40*first-14*crestExit,-33+12*first,Math.round(235*first*fade));
            card(canvas,344-55*second,242-40*second-14*crestExit,34-13*second,Math.round(235*second*fade));
            if (spade != null) {
                float cy = 169 + (1-crest)*6 - 19*crestExit;
                target.set(187,cy-59,295,cy+59);
                paint.setColor(Color.WHITE);
                paint.setAlpha(Math.round(255*crest));
                canvas.drawBitmap(spade,glassSource,target,paint);
            }
        }
        // The last frame of the moving title is the actual lobby title at its normal coordinates.
        float letters = progress(ms,570,1110);
        if (letters > 0) {
            float scale = 1.36f-.36f*handoff;
            canvas.save();
            canvas.translate(47+20*(1-handoff),100+213*(1-handoff)+9*(1-letters));
            canvas.scale(scale,scale);
            canvas.translate(-47,-100);
            a.pubMenu.drawTitle(Math.round(255*letters));
            canvas.restore();
        }
        float menu = progress(ms,1980,DURATION_MS);
        if (menu > 0) {
            int saved = canvas.saveLayerAlpha(20,0,1070,510,Math.round(menu*255));
            a.pubMenu.controls();
            canvas.restoreToCount(saved);
            a.hits.clear();
        }
        canvas.restore();
        if (!sounded && ms >= 530) {
            sounded = true;
            if (a.opt.optBoolean("sound")) a.host.fx("deal",.19f);
        }
        if (a.host.active) a.postInvalidateOnAnimation();
        return true;
    }
}
