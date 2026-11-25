package kr.mangwon.holdem;

import android.graphics.Paint;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONObject;

/** Four views of the same live decision. Chip amounts are table chips throughout. */
final class CoachDetails {
    private final Arena a;
    private static final int PANEL=0xff172b24, LINE=0xff4e5b49, GREEN=0xff91cab1;
    private static final int LEFT=149, RIGHT=932;
    private static final String[] KEYS={"mix","size","seats","commit","pots"};
    private static final String[] KO={"액션 빈도","레이즈 크기","좌석별 스택","팟 커밋","팟별 지분"};
    private static final String[] EN={"Frequencies","Bet sizing","Seat stacks","Commitment","Pot equities"};
    private final DecimalFormat decimal=new DecimalFormat("0.##",DecimalFormatSymbols.getInstance(Locale.US));
    private int page;

    CoachDetails(Arena arena){a=arena;}
    void first(){page=0;}
    boolean select(String action){
        if(!action.startsWith("coach:"))return false;
        for(int i=0;i<KEYS.length;i++){
            if(action.equals("coach:"+KEYS[i])){page=i;a.invalidate();return true;}
        }
        return false;
    }
    private String t(String ko,String en){return a.en?en:ko;}
    private String reason(JSONObject obj,String key){return obj.optString(key+(a.en?"En":"Ko"));}
    private void label(String text,float x,float y,float width,float size,int color){
        a.stage.bet.fitted(text,x,y,width,size,color,Paint.Align.LEFT);
    }
    private void center(String text,float x,float y,float width,float size,int color){
        a.stage.bet.fitted(text,x,y,width,size,color,Paint.Align.CENTER);
    }
    private String chips(long value){return a.fmt(value);}
    private String ratio(JSONObject obj,String key,String suffix){
        double value=obj.optDouble(key,Double.NaN);
        return Double.isFinite(value)?decimal.format(value)+suffix:"—";
    }
    private void paragraph(String text,int y,int lines){a.wrapped(text,LEFT,y,RIGHT-LEFT,14,a.paper,lines);}
    private void metrics(String[] headings,String[] values,int y){
        int w=(RIGHT-LEFT)/headings.length;
        for(int i=0;i<headings.length;i++){
            int x=LEFT+i*w;
            if(i>0)a.line(x-10,y-12,x-10,y+31,LINE,1);
            label(headings[i],x,y,w-17,12,a.muted);
            label(values[i],x,y+28,w-17,23,a.paper);
        }
    }
    void draw(JSONObject guide,long pot,long call){
        JSONObject detail=guide.optJSONObject("detail");
        if(detail==null)return;
        JSONObject size=detail.optJSONObject("sizing"),commit=detail.optJSONObject("commitment");
        JSONObject action=detail.optJSONObject("action");
        if(size==null||commit==null||action==null)return;
        a.box(125,47,955,435,11,PANEL);
        a.stroke(125,47,955,435,11,0xffa38b62,1.3f);
        a.hit(125,47,955,435,"coachPanel");
        label(t("실전 GTO 가이드","Live GTO guide"),LEFT,82,330,24,a.paper);
        String[] streets=a.en?new String[]{"Preflop","Flop","Turn","River"}:new String[]{"프리플랍","플랍","턴","리버"};
        int street=Math.max(0,Math.min(3,a.g.optInt("street")));
        label(streets[street]+"  ·  "+a.tr(guide.optString("position")),599,80,218,14,a.gold);
        a.brass(t("닫기","Close"),849,55,83,"liveCoach");
        JSONArray layers=detail.optJSONArray("potLayers");
        int tabs=layers!=null&&layers.length()>1?5:4;
        int tabWidth=(RIGHT-LEFT+1)/tabs;
        for(int i=0;i<tabs;i++){
            int x=LEFT+i*tabWidth;
            if(page==i)a.box(x,97,x+tabWidth-6,131,5,0xff354a3b);
            center((a.en?EN:KO)[i],x+(tabWidth-6)/2f,119,tabWidth-20,15,page==i?a.gold:a.muted);
            if(page==i)a.line(x+14,130,x+tabWidth-20,130,a.gold,2);
            a.hit(x,94,x+tabWidth-1,135,"coach:"+KEYS[i]);
        }
        a.line(LEFT,136,RIGHT,136,LINE,1);
        switch(page){
            case 0:mix(guide,detail,pot,call);break;
            case 1:sizing(guide,size);break;
            case 2:seats(detail,action,commit);break;
            case 3:commitment(commit);break;
            case 4:pots(layers);break;
            default:break;
        }
        a.line(LEFT,405,RIGHT,405,LINE,1);
        String basis=t("공개 정보 기준 학습용 추정","Study estimates from public information");
        if(detail.optBoolean("cash"))basis+=t(" · 레이크 전"," · Before rake");
        if(detail.optBoolean("double"))basis+=t(" · 보드당 ½"," · ½ per board");
        label(basis,LEFT,423,470,11,a.muted);
        a.stage.bet.fitted(t("상세 보기 중 내 결정 시간 일시정지","Your decision timer is paused"),RIGHT,423,300,11,a.muted,Paint.Align.RIGHT);
    }
    private void mix(JSONObject g,JSONObject detail,long pot,long call){
        metrics(new String[]{t("현재 팟","Current pot"),t("콜 비용","Price to call"),t("콜 필요 지분","Break-even equity"),t("추정 지분","Estimated equity")},
            new String[]{chips(pot),chips(call),ratio(g,"odds","%"),ratio(g,"equity","%")},159);
        if(call>0)frequency(t("폴드","Fold"),g.optInt("fold"),213,0xffcc9787);
        frequency(call>0?t("콜","Call"):t("체크","Check"),g.optInt("passive"),239,GREEN);
        if(g.optBoolean("canRaise"))frequency(g.optBoolean("opening")?t("베팅","Bet"):t("레이즈","Raise"),g.optInt("raise"),265,a.gold);
        label(g.optString("title"),LEFT,294,775,16,a.gold);
        paragraph(reason(detail,"mixReason"),319,3);
        if(call>0&&g.optLong("eligible")<pot)label(t("내가 받을 수 있는 현재 팟  ","Current pot eligible to win  ")+chips(g.optLong("eligible")),LEFT,393,675,13,a.gold);
        else label(reason(detail.optJSONObject("action"),"reason"),LEFT,393,780,13,a.muted);
    }
    private void frequency(String name,int percent,int y,int color){
        label(name,LEFT,y,115,15,a.paper);
        a.box(280,y-9,820,y-3,3,0xff35483d);
        if(percent>0)a.box(280,y-9,280+540*percent/100f,y-3,3,color);
        a.stage.bet.fitted(percent+"%",RIGHT,y,94,17,color,Paint.Align.RIGHT);
    }
    private void sizing(JSONObject g,JSONObject s){
        boolean open=g.optBoolean("opening"),can=g.optBoolean("canRaise");
        if(!can){
            label(t("현재 레이즈 불가","No legal raise in this spot"),LEFT,177,740,21,a.paper);
            paragraph(reason(s,"reason"),218,7);return;
        }
        metrics(new String[]{open?t("베팅 금액","Bet size"):t("레이즈 시 총액","Raise to"),t("지금 추가 투입","Chips to put in"),
                open?t("현재 팟 대비","As a share of pot"):t("콜 후 팟 대비 인상분","Raise increment / pot after call")},
            new String[]{chips(g.optLong("raiseTo")),chips(s.optLong("pay")),ratio(s,"potMultiple","×")},164);
        String basis=open?t("기준 팟","Current pot"):t("콜 후 팟","Pot after calling");
        label(basis+"  "+chips(s.optLong("potAfterCall"))+"     ·     "+t("인상분  ","Increment  ")+chips(s.optLong("increase")),LEFT,231,780,16,a.gold);
        label(t("가능한 총액  ","Legal total  ")+chips(s.optLong("minTo"))+" – "+chips(s.optLong("maxTo")),LEFT,260,780,15,a.paper);
        a.line(LEFT,276,RIGHT,276,LINE,1);
        paragraph(reason(s,"reason"),302,5);
    }
    private void seats(JSONObject detail,JSONObject action,JSONObject commit){
        JSONArray seats=detail.optJSONArray("seats");
        int n=seats==null?0:seats.length();
        label(t("내 스택  ","Your stack  ")+chips(commit.optLong("heroStack"))+"     ·     "+
            t("앞 순서 ","Before you ")+action.optInt("before")+"     ·     "+t("뒤 순서 ","Behind you ")+action.optInt("after")+"     ·     "+
            t("콜·체크 후 응답할 상대 ","Still to respond ")+action.optInt("pending"),LEFT,159,780,15,a.gold);
        int[] columns={LEFT,234,439,551,683,800},widths={78,195,108,126,113,132};
        String[] headers=a.en?new String[]{"Order","Player / position","Stack","Relative to you","Effective¹","After you call"}:
            new String[]{"액션 순서","선수 / 포지션","남은 스택","나보다 ±","유효 스택¹","콜·체크 후"};
        for(int i=0;i<columns.length;i++)label(headers[i],columns[i],183,widths[i],12,a.muted);
        a.line(LEFT,190,RIGHT,190,LINE,1);
        for(int i=0;i<n;i++){
            JSONObject seat=seats.optJSONObject(i);if(seat==null)continue;
            int y=208+i*23;
            long diff=seat.optLong("difference");
            String status=seat.optBoolean("allIn")?t("올인","All-in"):
                seat.optBoolean("canReraise")?t("레이즈 가능","Can raise"):
                seat.optBoolean("pending")?t("응답 대기","Must respond"):t("액션 완료","Acted");
            String player=a.tr(seat.optString("name"))+" · "+a.tr(seat.optString("position"));
            if("raiser".equals(seat.optString("role")))player+=t(" · 레이저"," · Raiser");
            else if("caller".equals(seat.optString("role")))player+=t(" · 콜러"," · Caller");
            String[] row={seat.optString("side").equals("before")?t("앞 순서","Before"):t("뒤 순서","Behind"),player,
                chips(seat.optLong("stack")),(diff==0?"":diff>0?"+":"−")+chips(Math.abs(diff)),chips(seat.optLong("effective")),status};
            for(int j=0;j<row.length;j++)label(row[j],columns[j],y,widths[j],14,j==5&&seat.optBoolean("canReraise")?a.gold:a.paper);
            if(i+1<n)a.line(LEFT,y+5,RIGHT,y+5,0xff2c4034,.7f);
        }
        label(t("¹ 서로 콜을 맞춘 뒤 둘 사이에 더 걸 수 있는 칩","¹ Chips you and this opponent can still wager against each other after matching the price"),LEFT,394,780,12,a.muted);
    }
    private void commitment(JSONObject c){
        metrics(new String[]{t("핸드 시작 스택","Starting stack"),t("이미 투입","Already invested"),t("콜·체크 후 내 스택","Stack after call or check")},
            new String[]{chips(c.optLong("startingStack")),chips(c.optLong("invested"))+"  ·  "+ratio(c,"investedPct","%"),chips(c.optLong("afterCall"))},164);
        metrics(new String[]{t("콜 후 누적 투입 비율","Starting stack invested after call"),t("상대와 남는 최대 유효 스택","Largest effective stack left"),t("콜 후 SPR","SPR after call")},
            new String[]{ratio(c,"committedAfterCallPct","%"),chips(c.optLong("effectiveAfterCall")),ratio(c,"spr","×")},234);
        label(t("참여 가능 팟 (콜 포함)  ","Pot you can win (including call)  ")+chips(c.optLong("eligibleAfterCall")),LEFT,291,775,15,a.gold);
        paragraph(reason(c,"reason"),321,4);
    }
    private void pots(JSONArray layers){
        if(layers==null)return;
        label(t("내 콜을 포함한 팟","Pot after your call"),LEFT,167,235,16,a.paper);
        label(t("참여 가능 금액","Pot amount"),401,167,170,14,a.muted);
        label(t("겨루는 상대","Eligible opponents"),593,167,160,14,a.muted);
        label(t("추정 지분","Estimated equity"),789,167,142,14,a.muted);
        for(int i=0;i<layers.length();i++){
            JSONObject layer=layers.optJSONObject(i);if(layer==null)continue;
            int y=197+i*21;
            label(i==0?t("메인 팟","Main pot"):t("사이드 팟 ","Side pot ")+i,LEFT,y,235,14,a.paper);
            label(chips(layer.optLong("amount")),401,y,170,15,a.paper);
            label(String.valueOf(layer.optInt("opponents")),593,y,160,15,a.paper);
            label(ratio(layer,"equity","%"),789,y,142,15,a.gold);
        }
        label(t("전체 추정 지분은 각 팟에서 이길 지분을 팟 금액으로 가중한 값입니다.",
            "Overall equity weights your estimated share of each pot by its chip value."),LEFT,394,780,12,a.muted);
    }
}
