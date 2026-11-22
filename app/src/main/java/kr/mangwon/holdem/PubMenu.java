package kr.mangwon.holdem;

import android.animation.ValueAnimator;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;

/** The three painted club entrances share a common brass rail and a single touch coordinate system. */
final class PubMenu {
    static final int[][] FEES = {{50000, 100000}, {100000, 150000, 200000}};
    private static final String[] NAMES = {"망원동", "마포구", "당산"};
    private static final String[] KEYS = {"venue0", "venue1", "venue2"};
    private static final String[] MOTTO = Arena.LOBBY_SLOGAN.split(" ");
    private static final String[] FORMATS = {
        "데일리 토너먼트  ·  2–4테이블",
        "마포구 대회  ·  12 / 24테이블",
        "원보드 / 더블보드  ·  6인 또는 9인"
    };
    private static final String[] STAKES = {"5만 – 10만 P", "10만 – 20만 P", "20만 – 200만 P"};
    private static final int[] INKS = {0x071713, 0x1e0e13, 0x071a29};
    private static final int[] HIGHLIGHTS = {0xfff8d69b, 0xffe6b6a0, 0xffb6dced};
    private static final int IVORY = 0xffffedcc, BRASS = 0xffd7bc88;
    private static final Shader.TileMode CLAMP = Shader.TileMode.CLAMP;
    private static final Rect CREST_SOURCE = new Rect(106,96,325,335);
    private static final Paint.Align LEFT = Paint.Align.LEFT, RIGHT = Paint.Align.RIGHT;

    private final Arena a;
    private final Bitmap[] portraits = new Bitmap[3];
    private final float[] presses = new float[3];
    private final float[] nameWidths = new float[3];
    private final float[] mottoWidths = new float[MOTTO.length];
    private final float[][] glyphWidths = new float[3][];
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint letters = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
    private final Bitmap crest;
    private final RectF bounds = new RectF();
    private final Rect chipSource = new Rect();
    private final Path arrow = new Path();
    private final LinearGradient[] veils = new LinearGradient[3];
    private final LinearGradient foil = new LinearGradient(-45,0,45,0,
        new int[]{0xfff2d8a9,0xfff5dfb8,0xfffffff1,0xfff5dfb8,0xfff2d8a9},
        new float[]{0,.32f,.5f,.68f,1},CLAMP);
    private final RadialGradient lamplight = new RadialGradient(0,0,1,0xfff5bd68,0x00efaf52,CLAMP);
    private final Matrix foilPosition = new Matrix();
    private LinearGradient headerShade;
    private float left, right, top, bottom, span;
    private int width, height, lastWallet = -1;
    private String wallet = "";
    final VenueArrival arrival;
    private long clock, lastFrame, lastMotionCheck, entered;
    private boolean motion = true, firstFrame = true, moving;

    PubMenu(Arena arena) {
        a = arena;
        arrival = new VenueArrival(arena,this);
        Bitmap atlas = a.image("lobby-venues.webp");
        if (atlas == null) atlas = a.image("venue-doors.jpg");
        for (int i = 0; i < portraits.length; i++) {
            portraits[i] = a.image("arrival-"+VenueArrival.ART[i]+"-still.webp");
            if (portraits[i]==null && atlas != null) {
                int x = i * atlas.getWidth()/3;
                Bitmap art = Bitmap.createBitmap(atlas,x,0,(i+1)*atlas.getWidth()/3-x,atlas.getHeight());
                portraits[i] = art;
            }
            glyphWidths[i] = new float[NAMES[i].length()];
            letters.setTypeface(a.title);
            letters.setTextSize(40);
            for (int j = 0; j < NAMES[i].length(); j++) {
                glyphWidths[i][j] = letters.measureText(NAMES[i],j,j+1);
                nameWidths[i] += glyphWidths[i][j];
            }
        }
        crest = BitmapFactory.decodeResource(a.getResources(),R.drawable.launcher_spade);
        arrow.moveTo(-3.5f,-5); arrow.lineTo(1.5f,0); arrow.lineTo(-3.5f,5);
        letters.setTypeface(a.normal);
        letters.setTextSize(14);
        for (int i=0;i<MOTTO.length;i++) mottoWidths[i]=letters.measureText(MOTTO[i])+letters.measureText(" ");
    }

    private void layout(int w, int h) {
        if (width == w && height == h) return;
        width = w; height = h;
        left = -a.offX/a.scale; top = -a.offY/a.scale;
        right = (w-a.offX)/a.scale; bottom = (h-a.offY)/a.scale;
        span = (right-left)/3;
        headerShade = new LinearGradient(0,top,0,top+114,
            new int[]{0xf7111514,0xec111514,0xc9101514,0x6d101514,0x00101514},
            new float[]{0,.39f,.53f,.67f,1},CLAMP);
        for (int i=0; i<3; i++) {
            int k = INKS[i];
            veils[i] = new LinearGradient(0,bottom-214,0,bottom,new int[]{
                k,0x17000000|k,0x85000000|k,0xdb000000|k,0xf6000000|k},
                new float[]{0,.19f,.48f,.74f,1},CLAMP);
        }
    }

    void backdrop(int w, int h) { backdrop(w,h,true); }

    void backdrop(int w, int h, boolean animateCast) {
        layout(w,h);
        if (firstFrame || a.now-lastMotionCheck>1000) {
            motion = ValueAnimator.areAnimatorsEnabled();
            lastMotionCheck = a.now;
        }
        moving = animateCast && motion && a.host.active && a.panel.isEmpty();
        if (firstFrame || a.now-lastFrame>1100) entered = clock;
        long step = firstFrame ? 0 : Math.min(60,Math.max(0,a.now-lastFrame));
        lastFrame = a.now; firstFrame = false;
        if (moving) clock += step;
        Canvas c = a.c;
        c.save(); c.translate(a.offX,a.offY); c.scale(a.scale,a.scale);
        for (int i=0; i<3; i++) {
            boolean pressed = KEYS[i].equals(a.held) || (a.g!=null && liveVenue()==i && "entry".equals(a.held));
            float target = pressed ? 1f : 0f;
            presses[i] += (target-presses[i]) * (moving ? Math.min(1,step/58f) : 1);
            float l = left+span*i, r = i==2 ? right : left+span*(i+1);
            c.save(); c.clipRect(l,top,r,bottom);
            if (portraits[i]!=null) drawPainting(i,l,r);
            else c.drawColor(0xff000000|INKS[i]);
            fill(veils[i],l,top,r,bottom);
            c.restore();
        }
        fill(headerShade,left,top,right,top+115);
        for (int i=1;i<3;i++) {
            float x=left+i*span;
            a.line(x-1,top+56,x-1,bottom,0xff201c1b,1.5f);
            a.line(x,top+56,x,bottom,0xb49c865d,1);
        }
        a.line(left,top+56,right,top+56,0x9a77684b,1);
        c.restore();
    }

    private void drawPainting(int i, float l, float r) {
        Bitmap art = portraits[i];
        float sw = art.getWidth(), sh = art.getHeight();
        double phase = clock*(2*Math.PI/(i==1 ? 10900 : i==2 ? 13700 : 12100)) + i*1.7;
        // Overscan gives the slow camera a few pixels to travel with no exposed edges.
        float breathe = motion ? (float)Math.sin(phase) : 0;
        float scale = Math.max((r-l)/sw,(bottom-top)/sh) * (1.025f+.005f*breathe+.018f*presses[i]);
        float x = (l+r-scale*sw)*.5f+2.4f*breathe;
        float y = (top+bottom-scale*sh)*.5f;
        Canvas c = a.c;
        c.save(); c.translate(x,y); c.scale(scale*sw,scale*sh);
        solid(Color.WHITE);
        bounds.set(0,0,1,1);c.drawBitmap(art,null,bounds,paint);
        if (motion) sceneLight(i,phase);
        c.restore();
    }

    void arrivingPortrait(int i,float center,float zoom,Bitmap sheet,int frame,float edgeFade,Paint edgePaint){
        Bitmap still=portraits[i];
        if(still==null)return;
        float sw=still.getWidth(),sh=still.getHeight();
        float base=Math.max(span/sw,(bottom-top)/sh)*1.026f;
        float dw=sw*base*zoom,dh=sh*base*zoom;
        Canvas c=a.c;
        c.save();c.translate(center-dw/2,(top+bottom-dh)/2);c.scale(dw,dh);
        int layer=edgeFade>0?c.saveLayer(0,0,1,1,null):-1;
        bounds.set(0,0,1,1);solid(Color.WHITE);
        if(sheet==null)c.drawBitmap(still,null,bounds,paint);
        else{
            int col=frame%4,row=frame%12/4,cellW=sheet.getWidth()/4,cellH=sheet.getHeight()/3;
            chipSource.set(col*cellW,row*cellH,(col+1)*cellW,(row+1)*cellH);
            c.drawBitmap(sheet,chipSource,bounds,paint);
        }
        if(layer>=0){
            edgePaint.setAlpha(Math.round(255*edgeFade));
            c.drawRect(0,0,1,1,edgePaint);c.restoreToCount(layer);
        }
        c.restore();
    }
    /** Scene effects are located on the objects in each illustration, in UV coordinates. */
    private void sceneLight(int i, double phase) {
        if (i==0) {
            glow(.528f,.061f,.245f,.091f,(int)(30+14*Math.sin(phase*2.1)));
        } else if (i==1) {
            float t=(clock%6900)/1050f;
            if (t<1) {
                float u=(float)Math.sin(t*Math.PI);
                float x=.735f+.16f*t, y=.17f-.030f*t;
                glow(x,y,.047f,.016f,(int)(110*u));
                glow(x,y,.005f,.024f,(int)(85*u));
            }
        } else {
            for (int j=0;j<5;j++) {
                double beat=phase*(1.3+j*.09)+1.51*j;
                float x=.083f+.052f*j+.007f*(float)Math.sin(beat);
                float y=.282f+.008f*j;
                glow(x,y,.023f,.0024f,(int)(43+29*Math.sin(beat+.7)));
            }
            glow(.954f,.076f,.109f,.106f,(int)(16+9*Math.sin(phase*1.3)));
        }
    }

    private void glow(float x, float y, float rx, float ry, int alpha) {
        Canvas c = a.c;
        c.save(); c.translate(x,y); c.scale(rx,ry);
        solid(Color.WHITE); paint.setShader(lamplight); paint.setAlpha(alpha);
        c.drawCircle(0,0,1,paint); c.restore();
    }

    private void solid(int color) {
        paint.setShader(null); paint.setStyle(Paint.Style.FILL); paint.setColor(color);
    }

    private void fill(Shader shader,float l,float t,float r,float b) {
        solid(Color.WHITE); paint.setShader(shader); a.c.drawRect(l,t,r,b,paint);
    }

    private int liveVenue() {
        return Math.max(0,Math.min(2,a.g==null ? a.venue : a.g.optInt("pub")));
    }

    float titleX() { return left+61; }
    float titleY() { return top+38; }

    void draw() {
        if ("entry".equals(a.panel)) return;
        drawTitle(255);
        controls();
    }

    void drawTitle(int alpha) {
        float x=titleX(), y=titleY();
        if (crest!=null) {
            bounds.set(x-38,y-30,x-8,y+3);
            solid(Color.WHITE); paint.setAlpha(alpha); a.c.drawBitmap(crest,CREST_SOURCE,bounds,paint);
        }
        a.text("망원동 홀덤",x,y,29,(alpha<<24)|0xf4e4bf,a.title,LEFT);
        a.hit(left+12,top+1,x+145,top+54,"replayIntro");

        float wordX=x+161;
        a.line(wordX-13,y-15,wordX-13,y+1,((alpha*95/255)<<24)|0xc7ab80,1);
        for (int i=0;i<MOTTO.length;i++) {
            float t=moving ? Math.max(0,Math.min(1,(clock-entered+130-55*i)/420f)) : 1;
            float rest=(1-t)*(1-t);
            a.text(MOTTO[i],wordX,y-3+rest*5,14,
                ((int)(alpha*(.43f+.57f*t))<<24)|0xd3c7ad,a.normal,LEFT);
            wordX+=mottoWidths[i];
        }
    }

    void controls() {
        headerButtons();
        for (int i=0;i<3;i++) venue(i);
    }

    private void headerButtons() {
        float y=top+29, settings=right-28, divider=right-102, walletRight=right-121;
        int funds=a.snapshot.optInt("wallet",50000);
        if (funds!=lastWallet) { wallet=a.fmt(funds)+" P"; lastWallet=funds; }
        letters.setTextSize(19); letters.setTypeface(a.nums);
        float walletWidth=letters.measureText(wallet);
        float walletLeft=walletRight-33-walletWidth-27;
        boolean music=a.host.musicEnabled;
        int muted=music ? 0xffe0d9c3 : 0xffaba99d;
        float soundX=walletLeft-104;
        a.brass("GTO 구매하기",soundX-129,y-15,118,"gtoStore");
        paint.setStrokeCap(Paint.Cap.ROUND);
        a.line(soundX+8,y+4,soundX+8,y-8,muted,1.8f);
        a.line(soundX+8,y-8,soundX+14,y-6,muted,2);
        solid(muted); a.c.drawOval(soundX+1,y+1,soundX+9,y+7,paint);
        if (!music) a.line(soundX,y-8,soundX+16,y+7,muted,1.6f);
        a.text(music?"음악 켬":"음악 끔",soundX+22,y+5,14,muted,a.normal,LEFT);
        a.hit(soundX-7,top+3,walletLeft-15,top+54,"music");
        a.line(walletLeft-9,y-9,walletLeft-9,y+9,0x977b6e55,1);
        Bitmap chips=a.chipArt;
        if (chips!=null) {
            chipSource.set(0,0,chips.getWidth()/2,chips.getHeight()/2);
            bounds.set(walletLeft-1,y-13,walletLeft+25,y+13);
            solid(Color.WHITE); a.c.drawBitmap(chips,chipSource,bounds,paint);
        }
        a.text(wallet,walletRight-29,y+6.5f,19,IVORY,a.nums,RIGHT);
        solid(0xffc8a56d); a.c.drawCircle(walletRight-11,y,9.5f,paint);
        a.line(walletRight-15,y,walletRight-7,y,0xff29251f,1.7f);
        a.line(walletRight-11,y-4,walletRight-11,y+4,0xff29251f,1.7f);
        a.hit(walletLeft-5,top+3,walletRight+5,top+54,"shop");
        a.line(divider,y-9,divider,y+9,0x977b6e55,1);
        drawGear(settings-39,y);
        a.text("설정",settings,y+5,15,IVORY,a.bold,RIGHT);
        a.hit(divider+5,top+3,right-6,top+54,"settings");
    }

    private void drawGear(float x,float y) {
        solid(0xffdaccad); paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(1.6f);
        a.c.drawCircle(x,y,5.3f,paint);
        for (int i=0;i<8;i++) {
            a.c.save(); a.c.rotate(i*45,x,y);
            a.c.drawLine(x,y-5.2f,x,y-7.5f,paint); a.c.restore();
        }
        solid(0xffdaccad); a.c.drawCircle(x,y,1.7f,paint);
    }

    private void venue(int i) {
        org.json.JSONObject seat=a.savedVenue(i);
        boolean session=seat!=null, selected=i==liveVenue(), playable=true;
        float l=left+i*span, r=l+span, x=l+23;
        int info=playable ? 0xffe2dece : 0xffa9adab;
        float lift=presses[i]*2.6f;
        String format = session ? (i==2 ? (seat.optBoolean("double")?"더블보드":"원보드") : "토너먼트")+"  ·  "+seat.optInt("hand")+"번째 핸드" : FORMATS[i];
        a.text(format,x,bottom-108-lift,16,info,a.bold,LEFT);
        sign(i,x,bottom-62-lift,playable);
        a.line(x,bottom-49,r-22,bottom-49,playable?0x775f695c:0x555f695c,1);
        a.text("바이인",x,bottom-21,14,info,a.normal,LEFT);
        a.text(session?a.shortP(seat.optInt("buyin"))+" P":STAKES[i],x+49,bottom-20,19,
            playable ? IVORY : info,a.nums,LEFT);
        if (playable) {
            String cta=session ? "이어하기" : "입장";
            float targetRight=r-19, targetLeft=targetRight-(session ? 89 : 72);
            if (selected) {
                a.stage.plate(targetLeft,bottom-44+presses[i],targetRight,bottom-7+presses[i],1);
                a.text(cta,(targetLeft+targetRight)*.5f-4,bottom-19+presses[i],
                    session?13:15,0xff272317,a.bold,Paint.Align.CENTER);
                chevron(targetRight-15,bottom-25+presses[i],0xff44341e);
            } else {
                a.text(cta,targetRight-21,bottom-20,15,HIGHLIGHTS[i],a.bold,RIGHT);
                chevron(targetRight-7,bottom-25,HIGHLIGHTS[i]);
            }
            a.hit(l,top+58,r,bottom,KEYS[i]);
        }
        a.line(l+1,top+57,r-1,top+57,selected?0xffebc88d:0x3caaa082,selected?2:1);
    }

    private void chevron(float x,float y,int color) {
        a.c.save(); a.c.translate(x,y);
        solid(color); paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(1.8f);
        paint.setStrokeJoin(Paint.Join.ROUND); a.c.drawPath(arrow,paint); a.c.restore();
    }

    private void sign(int i,float x,float y,boolean playable) {
        String name=NAMES[i];
        long age=clock-entered;
        boolean animated=moving && playable;
        float sweep=((clock+7500-i*1700)%9300)/1250f;
        boolean glint=animated && sweep<1;
        letters.setTypeface(a.title); letters.setTextSize(40); letters.setTextAlign(LEFT);
        if (glint) {
            foilPosition.setTranslate(x-64+sweep*(nameWidths[i]+127),0);
            foil.setLocalMatrix(foilPosition);
        }
        float start=x;
        for (int j=0;j<name.length();j++) {
            float arrival=animated ? Math.max(0,Math.min(1,(age+155-i*64-j*64)/470f)) : 1;
            float remain=(1-arrival)*(1-arrival);
            float pulse=animated?(float)Math.sin(clock*.00148+i*1.37-j*.57)*.63f:0;
            float glyphY=y+remain*12+pulse-presses[i]*(1.7f+(j%2)*1.4f);
            a.c.save(); a.c.rotate(remain*(-5.8f+j*1.1f)+pulse*.4f,x+glyphWidths[i][j]*.5f,glyphY);
            letters.setShader(null); letters.setColor(0xab100e0b);
            a.c.drawText(name,j,j+1,x+.7f,glyphY+1.6f,letters);
            letters.setColor(playable?0xfff4dfb7:0xffb0b3aa); letters.setShader(glint?foil:null);
            a.c.drawText(name,j,j+1,x,glyphY,letters);
            a.c.restore();
            x+=glyphWidths[i][j];
        }
        letters.setShader(null);
        a.text(i==1?"홀덤대회":"홀덤펍",start+nameWidths[i]+10,y-1,20,
            playable?HIGHLIGHTS[i]:0xffa9ada7,a.title,LEFT);
    }

    void schedule() {
        if (!motion) {
            if (a.now<a.toastEnd) a.postInvalidateDelayed(a.toastEnd-a.now);
        } else if (!a.held.isEmpty() || clock-entered<950) a.postInvalidateOnAnimation();
        else a.postInvalidateDelayed(33);
    }

}
