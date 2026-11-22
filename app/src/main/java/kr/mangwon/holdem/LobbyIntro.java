package kr.mangwon.holdem;

import android.animation.ValueAnimator;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.MotionEvent;

/** A painted tracking shot from the stained-glass spade to the neighborhood pub. */
final class LobbyIntro {
    private static final int PROMPT_MS = 2870, EXIT_MS = 420;
    private static final int NIGHT = 0xff0f191c;
    private static final float STREET_WIDTH = 1817f, STREET_HEIGHT = 866f;
    private static final float TITLE_WIDTH = 850f, TITLE_HEIGHT = 765f;
    private static final Rect[] LETTERS = {
        new Rect(3,3,298,305), new Rect(304,3,599,308), new Rect(605,3,892,308),
        new Rect(898,3,1344,451), new Rect(1350,3,1756,447)
    };
    private static final int[] LETTER_X = {57,297,548,8,435};
    private static final int[] LETTER_Y = {19,10,10,307,298};
    private static final int[] ENTRANCES = {1100,1220,1340,1570,1720};
    private static final float[] ANGLES = {-2.1f,-1.2f,.8f,-2.8f,1.7f};
    // Puddles are attached to the painted street in camera space.
    private static final float[] PUDDLE_X = {460,724,527,1367,917,598};
    private static final float[] PUDDLE_Y = {815,807,732,849,840,773};

    private final Arena a;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint letters = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
    private final RectF target = new RectF();
    private final Path glass = new Path();
    private final float[] rainLines = new float[21 * 4];
    private final PorterDuffXfermode foilMask = new PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP);
    private final RadialGradient amber = new RadialGradient(0,0,1,0xffffd38a,0x00f8aa51,Shader.TileMode.CLAMP);
    private final LinearGradient leftShade = new LinearGradient(0,0,1,0,
        new int[]{0xc50e171c,0x99101b20,0x00101b20},new float[]{0,.47f,1},Shader.TileMode.CLAMP);
    private final LinearGradient foil = new LinearGradient(-110,0,110,32,
        new int[]{0x00fff7d4,0x0ef8d79c,0x9bfff0c1,0x0ef8d79c,0x00fff7d4},
        new float[]{0,.32f,.5f,.68f,1},Shader.TileMode.CLAMP);
    private Bitmap street, pedestrian, title, entryPlate;
    private Bitmap lowerEnglishWord;
    private boolean pending, playing, swallowUp, sounded, reducedMotion, pressed, canStartOnRelease;
    private int requestNumber;
    private long lastFrame, playhead, exitAt=-1;
    private float touchX,touchY;

    LobbyIntro(Arena arena) {
        a = arena;
        // Inner glass of the illustrated sign. The iron frame remains dark as light passes.
        glass.moveTo(1268,110);
        glass.cubicTo(1255,138,1190,181,1183,219);
        glass.cubicTo(1175,258,1219,279,1253,235);
        glass.cubicTo(1255,265,1242,293,1231,305);
        glass.lineTo(1305,305);
        glass.cubicTo(1292,289,1281,266,1281,239);
        glass.cubicTo(1314,278,1357,261,1356,230);
        glass.cubicTo(1352,189,1292,147,1268,110);
        glass.close();
    }

    void prepare(boolean eligible) {
        pending = eligible;
    }

    void ready() { if(pending)load(); }

    void replay() {
        if (!blocking() && a.host.active) load();
    }

    private void load() {
        pending = true;
        reducedMotion = !ValueAnimator.areAnimatorsEnabled();
        int request = ++requestNumber;
        Thread loader = new Thread(() -> {
            Bitmap scene = a.image("opening-street.webp");
            Bitmap figure = a.image("opening-passerby.webp");
            Bitmap wordmark = a.image(a.en?"opening-top-en.webp":"opening-title.webp");
            Bitmap lower = a.en?a.image("opening-base-en.webp"):null;
            Bitmap plate = a.image("opening-entry.webp");
            if (scene != null) scene.prepareToDraw();
            if (figure != null) figure.prepareToDraw();
            if (wordmark != null) wordmark.prepareToDraw();
            a.host.ui.post(() -> {
                if (request != requestNumber || !pending || a.host.isDestroyed()) return;
                if (scene == null || wordmark == null) { finish(); return; }
                street = scene;
                pedestrian = figure;
                title = wordmark; lowerEnglishWord=lower;
                entryPlate = plate;
                pending = false;
                playing = true;
                sounded = false;
                lastFrame=0;playhead=reducedMotion?PROMPT_MS+520:0;exitAt=-1;pressed=false;
                a.invalidate();
            });
        }, "PubOpeningArt");
        loader.start();
        a.invalidate();
    }

    void finish() {
        requestNumber++;
        pending = playing = false;
        street = pedestrian = title = entryPlate = null;
        a.hits.clear();
        a.invalidate();
    }

    boolean blocking() { return pending || playing; }

    void pause(){lastFrame=0;pressed=swallowUp=false;}

    boolean back(){
        if(!blocking())return false;
        if(playing&&playhead<PROMPT_MS){playhead=PROMPT_MS+470;a.invalidate();return true;}
        if(exitAt>=0||pending)return true;
        finish();return false;
    }

    private void advance(){
        if(!playing||exitAt>=0)return;
        if(playhead<PROMPT_MS)playhead=PROMPT_MS+470;
        else{
            if(a.opt.optBoolean("sound"))a.host.fx("tap",.38f);
            exitAt=playhead;
            if(reducedMotion){finish();return;}
        }
        a.invalidate();
    }

    boolean keyboardStart(){
        if(!blocking())return false;
        advance();return true;
    }

    boolean touch(MotionEvent event) {
        int action = event.getActionMasked();
        if (!blocking() && !swallowUp) return false;
        if (action == MotionEvent.ACTION_DOWN) {
            swallowUp = true;
            pressed=playing&&exitAt<0;
            canStartOnRelease=pressed&&playhead>=PROMPT_MS;
            touchX=event.getX();touchY=event.getY();a.invalidate();
        }
        if(action==MotionEvent.ACTION_MOVE&&pressed){
            float slop=Math.max(20,a.getHeight()*.047f);
            if(Math.abs(touchX-event.getX())>slop||Math.abs(touchY-event.getY())>slop)pressed=false;
        }
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            if(action==MotionEvent.ACTION_UP&&pressed){
                if(canStartOnRelease)advance();
                else{playhead=Math.max(playhead,PROMPT_MS+470);a.invalidate();}
            }
            swallowUp=pressed=false;a.invalidate();
        }
        return true;
    }

    private static float unit(float x) { return Math.max(0, Math.min(1,x)); }

    private static float ease(long ms, int start, int end) {
        float t = unit((ms-start)/(float)(end-start));
        return t*t*(3-2*t);
    }

    private static float mix(float from, float to, float t) { return from+(to-from)*t; }

    private void bitmap(Canvas c, Bitmap art, Rect src, float x, float y, float w, float h, int alpha) {
        target.set(x,y,x+w,y+h);
        paint.setColor(Color.WHITE);
        paint.setAlpha(alpha);
        c.drawBitmap(art,src,target,paint);
    }

    private void glow(Canvas c, float x, float y, float rx, float ry, int alpha) {
        if (alpha <= 0) return;
        int saved = c.save();
        c.translate(x,y); c.scale(rx,ry);
        paint.setColor(Color.WHITE); paint.setShader(amber); paint.setAlpha(alpha);
        c.drawCircle(0,0,1,paint);
        paint.setShader(null);
        c.restoreToCount(saved);
    }

    private void street(Canvas c, int w, int h, long ms) {
        float open = ease(ms,80,2450), drift = ease(ms,2450,4310);
        float zoom = mix(2.12f,1.026f,open)-.022f*drift;
        float scale = Math.max(w/STREET_WIDTH,h/STREET_HEIGHT)*zoom;
        float dx = w-STREET_WIDTH*scale, dy = h-STREET_HEIGHT*scale;
        float x = mix(w*.636f-1270*scale,dx*.48f,open);
        float y = mix(h*.42f-208*scale,dy*.21f,open);
        c.save();
        c.translate(Math.max(dx,Math.min(0,x)),Math.max(dy,Math.min(0,y)));
        c.scale(scale,scale);
        bitmap(c,street,null,0,0,STREET_WIDTH,STREET_HEIGHT,255);
        if (!reducedMotion) {
            float pulse = .5f+.5f*(float)Math.sin(.00185f*ms-.7f);
            int saved = c.save();
            c.clipPath(glass);
            glow(c,1241+42*pulse,190+20*pulse,88,84,Math.round(26+48*pulse));
            c.restoreToCount(saved);
            glow(c,1196,458,162,106,Math.round(7+8*pulse));
            ripples(c,ms);
        }
        c.restore();
    }

    private void ripples(Canvas c, long ms) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1.2f);
        for (int i=0;i<PUDDLE_X.length;i++) {
            float t=((ms+i*761)%(1517+i*131))/700f;
            if (t>=1) continue;
            float rx=3+16*t, ry=.8f+2.2f*t;
            target.set(PUDDLE_X[i]-rx,PUDDLE_Y[i]-ry,PUDDLE_X[i]+rx,PUDDLE_Y[i]+ry);
            paint.setColor(Color.argb(Math.round(43*(1-t)),191,193,167));
            c.drawArc(target,17,205,false,paint);
        }
        paint.setStyle(Paint.Style.FILL);
    }

    private void rain(Canvas c, int w, int h, long ms) {
        int n = 0;
        for (int i=0;i<rainLines.length/4;i++) {
            int period = 990+(i*173)%630;
            float t=((ms+i*347L)%period)/(float)period;
            float x=w*((i*47%101)/101f-.029f*t+.014f), y=h*(1.19f*t-.12f);
            float len=h*(.007f+(i%4)*.0024f);
            rainLines[n++]=x; rainLines[n++]=y;
            rainLines[n++]=x-len*.22f; rainLines[n++]=y+len;
        }
        paint.setColor(0x367c9eaa); paint.setStrokeWidth(Math.max(1,h*.00123f));
        c.drawLines(rainLines,paint);
    }

    private void foreground(Canvas c, int w, int h, long ms) {
        if (ms>1900 || pedestrian==null) return;
        float u=unit(ms/1900f);
        // The close silhouette tracks across the lens faster than the painted building.
        float fx=mix(w*.155f,-h*.60f,u);
        float phase=ms*.009f;
        float high=h*(.92f-.035f*u);
        float fy=h*.31f+h*.007f*(float)Math.sin(phase);
        float wide=high*pedestrian.getWidth()/pedestrian.getHeight();
        c.save();
        c.rotate(.8f*(float)Math.sin(phase*.51f),fx,fy+high*.82f);
        bitmap(c,pedestrian,null,fx-wide*.48f,fy,wide,high,249);
        c.restore();
    }

    private void drawLetter(Canvas c, int i, long ms) {
        float reveal=ease(ms,ENTRANCES[i],ENTRANCES[i]+480);
        if (reveal<=0) return;
        Rect src=LETTERS[i];
        float tail=1-reveal;
        float settle=(float)Math.sin(Math.PI*reveal)*(-9.2f*reveal);
        float x=LETTER_X[i]+tail*(i%2==0?-12:10);
        float y=LETTER_Y[i]+tail*(i<3?39:50)+settle;
        if (!reducedMotion) {
            y+=1.1f*ease(ms,2300,2860)*(float)Math.sin(ms*.0019f+i*.48f);
        }
        c.save();
        c.rotate(ANGLES[i]*tail,x+src.width()*.45f,y+src.height()*.70f);
        bitmap(c,title,src,x,y,src.width(),src.height()*(1-.053f*tail),Math.round(255*reveal));
        c.restore();
    }

    private void title(Canvas c, int w, int h, long ms) {
        float visible=ease(ms,790,1800);
        if (visible<=0) return;
        c.save();
        c.scale(w*.55f,h);
        paint.setColor(Color.WHITE); paint.setShader(leftShade); paint.setAlpha(Math.round(112*visible));
        c.drawRect(0,0,1,1,paint);
        paint.setShader(null);
        c.restore();

        float height=Math.min(h*.515f,w*.335f*TITLE_HEIGHT/TITLE_WIDTH);
        float size=height/TITLE_HEIGHT, x=w*.072f, y=h*.465f-height*.51f;
        c.save();
        c.translate(x,y); c.scale(size,size);
        boolean glint=!reducedMotion && ms>=2300 && ms<3070;
        int layer=glint?c.saveLayer(-24,-22,TITLE_WIDTH+30,TITLE_HEIGHT+24,null):0;
        if(a.en){
            float topIn=ease(ms,1060,1640),baseIn=ease(ms,1300,2030);
            if(title!=null)bitmap(c,title,null,0,155+(1-topIn)*42,TITLE_WIDTH,204,(int)(255*topIn));
            if(lowerEnglishWord!=null)bitmap(c,lowerEnglishWord,null,0,365+(1-baseIn)*50,TITLE_WIDTH,225,(int)(255*baseIn));
        }else for(int i=0;i<LETTERS.length;i++)drawLetter(c,i,ms);
        if (glint) {
            float sweep=mix(-115,TITLE_WIDTH+116,ease(ms,2300,3070));
            c.save(); c.translate(sweep,0);
            paint.setColor(Color.WHITE); paint.setShader(foil); paint.setXfermode(foilMask);
            c.drawRect(-205,-20,208,TITLE_HEIGHT+22,paint);
            paint.setXfermode(null); paint.setShader(null);
            c.restore();
            c.restoreToCount(layer);
        }
        c.restore();

        float show=ease(ms,2490,2860);
        if (show<=0) return;
        letters.setTypeface(a.title);
        float textSize=h*.045f;
        letters.setTextSize(textSize);
        float measured=letters.measureText(a.tr(Arena.LOBBY_SLOGAN));
        float available=Math.min(w*.415f,Math.max(w*.325f,size*TITLE_WIDTH*1.15f));
        if (measured>available) letters.setTextSize(textSize*available/measured);
        float baseline=y+height+h*(.058f+.011f*(1-show));
        float left=x+size*11;
        letters.setColor(Color.argb(Math.round(170*show),21,16,14));
        c.drawText(a.tr(Arena.LOBBY_SLOGAN),left,baseline+h*.0018f,letters);
        letters.setColor(Color.argb(Math.round(250*show),250,229,185));
        c.drawText(a.tr(Arena.LOBBY_SLOGAN),left,baseline,letters);
    }

    private void lobby(Canvas c, int w, int h) {
        a.scale=Math.min(w/1080f,h/520f);
        a.offX=(w-1080*a.scale)*.5f; a.offY=(h-520*a.scale)*.5f;
        c.drawColor(NIGHT);
        a.pubMenu.backdrop(w,h);
        c.save(); c.translate(a.offX,a.offY); c.scale(a.scale,a.scale);
        a.lobby(); c.restore();
        a.hits.clear();
    }

    private void startPrompt(Canvas c,int w,int h,long ms){
        float show=reducedMotion?1:ease(ms,PROMPT_MS-130,PROMPT_MS+330);
        if(show<=0)return;
        float left=w*.072f,wide=Math.min(w*.337f,h*1.00f);
        float high=wide*134/720f,top=Math.min(h*.842f,h*.937f-high);
        if(pressed&&canStartOnRelease)top+=h*.004f;
        if(entryPlate!=null)bitmap(c,entryPlate,null,left,top,wide,high,Math.round(255*show));
        else{paint.setColor(0xeb132e25);target.set(left,top,left+wide,top+high);c.drawRoundRect(target,high*.2f,high*.2f,paint);}
        float light=reducedMotion?0:.5f+.5f*(float)Math.sin((ms-PROMPT_MS)*.0019);
        if(!reducedMotion)glow(c,left+.10f*wide,top+.5f*high,wide*.066f,high*.38f,(int)((16+44*light)*show));
        letters.setTypeface(a.title);letters.setTextAlign(Paint.Align.CENTER);
        letters.setTextSize(high*.43f);letters.setColor(Color.argb(Math.round(255*show),255,235,194));
        c.drawText(a.tr("눌러서 시작하기"),left+wide*.56f,top+high*.5f-(letters.ascent()+letters.descent())*.5f,letters);
        letters.setTextAlign(Paint.Align.LEFT);
    }

    boolean draw(Canvas c, long now) {
        if (!blocking()) return false;
        a.hits.clear();
        if (pending) { c.drawColor(NIGHT); return true; }
        if(a.host.active&&lastFrame!=0)playhead+=Math.max(0,now-lastFrame);
        lastFrame=a.host.active?now:0;
        long ms=playhead;
        if(exitAt>=0&&ms-exitAt>=EXIT_MS){finish();return false;}
        int w=a.getWidth(),h=a.getHeight();
        long shot=reducedMotion?3450:ms;
        if (exitAt<0||ms-exitAt<EXIT_MS/2) {
            street(c,w,h,shot);
            if (!reducedMotion) { rain(c,w,h,shot); foreground(c,w,h,shot); }
            title(c,w,h,shot);
            startPrompt(c,w,h,ms);
            float dark=Math.max(1-ease(shot,0,260),exitAt<0?0:ease(ms-exitAt,0,EXIT_MS/2));
            if (dark>0) c.drawColor((Math.round(255*dark)<<24)|(NIGHT&0x00ffffff));
        } else {
            lobby(c,w,h);
            float dark=1-ease(ms-exitAt,EXIT_MS/2,EXIT_MS);
            c.drawColor((Math.round(255*dark)<<24)|(NIGHT&0x00ffffff));
        }
        if (!sounded && shot>=ENTRANCES[0]) {
            sounded=true;
            if (a.host.active && a.opt.optBoolean("sound")) a.host.fx("opening",.48f);
        }
        if (a.host.active) {
            if(!reducedMotion){
                if(ms>PROMPT_MS+550&&exitAt<0)a.postInvalidateDelayed(32);else a.postInvalidateOnAnimation();
            }
        }
        return true;
    }
}
