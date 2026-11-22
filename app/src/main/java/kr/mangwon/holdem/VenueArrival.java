package kr.mangwon.holdem;

import android.animation.ValueAnimator;
import android.graphics.*;
import android.os.SystemClock;
import android.view.MotionEvent;
import java.io.InputStream;
import java.io.IOException;

/** Opens a painted lobby panel into its room while the seated host gestures towards the table. */
final class VenueArrival {
    static final String[] ART={"mangwon","mapo","dangsan"};
    private static final int CROSSOVER=4810, END=5260;
    private final Bitmap[] pages=new Bitmap[5];
    private final String[] percentages=new String[101];
    private volatile int loadId;
    private int decoded;
    private final Arena a;
    private final PubMenu menu;
    private final Bitmap[] rooms=new Bitmap[3];
    private Bitmap mark;
    private final Paint ink=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
    private final Paint edge=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF dst=new RectF();
    private final Path bolt=new Path(),fork=new Path();
    private final LinearGradient titleShadow=new LinearGradient(0,0,1,0,new int[]{0x00131a19,0xa015191a,0xf3111716},new float[]{0,.39f,1},Shader.TileMode.CLAMP);
    private int venue=-1;
    private long time,last;
    private boolean opened,pressed,reduced;

    VenueArrival(Arena arena,PubMenu pub){
        a=arena;menu=pub;
        for(int i=0;i<percentages.length;i++)percentages[i]=i+"%";
        edge.setShader(new LinearGradient(0,0,1,0,new int[]{0xffffffff,0x00ffffff,0x00ffffff,0xffffffff},new float[]{0,.17f,.83f,1},Shader.TileMode.CLAMP));
        edge.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_OUT));
        bolt.moveTo(-.55f,-.11f);bolt.lineTo(-.29f,-.075f);bolt.lineTo(-.20f,-.10f);
        bolt.lineTo(-.225f,-.058f);bolt.lineTo(.025f,-.02f);bolt.lineTo(.078f,-.048f);
        bolt.lineTo(.06f,-.009f);bolt.lineTo(.33f,.045f);bolt.lineTo(.46f,.032f);bolt.lineTo(.56f,.068f);
        fork.moveTo(-.20f,-.062f);fork.lineTo(-.13f,-.145f);fork.lineTo(-.064f,-.155f);fork.lineTo(-.033f,-.19f);
        fork.moveTo(.18f,.016f);fork.lineTo(.245f,.089f);fork.lineTo(.29f,.104f);
        Thread loader=new Thread(()->{
            String[] names={"opening-street.webp","mapo-room.jpg","dangsan-room.jpg"};
            Bitmap[] art=new Bitmap[names.length];
            for(int i=0;i<names.length;i++) art[i]=read(names[i]);
            Bitmap logo=read("opening-title.webp");
            a.host.ui.post(()->{System.arraycopy(art,0,rooms,0,art.length);mark=logo;});
        },"VenueEntranceArt");
        loader.start();
    }
    private Bitmap read(String name){
        try(InputStream in=a.host.getAssets().open(name)){
            BitmapFactory.Options options=new BitmapFactory.Options();options.inSampleSize=2;
            Bitmap result=BitmapFactory.decodeStream(in,null,options);
            if(result!=null)result.prepareToDraw();
            return result;
        }catch(IOException ignored){return null;}
    }
    boolean active(){return venue>=0;}
    private void loadPoses(int selected){
        int ticket=++loadId;
        decoded=0;java.util.Arrays.fill(pages,null);
        Thread loader=new Thread(()->{
            for(int i=0;i<pages.length&&ticket==loadId;i++){
                final int index=i;
                Bitmap image=null;
                try(InputStream in=a.host.getAssets().open("arrival-"+ART[selected]+"-"+i+".webp")){
                    BitmapFactory.Options options=new BitmapFactory.Options();
                    options.inPreferredConfig=Bitmap.Config.RGB_565;
                    image=BitmapFactory.decodeStream(in,null,options);
                    if(image!=null)image.prepareToDraw();
                }catch(IOException ignored){}
                final Bitmap ready=image;
                a.host.ui.post(()->{
                    if(ticket!=loadId)return;
                    pages[index]=ready;decoded=index+1;a.postInvalidateOnAnimation();
                });
            }
        },"VenueGestureFrames");
        loader.start();
    }
    private void releaseFrames(){
        loadId++;java.util.Arrays.fill(pages,null);venue=-1;pressed=false;
    }
    void start(int selected){
        if(active())return;
        venue=selected;time=0;last=SystemClock.uptimeMillis();opened=false;pressed=false;
        reduced=!ValueAnimator.areAnimatorsEnabled();a.held="";a.hits.clear();a.invalidate();
        if(reduced)decoded=pages.length;else loadPoses(selected);
    }
    void update(long now){
        if(!active())return;
        if(a.host.active){
            long next=time+Math.min(120,Math.max(0,now-last));
            if(!reduced&&decoded<pages.length){
                long readyThrough=190L+Math.max(0,decoded*12-1)*3250L/56;
                next=Math.min(next,readyThrough);
            }
            time=next;
        }
        last=now;
        if(time>=CROSSOVER&&!opened)open();
        if(time>=END)releaseFrames();
    }
    private void open(){
        opened=true;a.openVenue(venue);
    }
    boolean back(){
        if(!active())return false;
        releaseFrames();a.invalidate();return true;
    }
    boolean touch(MotionEvent e){
        if(!active())return false;
        boolean skip=e.getX()>a.getWidth()*.75f&&e.getY()>a.getHeight()*.77f;
        if(e.getActionMasked()==MotionEvent.ACTION_DOWN)pressed=skip;
        if(e.getActionMasked()==MotionEvent.ACTION_UP&&pressed&&skip&&time>1400&&!opened){
            time=CROSSOVER;open();
            loadId++;decoded=pages.length;
        }
        if(e.getActionMasked()==MotionEvent.ACTION_UP||e.getActionMasked()==MotionEvent.ACTION_CANCEL)pressed=false;
        return true;
    }
    private static float ease(float v){
        float t=Math.max(0,Math.min(1,v));return t*t*(3-2*t);
    }
    private static float mix(float s,float e,float t){return s+(e-s)*t;}
    void draw(Canvas c,int w,int h){
        if(!active())return;
        float scale=Math.min(w/1080f,h/520f);
        float W=w/scale,H=h/scale,L=(1080-W)*.5f,T=(520-H)*.5f;
        float expanded=reduced?1:ease(time/1280f),start=L+venue*W/3,span=W/3;
        float l=mix(start,L,expanded),r=mix(start+span,L+W,expanded),middle=mix(start+span/2,L+W*.285f,expanded);
        c.save();c.scale(scale,scale);c.translate(-L,-T);
        int layer=-1;
        if(opened)layer=c.saveLayerAlpha(L,T,L+W,T+H,Math.round(255*(1-ease((time-CROSSOVER)/(float)(END-CROSSOVER)))));
        c.save();c.clipRect(l,T,r,T+H);
        Bitmap room=rooms[venue];
        if(room!=null)a.cover(room,L,T,L+W,T+H);else c.drawColor(0xff22201c);
        if(expanded>0){
            c.save();c.translate(L,T);c.scale(W,H);
            ink.setColor(-1);ink.setShader(titleShadow);ink.setAlpha((int)(255*expanded));
            c.drawRect(0,0,1,1,ink);ink.setShader(null);c.restore();
        }
        int frame=Math.max(0,Math.min(56,(int)((time-190)*56/3250)));
        menu.arrivingPortrait(venue,middle,1+.145f*expanded,pages[frame/12],frame,expanded,edge);
        float show=reduced?1:ease((time-1950)/730f),cx=L+W*.691f,cy=T+H*.47f;
        if(show>0){
            float tw=W*(.445f+.031f*show),th=mark==null?tw*.258f:tw*mark.getHeight()/mark.getWidth();
            float flash=(float)Math.sin(Math.PI*ease((time-1940)/485f))+.55f*(float)Math.sin(Math.PI*ease((time-2490)/275f));
            if(!reduced&&flash>.01f){
                c.save();c.translate(cx,cy);c.scale(tw,tw);ink.setShader(null);ink.setStyle(Paint.Style.STROKE);
                ink.setStrokeJoin(Paint.Join.ROUND);ink.setStrokeCap(Paint.Cap.ROUND);
                for(int pass=0;pass<3;pass++){
                    ink.setColor(pass==2?0xfffff7d4:0xff72c5cb);ink.setAlpha((int)(Math.min(1,flash)*(pass==2?230:pass==1?95:48)));
                    ink.setStrokeWidth(pass==2?.0018f:pass==1?.005f:.012f);c.drawPath(bolt,ink);c.drawPath(fork,ink);
                }
                ink.setStyle(Paint.Style.FILL);c.restore();
            }
            if(mark!=null){
                dst.set(cx-tw/2,cy-th/2+(1-show)*19,cx+tw/2,cy+th/2+(1-show)*19);
                ink.setColor(-1);ink.setAlpha(Math.round(255*show));c.drawBitmap(mark,null,dst,ink);
            }else a.text("망원동 홀덤",cx,cy,48,(Math.round(255*show)<<24)|0xffdfb5,a.title,Paint.Align.CENTER);
            float caption=reduced?1:ease((time-2480)/620f);
            a.text(a.pubs[venue],cx,cy+th/2+34+(1-caption)*6,21,(Math.round(250*caption)<<24)|0xe9d7b5,a.title,Paint.Align.CENTER);
        }
        drawGauge(c,L,T,W,H);
        c.restore();if(layer>=0)c.restoreToCount(layer);c.restore();
    }

    private void drawGauge(Canvas c,float l,float t,float w,float h){
        float appear=ease((time-280)/500f);
        if(appear<=0)return;
        float x=l+w*.08f,end=l+w*.92f,y=t+h*.913f;
        int percent=Math.min(100,Math.min((int)(time*100/CROSSOVER),65+decoded*7));
        float point=mix(x,end,percent/100f);
        a.box(x-1,y-1,end+1,y+5,3,(Math.round(210*appear)<<24)|0x180f09);
        a.box(x,y,end,y+4,2,(Math.round(245*appear)<<24)|0x6f624a);
        if(point>x)a.box(x,y,point,y+4,2,(Math.round(250*appear)<<24)|0xe7bc72);
        a.text("입장 준비",x,y-10,14,(Math.round(255*appear)<<24)|0xf7e7cd,a.bold,Paint.Align.LEFT);
        a.text(percentages[percent],x+68,y-10,13,(Math.round(255*appear)<<24)|0xe6c994,a.nums,Paint.Align.LEFT);
        if(time>1400&&!opened)a.text("건너뛰기  ›",end,y-10,14,0xfff6ebdc,a.bold,Paint.Align.RIGHT);
    }
}
