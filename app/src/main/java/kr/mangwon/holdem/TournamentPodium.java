package kr.mangwon.holdem;

import android.animation.ValueAnimator;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.SystemClock;
import org.json.JSONObject;
import java.util.Random;

/** Presents the rules engine's settled prize. This view never credits the wallet. */
final class TournamentPodium {
    private static final int[] METALS={0xffefc378,0xffcbdde4,0xffdfa17e};
    private static final int INK=0xff111d19, IVORY=0xfff4e9d5, MUTED=0xffbec5b8;
    private final Arena a;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
    private final Rect source=new Rect();
    private final RectF bounds=new RectF();
    private final RadialGradient[] halo=new RadialGradient[3];
    private final float[][] ribbon=new float[66][6];
    private Bitmap trophies,room;
    private String lastSession="";
    private int rank,pub;
    private boolean moving;
    private long began;
    private JSONObject result;

    TournamentPodium(Arena arena){
        a=arena;
        for(int i=0;i<3;i++)
            halo[i]=new RadialGradient(0,0,1,new int[]{METALS[i],METALS[i]&0x38ffffff,METALS[i]&0xffffff},
                    new float[]{0,.5f,1},Shader.TileMode.CLAMP);
        Random random=new Random(25761);
        for(float[] strip:ribbon)for(int i=0;i<strip.length;i++)strip[i]=random.nextFloat();
    }

    void observe(JSONObject snapshot){
        JSONObject game=snapshot.optJSONObject("g");
        if(game!=null){
            result=null;
            if(trophies==null&&!game.optBoolean("cash")&&game.optInt("left",99)<=4)
                trophies=a.honors.trophySheet();
            return;
        }
        JSONObject outcome=snapshot.optJSONObject("tournamentResult");
        boolean justFinished=snapshot.optBoolean("tournamentFinished");
        if(outcome==null||(!justFinished&&!a.panel.equals("result"))){result=null;return;}
        String type=outcome.optString("type"),session=outcome.optString("sessionId");
        int place=outcome.optInt("rank");
        int venue=outcome.optInt("pub",-1);
        if(session.isEmpty()||venue<0||venue>1||place<1||place>3||outcome.optLong("payout")<=0
                ||(!type.equals("winner")&&!type.equals("placed")))return;
        result=outcome;
        if(lastSession.equals(session))return;
        lastSession=session;
        rank=place;
        pub=venue;
        moving=justFinished&&ValueAnimator.areAnimatorsEnabled();
        began=SystemClock.uptimeMillis();
        if(trophies==null)trophies=a.honors.trophySheet();
        room=a.stage.place.backdrop(VenueScene.roomFor(pub,outcome.optInt("initial",18)));
    }

    boolean showing(){return result!=null&&a.panel.equals("result");}
    boolean animating(long now){return moving&&now-began<4100;}

    void backdrop(Canvas canvas,int w,int h){
        canvas.drawColor(INK);
        a.cover(room,0,0,w,h);
        canvas.drawARGB(219,9,19,17);
        a.grad(0,0,w,h,0x030b100e,0xdb101813,true);
    }

    private static float ease(float t){
        t=Math.min(1,Math.max(0,t));
        return t*t*(3-2*t);
    }

    void draw(){
        a.hits.clear();
        float t=moving?Math.max(0,(a.now-began)/1000f):5;
        int tint=METALS[rank-1];
        float lift=ease(t/.78f);
        Canvas c=a.c;
        c.save();c.translate(321,253);c.scale(325,239);
        paint.setColor(Color.WHITE);paint.setAlpha((int)(42+25*lift));
        paint.setShader(halo[rank-1]);c.drawCircle(0,0,1,paint);
        paint.setShader(null);c.restore();
        if(moving&&t<3.1f){
            burst(134,142,t-.32f,tint,rank==1?16:11,1);
            if(rank!=3)burst(469,99,t-.72f,tint,rank==1?18:12,.84f);
            if(rank==1)burst(443,234,t-1.14f,0xffffdfad,12,.60f);
        }
        if(trophies!=null){
            int w=trophies.getWidth(),h=trophies.getHeight();
            source.set((rank-1)*w/3,0,rank*w/3,h);
            float ratio=source.width()/(float)h;
            float height=Math.min(372,394/ratio)*(.94f+.06f*lift),width=height*ratio;
            float bottom=424+(1-lift)*23;
            bounds.set(319-width/2,bottom-height,319+width/2,bottom);
            paint.setColor(Color.WHITE);paint.setAlpha((int)(255*ease(t/.46f)));
            c.drawBitmap(trophies,source,bounds,paint);
        }
        if(moving&&t<3.85f)ribbons(t,tint);
        float reveal=ease((t-.18f)/.6f);
        c.save();c.translate(0,7*(1-reveal));
        int cream=alpha(IVORY,reveal),accent=alpha(tint,reveal),muted=alpha(MUTED,reveal);
        a.text(a.pubs[pub]+"   /   데일리 토너먼트",550,130,17,muted,a.bold,Paint.Align.LEFT);
        String heading=result.optBoolean("tied")?"공동 "+rank+"위":rank==1?"1위 우승":rank==2?"2위 준우승":"3위 입상";
        a.text(heading,546,219,68,cream,a.title,Paint.Align.LEFT);
        a.line(550,245,943,245,alpha(tint,.56f*reveal),1);
        JSONObject pass=result.optJSONObject("ticketAwarded");
        a.txt("획득 상금",552,pass==null?279:267,17,muted);
        a.text(a.fmt(result.optLong("payout"))+" P",548,pass==null?330:308,pass==null?45:40,accent,a.nums,Paint.Align.LEFT);
        a.txt("총상금  "+a.fmt(result.optLong("pool"))+" P   ·   참가 "+result.optInt("initial")+"명",552,pass==null?366:334,16,muted);
        if(pass!=null&&reveal>.8f){
            a.honors.voucher(pass,547,342,296,94,a.en?"Bonus entry pass":"우승 보너스 · 바이인 1회권");
        }
        c.restore();
        float buttons=pass==null?405:446;
        a.key("로비로",547,buttons,176,49,"close",1);
        a.key(a.en?"My awards":"나의 수상 이력",735,buttons,212,49,"honorsOpen",0);
    }

    private static int alpha(int color,float opacity){
        return (color&0xffffff)|(Math.round(Math.min(1,Math.max(0,opacity))*255)<<24);
    }

    private void burst(float x,float y,float t,int color,int count,float size){
        if(t<0||t>=1)return;
        float r=(12+77*(1-(1-t)*(1-t)))*size;
        float tail=(1-t)*18*size,fall=18*t*t;
        paint.setColor(alpha(color,.7f*(1-t)));paint.setStrokeWidth(1.4f);
        for(int i=0;i<count;i++){
            double angle=2*Math.PI*i/count+.26;
            float dx=(float)Math.cos(angle),dy=(float)Math.sin(angle);
            a.c.drawLine(x+dx*(r-tail),y+dy*(r-tail)+fall,x+dx*r,y+dy*r+fall,paint);
        }
    }

    private void ribbons(float t,int tint){
        int count=rank==1?66:rank==2?43:28;
        for(int i=0;i<count;i++){
            float[] seed=ribbon[i];
            float age=t-.18f-seed[0]*1.04f;
            if(age<0)continue;
            float y=-15+age*(72+99*seed[2])+43*age*age;
            if(y>508)continue;
            float x=48+463*seed[1]+(float)Math.sin(age*3+seed[3]*6.28f)*(10+16*seed[4]);
            float fade=1-ease((age-1.68f)/1.12f);
            paint.setColor(alpha(i%4==0?IVORY:tint,fade*(.4f+seed[2]*.52f)));
            a.c.save();a.c.translate(x,y);a.c.rotate(seed[3]*270+age*(62+100*seed[4]));
            float half=.5f+Math.abs((float)Math.sin(age*5+seed[5]*6))*(1.3f+seed[4]*1.2f);
            a.c.drawRect(-half,-3.1f,half,3.1f,paint);a.c.restore();
        }
    }
}
