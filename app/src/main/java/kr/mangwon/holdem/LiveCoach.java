package kr.mangwon.holdem;

import android.graphics.Paint;
import android.util.Log;
import org.json.JSONObject;

/** A turn-specific guide on the felt. Looking at it never issues a betting action. */
final class LiveCoach {
    private final Arena a;
    private String session="";
    private int hand=-1,street,seq,ticket;
    private long pot,call;
    private JSONObject advice;
    private boolean expanded;
    private final CoachDetails details;

    LiveCoach(Arena arena){a=arena;details=new CoachDetails(a);}

    private boolean eligible(){
        return a.host.studyBilling.unlocked()&&a.g!=null&&!a.g.optBoolean("heroHoleHidden")&&a.g.optBoolean("own")
            &&a.screen.equals("table")&&!a.deal.running()&&a.g.optString("phase").equals("bet");
    }

    void update(){
        if(!eligible()){
            if(hand>=0){ticket++;hand=-1;advice=null;expanded=false;}
            return;
        }
        JSONObject g=a.g,action=g.optJSONObject("announcement");
        int nextSeq=action==null?0:action.optInt("seq");
        if(hand==g.optInt("hand")&&street==g.optInt("street")&&pot==g.optLong("pot")
                &&call==g.optLong("call")&&seq==nextSeq&&session.equals(g.optString("sessionId")))return;
        session=g.optString("sessionId");hand=g.optInt("hand");street=g.optInt("street");
        seq=nextSeq;pot=g.optLong("pot");call=g.optLong("call");advice=null;expanded=false;
        final int request=++ticket;
        String query=GameActivity.args("op","liveGuide","session",session,"hand",hand,"street",street,"pot",pot,"call",call).toString();
        a.host.worker.post(()->{
            try{
                JSONObject result=new JSONObject(GameActivity.call(query));
                a.host.ui.post(()->{
                    if(request==ticket&&eligible()&&result.has("passive")){
                        advice=result;a.invalidate();
                    }
                });
            }catch(Exception e){Log.w("Mangwon","Live study calculation",e);}
        });
    }

    void toggle(){if(eligible()&&advice!=null){expanded=!expanded;details.first();a.invalidate();}}
    boolean reading(){return expanded&&eligible()&&a.panel.isEmpty();}
    boolean select(String action){return reading()&&details.select(action);}
    boolean dismiss(){if(!expanded)return false;expanded=false;a.invalidate();return true;}

    private void text(String text,float x,float y,float size,int color,Paint.Align align){
        a.text(text,x,y,size,color,a.bold,align);
    }
    private void frequency(String label,int percent,int y,int color){
        text(label,690,y,12,a.paper,Paint.Align.LEFT);
        text(percent+"%",819,y,14,color,Paint.Align.RIGHT);
        a.box(733,y-7,777,y-3,2,0xff334539);
        if(percent>0)a.box(733,y-7,733+44*percent/100f,y-3,2,color);
    }
    void draw(){
        update();
        if(advice==null||!eligible()||!a.panel.isEmpty())return;
        boolean faced=call>0,raising=advice.optBoolean("canRaise");
        String passive=faced?"콜":"체크";
        String aggressive=advice.optBoolean("opening")?"베팅":"레이즈";
        a.box(682,252,827,357,6,0xf0192a23);
        a.stroke(682,252,827,357,6,0xff8c7958,1);
        a.stage.plate(681,247,828,271,0);
        text("GTO 가이드",694,263,13,a.gold,Paint.Align.LEFT);
        text(expanded?"닫기":"자세히 ›",815,263,11,a.paper,Paint.Align.RIGHT);
        a.stage.bet.fitted("팟 "+a.fmt(pot)+(faced?" · 콜 "+a.fmt(call):""),754,283,131,11,a.paper,Paint.Align.CENTER);
        int y=303;
        if(faced){frequency("폴드",advice.optInt("fold"),y,0xffd49a8b);y+=19;}
        frequency(passive,advice.optInt("passive"),y,0xff92c7ad);y+=19;
        if(raising)frequency(aggressive,advice.optInt("raise"),y,0xffe5bd79);
        a.hit(680,246,829,358,"liveCoach");
        if(expanded)details.draw(advice,pot,call);
    }

}
