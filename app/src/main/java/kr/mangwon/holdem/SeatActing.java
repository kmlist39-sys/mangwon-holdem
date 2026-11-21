package kr.mangwon.holdem;

import android.animation.ValueAnimator;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import java.util.Arrays;
import org.json.JSONArray;
import org.json.JSONObject;

/** Performance of the seated cast. Only visible decisions drive an opponent's posture. */
final class SeatActing {
    private static final int REST=0, CALL=1, CHECK=2, RAISE=3;
    // Screen-facing direction in the source atlas, before seating at either end of the rail.
    private static final int[] FACING={
         1,1,1,1,1,1,1,1,1,1, 1,1,1,1,1,1,1,1,1,1,
         1,-1,1,1,1,1,1,1,1,1, -1,1,1,-1,1,1,1,-1,1,1,
         1,1,1,1,1,1,1,1,1,-1
    };
    private final Arena a;
    private Bitmap regrets,thoughts;
    private volatile boolean expressionsReady;
    private boolean loading;
    private final Portrait[] cast=new Portrait[50];
    private final boolean[] seated=new boolean[50];
    private final int[] action=new int[50];
    private final long[] acted=new long[50];
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
    private static final PorterDuffXfermode ADD=new PorterDuffXfermode(PorterDuff.Mode.ADD);
    private int lastSeq=-1;
    private long lastMotionCheck;
    private boolean motion=true;

    SeatActing(Arena arena){
        a=arena;
    }

    private void prepareExpressions(){
        if(loading)return;
        loading=true;
        Thread thread=new Thread(()->{
            regrets=validSheet(a.image("seat-fold.webp"));
            thoughts=validSheet(a.image("seat-think.webp"));
            expressionsReady=true;
            a.postInvalidate();
        },"SeatedCastArt");
        thread.setPriority(Thread.MIN_PRIORITY);thread.start();
    }

    private static Bitmap validSheet(Bitmap image){
        if(image==null||image.getWidth()<500||image.getHeight()<250)return null;
        float aspect=(float)image.getWidth()/image.getHeight();
        return Math.abs(aspect-2)<.12f?image:null;
    }

    void clear(){
        Arrays.fill(cast,null);Arrays.fill(acted,0);lastSeq=-1;
    }

    void observe(JSONObject game,long now,boolean newHand){
        JSONArray players=game.optJSONArray("p");
        if(players==null)return;
        prepareExpressions();
        if(newHand){
            Arrays.fill(acted,0);Arrays.fill(action,REST);Arrays.fill(seated,false);
            for(int i=0;i<players.length();i++){
                int id=players.optJSONObject(i).optInt("id",-1);
                if(id>=0&&id<cast.length)seated[id]=true;
            }
            for(int id=0;id<cast.length;id++){
                if(!seated[id])cast[id]=null;
                else if(cast[id]==null&&a.busts!=null){
                    int cell=a.artMap[id];
                    cast[id]=new Portrait(cell,cut(a.busts,cell));
                }
                if(cast[id]!=null)cast[id].resetFocus();
            }
        }
        JSONObject event=game.optJSONObject("announcement");
        int seq=event==null?-1:event.optInt("seq",-1);
        if(!newHand&&event!=null&&seq!=lastSeq){
            int id=event.optInt("id",-1);
            if(id>=0&&id<cast.length){
                String kind=event.optString("kind");
                if(!"fold".equals(kind)){
                    action[id]="call".equals(kind)?CALL:"check".equals(kind)?CHECK:RAISE;
                    acted[id]=now;
                }
            }
        }
        lastSeq=seq;
    }

    private static Bitmap cut(Bitmap sheet,int cell){
        if(sheet==null)return null;
        int col=cell%10,row=cell/10;
        int x=col*sheet.getWidth()/10,y=row*sheet.getHeight()/5;
        return Bitmap.createBitmap(sheet,x,y,(col+1)*sheet.getWidth()/10-x,(row+1)*sheet.getHeight()/5-y);
    }

    private static float ease(float value,float start,float end){
        float u=Math.max(0,Math.min(1,(value-start)/(end-start)));return u*u*(3-2*u);
    }

    static float foldShade(long age){
        // The player has time to read the disappointed look before the seat settles into shadow.
        return .14f*ease(age,35,230)+.86f*ease(age,670,1100);
    }

    void draw(JSONObject player,float x,float y,float size,Path mask,boolean active){
        int id=player.optInt("id",-1);
        if(id<0||id>=cast.length)return;
        Portrait person=cast[id];
        if(person==null){a.person(id,x-size/2,y-size,size,player.optInt("won")>0);return;}
        if(a.now-lastMotionCheck>=1000||lastMotionCheck==0){
            motion=ValueAnimator.areAnimatorsEnabled();lastMotionCheck=a.now;
        }
        boolean folded=player.optBoolean("fold");
        long foldTime=a.foldStarted[id+1];
        float foldAge=folded&&foldTime>0?(float)(a.now-foldTime):5000;
        float since=acted[id]>0?(float)(a.now-acted[id]):5000;
        if(expressionsReady){
            if(folded)person.prepareFold(regrets);
            else if(active)person.prepareThought(thoughts);
        }
        int kind=action[id];
        boolean paying=!folded&&(kind==CALL||kind==RAISE)&&since<1050;
        float think=person.focus(active&&!folded&&expressionsReady,paying,a.now,motion);
        float seatDepth=Arena.frontWing(x)?1:Arena.endSeat(x)?.74f:Math.min(.46f,Math.abs(x-540)/680);
        int inward=x<540?1:-1;
        boolean flipped=seatDepth>.55f&&FACING[person.cell]!=inward;
        int localInward=flipped?-inward:inward;
        float cycle=3340+id*383%1975;
        double wave=(a.now-a.born+id*347)%((long)cycle)*(2*Math.PI/cycle);
        float breath=motion&&!folded?(float)Math.sin(wave):0;
        float glance=motion?(float)Math.sin(wave+.75)*think:0;
        float regret=folded?(motion?ease(foldAge,25,370):1):0;
        float lookBack=folded&&motion?(float)Math.sin(2*Math.PI*ease(foldAge,280,990))*
            (1-ease(foldAge,700,1030)):0;
        float reach=0,lift=0,nod=0;
        if(paying&&motion){
            boolean raise=kind==RAISE;
            float extend=ease(since,12,raise?385:320);
            float release=ease(since,raise?390:345,raise?670:535);
            float returnToSeat=1-ease(since,635,1050);
            float gather=ease(since,0,70)*(1-ease(since,70,160));
            reach=(extend*(raise?.071f:.054f)+release*.006f)*returnToSeat-gather*.006f;
            lift=((float)Math.sin(Math.PI*extend)*-.012f+release*.005f)*returnToSeat;
            nod=(extend*.64f+release*.10f)*returnToSeat;
        }else if(!folded&&motion&&kind==CHECK&&since<970){
            float tap=ease(since,270,336)-ease(since,341,415)+ease(since,469,526)-ease(since,535,615);
            nod=.34f*ease(since,0,250)*(1-ease(since,590,970));
            lift=-nod*.018f+tap*.025f;
        }
        float deadline=think*ease(a.now-person.focusBegan,580,1500);
        boolean announced=player.optBoolean("spot")||player.optInt("won")>0;
        float lookUp=announced&&!folded?1:0;
        float headX=localInward*(seatDepth*.040f+think*.012f+deadline*.008f+
            glance*.010f+nod*.015f+lookUp*.009f)+lookBack*(id%2==0?.012f:-.012f);
        float headY=breath*.0042f+think*.014f+glance*.006f+
            regret*(.032f+.005f*(id%3))+nod*.021f-lookUp*.016f;
        float shoulder=breath*.006f+regret*.020f+nod*.014f-lookUp*.008f;
        float tableX=540-x,tableY=(Arena.frontWing(x)?295:337)-y;
        float distance=Math.max(1,(float)Math.hypot(tableX,tableY));
        float wristX=(flipped?-tableX:tableX)*reach/distance+localInward*glance*.010f;
        float wristY=tableY*reach*.68f/distance+lift;
        float expression=regret*(1-ease(foldAge,950,1400)*.27f);
        person.pose(headX,headY,shoulder,wristX,wristY,localInward,seatDepth,expression,
            motion?glance*.6f+think*.4f:0);

        Canvas c=a.c;c.save();c.clipPath(mask);
        float enter=a.stage.rebuys.entering(id);
        if(enter>0)c.translate(-inward*11*enter,16*enter);
        float width=size*(1-.073f*seatDepth);
        c.translate(x+(flipped?width:-width)*.5f,y-size);
        c.scale(flipped?-width:width,size+5);
        paint.setColorFilter(folded?Arena.FOLD_TONES[Math.round(16*a.foldFade(id))]:null);
        float foldPose=person.fold!=null&&folded?(motion?ease(foldAge,80,245):1):0;
        person.render(c,paint,person.think==null?0:think,foldPose);
        c.restore();
    }

    /** The face, shoulders and wrist have separate influences; the seated lower edge is fixed. */
    private static final class Portrait {
        static final int COLS=14,ROWS=15,N=(COLS+1)*(ROWS+1);
        static final float[] U=new float[N],V=new float[N];
        static {for(int y=0;y<=ROWS;y++)for(int x=0;x<=COLS;x++){
            int k=y*(COLS+1)+x;U[k]=x/(float)COLS;V[k]=y/(float)ROWS;
        }}
        final int cell;
        final Bitmap idle;
        Bitmap fold,think;
        private boolean foldChecked,thinkChecked;
        private static final int RELAX=0,ATTEND=1,HOLD=2;
        private int focusMode=RELAX;
        private float focusFrom,focusTo;
        private long focusChanged,focusBegan;
        private final float[] mesh=new float[N*2],heads=new float[N],shoulders=new float[N],
            wrists=new float[N],elbows=new float[N],brows=new float[N],mouths=new float[N],yaw=new float[N];

        Portrait(int frame,Bitmap neutral){
            cell=frame;idle=neutral;
            // Skin centroid anchors the rig to each haircut and face, including the older cast.
            int count=0;float tx=0,ty=0;
            for(int iy=3;iy<=11;iy++)for(int ix=4;ix<=16;ix++){
                float u=ix/20f,v=.14f+iy*.034f;
                int pixel=neutral.getPixel((int)(u*(neutral.getWidth()-1)),(int)(v*(neutral.getHeight()-1)));
                int r=(pixel>>>16)&255,g=(pixel>>>8)&255,b=pixel&255;
                if((pixel>>>24)>190&&r>128&&r>g*1.08f&&g>b*1.08f&&r-b>26){tx+=u;ty+=v;count++;}
            }
            float cx=count==0?.53f:Math.max(.36f,Math.min(.68f,tx/count));
            float cy=count==0?.40f:Math.max(.32f,Math.min(.46f,ty/count));
            for(int k=0;k<N;k++){
                float u=U[k],v=V[k],pinned=1-ease(v,.78f,1);
                heads[k]=bell(u,v,cx,cy,.39f,.42f)*pinned;
                shoulders[k]=bell(u,v,.50f,.70f,.57f,.28f)*pinned;
                wrists[k]=bell(u,v,.50f+FACING[cell]*.095f,.82f,.30f,.20f)*(1-ease(v,.90f,1));
                elbows[k]=bell(u,v,.50f-FACING[cell]*.14f,.77f,.26f,.18f)*(1-ease(v,.88f,1));
                brows[k]=bell(u,v,cx,cy-.047f,.19f,.12f)*pinned;
                mouths[k]=bell(u,v,cx,cy+.099f,.16f,.097f)*pinned;
                yaw[k]=(u-.5f)*shoulders[k];
            }
        }

        void resetFocus(){
            focusMode=RELAX;focusFrom=focusTo=0;focusChanged=focusBegan=0;
        }

        float focus(boolean attentive,boolean holding,long now,boolean animate){
            int next=attentive?ATTEND:holding?HOLD:RELAX;
            float t=animate?ease(now-focusChanged,0,focusMode==ATTEND?365:345):1;
            float value=focusFrom+(focusTo-focusFrom)*t;
            if(next!=focusMode){
                focusMode=next;focusFrom=value;focusChanged=now;
                // Keep the expression seen before the wager until the cuff has returned.
                focusTo=next==HOLD?value:next==ATTEND?1:0;
                if(next==ATTEND)focusBegan=now;
            }
            return animate?value:focusTo;
        }

        void prepareFold(Bitmap sheet){
            if(foldChecked)return;
            foldChecked=true;
            Bitmap b=cut(sheet,cell);
            fold=hasInk(b)?b:null;
        }

        void prepareThought(Bitmap sheet){
            if(thinkChecked)return;
            thinkChecked=true;
            Bitmap b=cut(sheet,cell);
            think=hasInk(b)?b:null;
        }

        private static boolean hasInk(Bitmap b){
            if(b==null)return false;
            for(int y=2;y<9;y+=2)for(int x=2;x<9;x+=2)
                if((b.getPixel(x*(b.getWidth()-1)/10,y*(b.getHeight()-1)/10)>>>24)>100)return true;
            return false;
        }

        private static float bell(float x,float y,float cx,float cy,float rx,float ry){
            float dx=(x-cx)/rx,dy=(y-cy)/ry,f=Math.max(0,1-dx*dx-dy*dy);
            return f*f*(3-2*f);
        }

        void pose(float headX,float headY,float shoulder,float wristX,float wristY,
                int inward,float depth,float regret,float gaze){
            for(int k=0;k<N;k++){
                float arm=wrists[k]+elbows[k]*.32f;
                mesh[k*2]=U[k]+heads[k]*headX+shoulders[k]*headX*.34f+
                    arm*wristX+brows[k]*inward*gaze*.004f-yaw[k]*depth*.16f;
                mesh[k*2+1]=V[k]+heads[k]*headY+shoulders[k]*shoulder+
                    arm*wristY+brows[k]*(regret*.009f+gaze*.005f)+mouths[k]*regret*.007f;
            }
        }

        void render(Canvas c,Paint paint,float attention,float folded){
            int foldAlpha=Math.round(255*folded);
            int thinkAlpha=Math.round((255-foldAlpha)*attention);
            int idleAlpha=255-foldAlpha-thinkAlpha;
            if(idleAlpha==255||thinkAlpha==255||foldAlpha==255){
                draw(c,idleAlpha==255?idle:thinkAlpha==255?think:fold,paint,255);
                return;
            }
            // Add premultiplied contributions inside one transparent seat-sized layer.
            // The two matching opaque cheeks stay opaque even midway through a change.
            c.saveLayer(-.13f,-.13f,1.13f,1.13f,null);
            paint.setXfermode(ADD);
            draw(c,idle,paint,idleAlpha);
            draw(c,think,paint,thinkAlpha);
            draw(c,fold,paint,foldAlpha);
            paint.setXfermode(null);
            c.restore();
        }

        void draw(Canvas c,Bitmap image,Paint paint,int alpha){
            if(alpha<=0)return;
            paint.setAlpha(alpha);c.drawBitmapMesh(image,COLS,ROWS,mesh,0,null,0,paint);
        }
    }
}
