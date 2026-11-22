package kr.mangwon.holdem;

import android.animation.ValueAnimator;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathMeasure;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.SystemClock;
import android.view.MotionEvent;
import org.json.JSONObject;

/** Tournament milestones share one input guard and a saved rules clock. */
final class TournamentBanner {
    private static final int FINAL_TABLE_MS = 2720, HEADS_UP_MS = 2960;
    private static final String[] BUBBLE_DISTANCE={"","1명 탈락 시 입상","2명 탈락 시 입상","3명 탈락 시 입상"};
    private final Arena arena;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final RectF titleBounds = new RectF();
    private final Path bubblePlate = new Path();
    private final Matrix glintPosition = new Matrix();
    private final LinearGradient glint = new LinearGradient(-28,5,28,-5,
            new int[]{0x00ebd2a1,0x10f1d79f,0x62ffe7ac,0x13f5db9b,0x00ebd2a1},
            new float[]{0,.37f,.52f,.73f,1},Shader.TileMode.CLAMP);
    private final RadialGradient bloom = new RadialGradient(0, 0, 1,
            new int[]{0x9c9e7536, 0x394d572c, 0x0016241d},
            new float[]{0, .54f, 1}, Shader.TileMode.CLAMP);
    private final RadialGradient duelBloom = new RadialGradient(0, 0, 1,
            new int[]{0x9b886847,0x4c534640,0x00141c1e},
            new float[]{0,.55f,1}, Shader.TileMode.CLAMP);
    private Bitmap finalTitle, duelTitle, duelInk;
    private final HeadsUpLettering duelLettering=new HeadsUpLettering();
    private final FinalTableLettering finalLettering;
    private boolean loadingFinal, loadingDuel, headsUp, swallowing;
    private int remaining = -1;
    private long sampledAt;

    private final PathMeasure[] branches = {
        bolt(240,-97, 212,-124, 205,-118, 168,-145, 176,-123, 125,-152),
        bolt(172,-139, 150,-139, 141,-132, 114,-135),
        bolt(748,95, 790,117, 784,107, 831,126, 823,111, 872,137),
        bolt(831,126, 842,142, 850,139, 866,153)
    };
    private final Path trace = new Path();

    TournamentBanner(Arena arena) {
        this.arena=arena;finalLettering=new FinalTableLettering(arena);
        bubblePlate.addRoundRect(306,57,557,83,13,13,Path.Direction.CW);
    }

    private static PathMeasure bolt(int... points) {
        Path path = new Path();
        path.moveTo(points[0], points[1]);
        for(int i=2;i<points.length;i+=2)path.lineTo(points[i],points[i+1]);
        return new PathMeasure(path,false);
    }

    void observe(JSONObject game) {
        boolean duel = game!=null && game.has("headsUpMs");
        int next = game==null ? -1 : game.optInt(duel?"headsUpMs":"finalTableMs",-1);
        if (remaining != next || headsUp != duel) {
            sampledAt = SystemClock.uptimeMillis();
            remaining = next;
            headsUp = duel;
        }
        if (game == null || game.optBoolean("cash")) return;
        int alive=game.optInt("left");
        if (!loadingFinal && alive<=game.optInt("capacity",9)+4)load(false);
        if (!loadingDuel && alive<=5)load(true);
    }

    private void load(boolean duel) {
        if(duel)loadingDuel=true;else loadingFinal=true;
        Thread loader = new Thread(() -> {
            Bitmap loaded = arena.image(arena.en?(duel?"headsup-en.webp":"final-table-en.webp"):(duel?"headsup-title.webp":"final-table-title.webp"));
            Bitmap timeline=duel&&!arena.en?arena.image("headsup-ink-time.png"):null;
            if(!arena.en&&!duel&&loaded!=null)finalLettering.prepare(loaded);
            arena.post(() -> {
                if(duel){duelTitle=loaded;duelInk=timeline;}else finalTitle=loaded;
                arena.invalidate();
            });
        }, "TournamentArt");
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

    /** A quiet, persistent tournament rail. It neither captures touches nor alters the hand clock. */
    void bubble(JSONObject tournament,long now){
        if(tournament==null||!tournament.optBoolean("bubble"))return;
        int distance=tournament.optInt("itmDistance");
        if(distance<1||distance>=BUBBLE_DISTANCE.length)return;
        boolean animated=ValueAnimator.areAnimatorsEnabled();
        float phase=(now%3200L)/3200f;
        float pulse=animated?(float)(.5-.5*Math.cos(phase*2*Math.PI)):.5f;
        int amber=android.graphics.Color.rgb(237+(int)(18*pulse),180+(int)(46*pulse),102+(int)(54*pulse));
        Canvas c=arena.c;
        c.save();
        c.scale(1+.018f*pulse,1+.026f*pulse,431.5f,70);
        arena.stage.plate(297,55,566,85,0);
        if(animated&&phase<.68f){
            glintPosition.setTranslate(273+317*ease(phase/.68f),70);
            glint.setLocalMatrix(glintPosition);
            paint.setStyle(Paint.Style.FILL);paint.setColor(0xffffffff);paint.setShader(glint);
            c.drawPath(bubblePlate,paint);paint.setShader(null);
        }
        arena.text("버블 구간",374,76,18,amber,arena.title,Paint.Align.CENTER);
        arena.line(426,62,426,78,0xff87754e,1);
        arena.text(BUBBLE_DISTANCE[distance],482,75,14,arena.paper,arena.bold,Paint.Align.CENTER);
        c.restore();
    }

    void draw(Canvas canvas, long now) {
        int w = canvas.getWidth(), h = canvas.getHeight();
        int duration = headsUp ? HEADS_UP_MS : FINAL_TABLE_MS;
        float t = duration - remaining + Math.min(180, Math.max(0, now - sampledAt));
        float opacity = Math.min(ease(t / 240), ease((duration - t) / 340));
        if (opacity <= 0) return;
        boolean animate = ValueAnimator.areAnimatorsEnabled();
        float enter = animate ? ease((t-(headsUp?140:0)) / 570) : 1;
        float exit = animate ? 1 - ease((duration - t) / 340) : 0;
        float cx = w * .5f, cy = h * (.477f + .031f * (1 - enter) - .013f * exit);
        canvas.drawARGB((int) (217 * opacity), 8, 19, 16);
        canvas.save();
        canvas.translate(cx, cy);
        canvas.scale(w * .66f, h * .65f);
        paint.setShader(headsUp?duelBloom:bloom);
        paint.setAlpha((int) (228 * opacity));
        canvas.drawCircle(0, 0, 1, paint);
        paint.setShader(null);
        canvas.restore();

        Bitmap title=headsUp?duelTitle:finalTitle;
        if (title != null) {
            float ratio = title.getWidth() / (float) title.getHeight();
            float width = Math.min(w * .86f, h * .72f * ratio) * (.94f + .06f * enter);
            float halfH = width / (2 * ratio);
            titleBounds.set(cx - width / 2, cy - halfH, cx + width / 2, cy + halfH);
            if(arena.en){
                paint.setShader(null);paint.setColor(android.graphics.Color.WHITE);
                paint.setAlpha(Math.round(255*opacity));
                canvas.save();canvas.scale(1, .90f+.10f*enter,cx,cy);
                canvas.drawBitmap(title,null,titleBounds,paint);canvas.restore();
                if(headsUp&&animate)drawLightning(canvas,t,width,cx,cy);
            }else if(headsUp){
                duelLettering.draw(canvas,title,duelInk,titleBounds,t,opacity,animate);
                if(animate)drawLightning(canvas,t,width,cx,cy);
            }else finalLettering.draw(canvas,title,titleBounds,t,opacity,animate);
        }
    }

    private void drawLightning(Canvas canvas,float t,float width,float cx,float cy) {
        float age=t-480;
        if(age<=0||age>=710)return;
        canvas.save();
        canvas.translate(cx-width/2,cy);
        canvas.scale(width/1000,width/1000);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        float fade=1-ease((age-235)/475);
        for(int i=0;i<branches.length;i++){
            float growth=ease((age-((i%2)*100+(i/2)*36))/200);
            if(growth<=0)continue;
            trace.rewind();
            branches[i].getSegment(0,branches[i].getLength()*growth,trace,true);
            paint.setColor(0xffc5a978);paint.setAlpha((int)(77*fade));paint.setStrokeWidth(6.5f);
            canvas.drawPath(trace,paint);
            paint.setColor(0xffffe9ac);paint.setAlpha((int)(232*fade));paint.setStrokeWidth(1.8f);
            canvas.drawPath(trace,paint);
        }
        paint.setStyle(Paint.Style.FILL);
        canvas.restore();
    }
}
