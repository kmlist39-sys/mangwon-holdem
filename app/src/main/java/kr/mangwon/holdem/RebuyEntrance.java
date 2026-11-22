package kr.mangwon.holdem;

import android.graphics.Bitmap;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayDeque;
import java.util.Arrays;

/** A public tournament re-entry receipt and the returning player's seat cue. */
final class RebuyEntrance {
    private final StageArt stage;
    private final Arena a;
    private final Bitmap ticket;
    private final ArrayDeque<JSONObject> queue=new ArrayDeque<>();
    private final int[] pendingHand=new int[CastArt.COUNT];
    private final long[] seatAt=new long[CastArt.COUNT];
    private long shownAt;
    private JSONObject shown;
    RebuyEntrance(StageArt stage){this.stage=stage;a=stage.a;ticket=a.image("rebuy-ticket.png");Arrays.fill(pendingHand,-1);}
    void observe(JSONObject old,JSONObject g,JSONArray events,long now){
        if(old==null||g==null||!old.optString("sessionId").equals(g.optString("sessionId"))){
            queue.clear();shown=null;Arrays.fill(pendingHand,-1);Arrays.fill(seatAt,0);
        }
        if(old!=null&&g!=null&&g.optBoolean("cash")&&g.optString("sessionId").equals(old.optString("sessionId"))){
            JSONObject previous=old.optJSONObject("cashEvent");
            long last=previous==null?0:previous.optLong("seq");
            JSONArray cash=g.optJSONArray("cashEvents");
            for(int i=0;cash!=null&&i<cash.length();i++){
                JSONObject event=cash.optJSONObject(i);
                if(event!=null&&event.optLong("seq")>last){
                    queue.addLast(event);
                    int id=event.optInt("id",-1);
                    if(id>=0&&id<seatAt.length)seatAt[id]=now;
                }
            }
        }
        if(events!=null)for(int i=0;i<events.length();i++){
            JSONObject event=events.optJSONObject(i);
            if(event==null)continue;
            queue.addLast(event);
            int id=event.optInt("id",-1);
            if(g!=null&&id>=0&&id<pendingHand.length&&"rebought".equals(event.optString("kind")))
                pendingHand[id]=g.optInt("hand");
        }
        if(old==null||g==null)return;
        if(old.optInt("hand")!=g.optInt("hand")){
            JSONArray seats=g.optJSONArray("p");
            for(int i=0;seats!=null&&i<seats.length();i++){
                int id=seats.optJSONObject(i).optInt("id",-1);
                if(id>=0&&id<pendingHand.length&&pendingHand[id]>=0&&g.optInt("hand")-pendingHand[id]<=2){
                    seatAt[id]=now;pendingHand[id]=-1;
                }
            }
        }
    }
    float entering(int id){
        if(seatAt[id]<=0)return 0;
        return 1-TitleSequence.out(TitleSequence.range(a.now-seatAt[id],0,790));
    }
    void seat(int id,float x,float y){
        long age=a.now-seatAt[id];if(seatAt[id]<=0||age<0||age>1160)return;
        float approach=TitleSequence.out(TitleSequence.range(age,110,630)),fade=1-TitleSequence.smooth(TitleSequence.range(age,765,395));
        for(int k=0;k<4;k++){
            float cx=x+(x<540?-1:1)*(31+(1-approach)*(28+k*4));
            float cy=y-19-(1-approach)*(19+k*2)-k*2.1f;
            stage.chips.disc(cx,cy,16,k,(int)(220*fade));
        }
    }
    boolean ticket(){
        if(shown!=null&&a.now-shownAt>=2820)shown=null;
        if(shown==null){
            JSONObject next=queue.peekFirst();if(next==null)return false;
            shown=queue.removeFirst();shownAt=a.now;
        }
        long age=a.now-shownAt;
        float enter=TitleSequence.out(TitleSequence.range(age,0,355)),out=TitleSequence.smooth(TitleSequence.range(age,2450,370));
        float x=13-(1-enter)*70-out*20,y=372+(1-enter)*9+out*13;
        a.c.save();a.c.clipRect(13,372,275,436);
        a.c.saveLayerAlpha(9,368,279,440,(int)(255*(1-out)));
        a.c.rotate(-4*(1-enter),144,412);
        if(ticket!=null)a.photo(ticket,x,y,x+262,y+64);else stage.plate(x,y,x+262,y+64,0);
        float center=x+133;
        boolean rebought="rebought".equals(shown.optString("kind"));
        if(shown.has("session")){
            String who=shown.optInt("id")==-1?(a.en?"You":"나"):a.tr(shown.optString("name"));
            String action=rebought?(a.en?"Rebuy":"리바인"):(a.en?"Add-on":"애드온");
            stage.bet.fitted(who+"  ·  "+action,center,y+25,178,15,a.paper,android.graphics.Paint.Align.CENTER);
            stage.bet.fitted("+"+a.fmt(shown.optLong("amount"))+" P",center,y+48,178,18,a.gold,android.graphics.Paint.Align.CENTER);
            a.c.restore();a.c.restore();return true;
        }
        String info=rebought?"리바인 "+shown.optInt("count",1)+"회 · +"+a.fmt(shown.optInt("stack",50000))+"칩":"핸드 "+shown.optInt("hand");
        stage.bet.fitted(shown.optInt("table")+"번 테이블  ·  "+info,center,y+24,178,12,a.gold,android.graphics.Paint.Align.CENTER);
        stage.bet.fitted(a.en?a.tr(shown.optString("name"))+(rebought?" has rebought.":" is out."):
                shown.optString("name")+" 님, "+(rebought?"리바인하셨습니다.":"탈락하셨습니다."),center,y+47,179,16,a.paper,android.graphics.Paint.Align.CENTER);
        a.c.restore();a.c.restore();
        return true;
    }
}
