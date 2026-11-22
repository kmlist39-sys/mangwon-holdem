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

    LiveCoach(Arena arena){a=arena;}

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

    void toggle(){if(eligible()&&advice!=null){expanded=!expanded;a.invalidate();}}
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
        if(expanded)detail(passive,aggressive);
    }

    private void detail(String passive,String aggressive){
        a.box(264,132,672,380,9,0xfc172a22);a.stroke(264,132,672,380,9,0xffa38b62,1.4f);
        a.hit(264,132,672,380,"coachPanel");
        a.stage.bet.fitted("현재 핸드 · "+advice.optString("position"),281,158,305,19,a.paper,Paint.Align.LEFT);
        a.brass("닫기",595,139,66,"liveCoach");
        a.line(281,170,651,170,0xff74694f,1);
        a.txt("현재 팟 "+a.fmt(pot)+"   ·   "+(call>0?"콜 비용 "+a.fmt(call):"체크 가능"),282,191,15,a.paper);
        a.txt("콜 필요 지분 "+advice.optDouble("odds")+"%   ·   추정 지분 "+advice.optInt("equity")+"%",282,212,14,a.gold);
        int y=238;
        if(call>0){option("폴드","추가 투입 0",advice.optInt("fold"),y);y+=21;}
        option(advice.optBoolean("allInCall")?"올인 콜":passive,"추가 투입 "+a.fmt(call),advice.optInt("passive"),y);
        if(advice.optBoolean("canRaise"))option(advice.optBoolean("allInRaise")?"올인":aggressive,
            "총 "+a.fmt(advice.optLong("raiseTo")),advice.optInt("raise"),y+21);
        a.line(281,290,651,290,0xff645d47,1);
        a.stage.bet.fitted(advice.optString("title"),282,309,367,15,a.gold,Paint.Align.LEFT);
        a.wrapped(advice.optString("reason"),282,a.en?322:328,366,a.en?11:12,a.paper,a.en?3:2);
        String note=advice.optLong("eligible")<pot&&call>0?"참여 가능 팟 "+a.fmt(advice.optLong("eligible"))+" 기준":"공개 액션과 상대 범위를 바탕으로 계산";
        a.stage.bet.fitted("학습용 추정 빈도 · "+note,282,369,365,11,a.muted,Paint.Align.LEFT);
    }
    private void option(String name,String amount,int percent,int y){
        text(name,282,y,14,a.paper,Paint.Align.LEFT);a.txt(amount,380,y,13,a.muted);
        text(percent+"%",650,y,15,a.gold,Paint.Align.RIGHT);
    }
}
