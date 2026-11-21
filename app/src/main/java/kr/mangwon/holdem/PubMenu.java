package kr.mangwon.holdem;

import android.animation.ValueAnimator;
import android.graphics.*;

/** Painted pub entrance and its fixed, touchable venue directory. */
final class PubMenu {
    static final int[][] FEES={{50000,100000},{100000,150000,200000}};
    private static final String[] STAKES={"5만 – 10만 P","10만 – 20만 P","20만 – 70만 P"};
    private static final String[] FORMAT={"데일리 토너먼트  ·  2테이블  ·  18명","데일리 토너먼트  ·  4테이블  ·  36명","원보드 / 더블보드  ·  6인 또는 9인"};
    private static final String[] VENUE_KEYS={"venue0","venue1","venue2"};
    private static final int CREAM=0xfff6e4b9, BRASS=0xffd7b478, SUBTLE=0xffd3ccba;
    private static final float[] DROPS_X={863,879,1001,1051,1403,1443,1462};
    private static final int[] DROPS_Y={31,50,24,35,138,129,140};
    private static final int[] FALL={31,28,40,49,49,58,42};
    private static final int[] PERIOD={3300,4400,4900,4200,5800,3900,5300};

    private final Arena a;
    private final Bitmap doors;
    private final PortraitMotion[] cast;
    private final Rect[] photo={new Rect(),new Rect(),new Rect()};
    private final RectF dst=new RectF();
    private final BitmapShader scene;
    private final Matrix map=new Matrix();
    private final RadialGradient warmth=new RadialGradient(0,0,1,0xc1ffce7c,0x00ffac56,Shader.TileMode.CLAMP);
    private LinearGradient woodShade,topShade,bleedShade;
    private final LinearGradient rowShade=new LinearGradient(121,0,440,0,0xeb14251e,0xf120241e,Shader.TileMode.CLAMP);
    private final LinearGradient titleFoil=new LinearGradient(0,53,0,102,0xfffff1cc,0xffddb77b,Shader.TileMode.CLAMP);
    private final Arena.Hit[] venueHits=new Arena.Hit[3];
    private final float[] pressed=new float[3];
    private int layoutWidth,layoutHeight,previousWallet=-1,previousHand=-1,previousBuy,previousPub,previousFormat;
    private String walletText="",resumeText="";
    private float sceneScale,sceneLeft,sceneTop;
    private long lastFrame,elapsed,settingChecked;
    private boolean allowMotion=true;

    PubMenu(Arena arena){
        a=arena;doors=a.image("venue-doors.jpg");
        scene=a.pub==null?null:new BitmapShader(a.pub,Shader.TileMode.CLAMP,Shader.TileMode.CLAMP);
        cast=a.pub==null?new PortraitMotion[0]:new PortraitMotion[]{
            new PortraitMotion(a.pub,485,196,347,330,-1.3f,2.9f,5100),
            new PortraitMotion(a.pub,820,123,260,280,1.4f,2.6f,4560),
            new PortraitMotion(a.pub,1183,376,218,174,-2.4f,1.5f,5340),
            new PortraitMotion(a.pub,1482,231,245,290,.8f,2.1f,6100)
        };
        for(int i=0;i<3;i++){
            if(doors!=null){
                int width=doors.getWidth()/3;
                photo[i].set(i*width+2,3,(i+1)*width-2,doors.getHeight()-3);
            }
            float y=rowTop(i);
            venueHits[i]=new Arena.Hit(38,y-2,452,y+84,VENUE_KEYS[i]);
        }
    }
    private static float rowTop(int i){return 140+96*i;}
    private void resize(int w,int h){
        if(w==layoutWidth&&h==layoutHeight)return;
        layoutWidth=w;layoutHeight=h;
        float unit=a.scale;
        // Preserve the cast on wider foldable screens. Vertical overscan falls into the frame shadow.
        sceneScale=Math.min(w/1774f,Math.max(h/887f,w/1774f*.97f));
        sceneLeft=(w-1774*sceneScale)*.50f+unit*46;
        sceneTop=(h-887*sceneScale)*.5f;
        if(scene!=null){
            map.setScale(sceneScale*1774/a.pub.getWidth(),sceneScale*887/a.pub.getHeight());
            map.postTranslate(sceneLeft,sceneTop);scene.setLocalMatrix(map);
        }
        float x=a.offX,y=a.offY;
        woodShade=new LinearGradient(x,0,x+595*unit,0,
            new int[]{0xf0171715,0xd51b1915,0x801a1814,0x001a1814},new float[]{0,.5f,.76f,1},Shader.TileMode.CLAMP);
        topShade=new LinearGradient(0,0,0,Math.max(1,y+71*unit),0x8c100e11,0x00100e11,Shader.TileMode.CLAMP);
        if(sceneTop>1){
            float edge=Math.min(.35f,sceneTop/h);
            bleedShade=new LinearGradient(0,0,0,h,new int[]{0xff161713,0xf0171714,0x00171714,0x00171714,0xf0171714,0xff161713},
                new float[]{0,edge*.75f,edge+.035f,.965f-edge,1-edge*.75f,1},Shader.TileMode.CLAMP);
        }else bleedShade=null;
    }
    private void shade(Shader shader,float l,float t,float r,float b){
        a.reset(-1);a.p.setShader(shader);a.c.drawRect(l,t,r,b,a.p);
    }
    private void light(float x,float y,float rx,float ry,int alpha){
        a.c.save();a.c.translate(x,y);a.c.scale(rx,ry);a.reset(-1);a.p.setShader(warmth);a.p.setAlpha(alpha);
        a.c.drawCircle(0,0,1,a.p);a.c.restore();
    }
    void backdrop(int w,int h){
        backdrop(w,h,true);
    }
    void backdrop(int w,int h,boolean animateCast){
        resize(w,h);
        if(a.now-settingChecked>1000){allowMotion=ValueAnimator.areAnimatorsEnabled();settingChecked=a.now;}
        long delta=lastFrame==0?0:Math.min(50,a.now-lastFrame);
        lastFrame=a.now;
        boolean moving=animateCast&&allowMotion&&a.host.active&&a.panel.isEmpty();
        if(moving)elapsed+=delta;
        if(scene!=null){
            shade(scene,0,0,w,h);
            if(moving){
                a.c.save();a.c.translate(sceneLeft,sceneTop);a.c.scale(sceneScale,sceneScale);
                details();a.c.restore();
            }
        }
        if(bleedShade!=null)shade(bleedShade,0,0,w,h);
        shade(woodShade,0,0,w,h);shade(topShade,0,0,w,h);
        for(int i=0;i<3;i++){
            float target=VENUE_KEYS[i].equals(a.held)?1:0;
            pressed[i]=moving?pressed[i]+(target-pressed[i])*Math.min(1,delta/65f):target;
        }
    }
    private void details(){
        for(int i=0;i<cast.length;i++)cast[i].draw(a,elapsed+i*1543L);
        float beat=(float)Math.sin(elapsed*.00074)+.4f*(float)Math.sin(elapsed*.00161);
        light(1253,45,163,71,(int)(38+beat*13));
        light(1572,122,40,66,(int)(30+beat*7));
        for(int i=0;i<DROPS_X.length;i++){
            float u=((elapsed+511L*i)%PERIOD[i])/1100f;
            if(u>=1)continue;
            float x=DROPS_X[i]+u*.8f,y=DROPS_Y[i]+u*u*FALL[i];
            int alpha=Math.round(98*(float)Math.sin(u*Math.PI));
            a.line(x,y-6-u*4,x,y,(alpha<<24)|0xb5d5dc,1.55f);
            a.reset((alpha<<24)|0xe8e6ca);a.c.drawCircle(x,y+.5f,1.06f,a.p);
        }
        float shine=(elapsed%9700)/1700f;
        if(shine<1){
            int alpha=Math.round(157*(float)Math.sin(shine*Math.PI));
            float x=948+shine*56;
            a.line(x-4,587,x+4,587,(alpha<<24)|0xffde9d,1.6f);
        }
    }
    /** Move the ink and its shading together while pinning every edge to the painted room. */
    private static final class PortraitMotion {
        private static final int COLS=8,ROWS=7,VERTICES=(COLS+1)*(ROWS+1);
        private final float[] origin=new float[2*VERTICES],mesh=new float[2*VERTICES],weight=new float[VERTICES];
        private final Bitmap patch;
        private final float dx,dy;private final int period;
        PortraitMotion(Bitmap art,int l,int t,int w,int h,float dx,float dy,int period){
            this.dx=dx;this.dy=dy;this.period=period;
            float sx=art.getWidth()/1774f,sy=art.getHeight()/887f;
            patch=Bitmap.createBitmap(art,Math.round(l*sx),Math.round(t*sy),Math.round(w*sx),Math.round(h*sy));
            for(int j=0;j<=ROWS;j++)for(int i=0;i<=COLS;i++){
                int k=j*(COLS+1)+i;float u=i/(float)COLS,v=j/(float)ROWS;
                origin[k*2]=l+u*w;origin[k*2+1]=t+v*h;
                float envelope=(float)(Math.sin(Math.PI*u)*Math.sin(Math.PI*v));
                weight[k]=envelope*envelope;
            }
        }
        void draw(Arena a,long clock){
            double phase=(clock%period)*(2*Math.PI/period);
            float x=dx*(float)Math.sin(phase),y=dy*(float)Math.sin(phase+.95);
            for(int k=0;k<VERTICES;k++){
                mesh[k*2]=origin[k*2]+x*weight[k];mesh[k*2+1]=origin[k*2+1]+y*weight[k];
            }
            a.reset(-1);a.c.drawBitmapMesh(patch,COLS,ROWS,mesh,0,null,0,a.p);
        }
    }
    void draw(){
        if(a.panel.equals("entry"))return;
        drawTitle(255);
        controls();
    }
    void drawTitle(int alpha){
        a.text("망원동 홀덤",48,102,52,Color.argb(207*alpha/255,33,23,14),a.title,Paint.Align.LEFT);
        a.reset(-1);a.p.setShader(titleFoil);a.p.setTypeface(a.title);a.p.setTextSize(52);
        a.p.setAlpha(alpha);
        a.c.drawText("망원동 홀덤",47,100,a.p);
    }
    void controls(){
        a.musicButton(698,24);
        int wallet=a.snapshot.optInt("wallet",50000);
        if(wallet!=previousWallet){walletText=a.fmt(wallet)+" P   +";previousWallet=wallet;}
        a.brass(walletText,793,25,157,"shop");a.brass("설정",962,25,78,"settings");
        if(a.g!=null){resume();return;}
        for(int i=0;i<3;i++)venue(i);
    }
    private void door(int i,float x,float y,float size,int alpha){
        if(doors==null)return;
        dst.set(x,y,x+size,y+size);a.reset(-1);a.p.setAlpha(alpha);a.c.drawBitmap(doors,photo[i],dst,a.p);
    }
    private void venue(int i){
        float y=rowTop(i),press=pressed[i],x=40,edge=448;
        a.c.save();a.c.translate(0,press*1.7f);
        a.box(x,y+3,edge,y+82,3,0x7c0e0e0b);
        a.box(x,y,edge,y+79,2,0xff24211a);
        shade(rowShade,x+77,y+1,edge-1,y+78);
        door(i,x+2,y+2,75,255);
        a.line(x+78,y+1,x+78,y+78,0xff786345,1);
        a.stroke(x+.5f,y+.5f,edge-.5f,y+78.5f,2,0xff786748,1);
        if(press>.01f)a.box(x+1,y+1,edge-1,y+78,1,((int)(44*press)<<24)|0xe3bf75);
        a.text(a.pubs[i],131,y+33,23,CREAM,a.bold,Paint.Align.LEFT);
        a.text(STAKES[i],431,y+32,16,BRASS,a.nums,Paint.Align.RIGHT);
        a.text(FORMAT[i],132,y+60,15,SUBTLE,a.normal,Paint.Align.LEFT);
        a.c.restore();a.hits.add(venueHits[i]);
    }
    private void resume(){
        int pub=a.g.optInt("pub"),buy=a.g.optInt("buyin"),hand=a.g.optInt("hand");
        int mode=a.g.optBoolean("cash")?(a.g.optBoolean("double")?2:1):0;
        if(hand!=previousHand||pub!=previousPub||buy!=previousBuy||mode!=previousFormat){
            String format=mode==2?"더블보드":mode==1?"원보드":"데일리 토너먼트";
            resumeText=format+"  ·  "+a.shortP(buy)+"P  ·  "+hand+"번째 핸드";
            previousHand=hand;previousPub=pub;previousBuy=buy;previousFormat=mode;
        }
        a.txt("진행 중인 게임",51,170,16,BRASS);
        a.box(42,192,446,287,2,0xec17251f);
        door(pub,45,195,89,255);
        a.stroke(42,192,446,287,2,0xff796847,1);
        a.text(a.pubs[pub],148,232,26,CREAM,a.bold,Paint.Align.LEFT);
        a.txt(resumeText,148,262,15,SUBTLE);
        a.key("내 자리로 돌아가기",50,313,250,53,"entry",0);
    }
    void schedule(){
        if(allowMotion&&!a.held.isEmpty())a.postInvalidateOnAnimation();
        else if(allowMotion)a.postInvalidateDelayed(33);
        else if(a.now<a.toastEnd)a.postInvalidateDelayed(a.toastEnd-a.now);
    }
}
