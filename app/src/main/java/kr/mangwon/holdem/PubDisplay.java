package kr.mangwon.holdem;

import android.graphics.Bitmap;
import android.graphics.Paint;
import android.graphics.Typeface;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.Locale;

/** The tournament director's wall monitor, drawn over the physical screen artwork. */
final class PubDisplay {
    private final Arena a;
    private final Bitmap monitor;
    boolean seats;
    private static final int WHITE=0xffe9f0f4, GREY=0xffa3b3bf, AMBER=0xffffcd86, MINT=0xff8ed3ba;
    private static final int[] SMALL={100,200,400,500,1000,2000,4000,5000,10000,20000};
    private final Typeface clockFace=Typeface.create("sans-serif-condensed",Typeface.BOLD);

    PubDisplay(Arena arena){a=arena;monitor=a.image("tournament-monitor.jpg");}

    void open(){seats=false;a.stage.openTables();}
    void tab(String label,float x,float width,String key,boolean selected){
        if(selected)a.box(x,43,x+width,73,5,0xff293c45);
        a.mid(label,x+width/2,64,15,selected?WHITE:GREY);
        a.hit(x-2,35,x+width+2,81,key);
    }
    void draw(){
        a.hits.clear();a.box(0,0,1080,520,0,0xd30a0d0e);
        a.photo(monitor,8,7,1072,517);
        boolean cash=a.g.optBoolean("cash");
        a.text(a.pubs[a.g.optInt("pub")]+"   /   "+(cash?(a.g.optBoolean("double")?"더블보드":"원보드"):"데일리"),56,65,23,WHITE,a.bold,Paint.Align.LEFT);
        if(!cash){
            tab("전광판",676,92,"monitorClock",!seats);
            tab("좌석표",774,92,"monitorSeats",seats);
        }
        tab("닫기",929,88,"close",false);
        a.line(55,85,1020,85,0xff41505d,1);
        if(cash){drawCash();return;}
        if(seats){drawSeats();return;}
        JSONObject t=a.g.optJSONObject("tournament");
        if(t==null)t=new JSONObject();
        int lvl=a.g.optInt("level"),alive=t.optInt("alive",a.g.optInt("left"));
        boolean rest=a.g.optBoolean("break"), finalHand=t.optBoolean("finalRebuyHand");
        int elapsed=(int)a.g.optDouble("elapsed"),left=Math.max(0,(lvl+1)*240-elapsed);
        if(rest)left=(int)Math.ceil(a.g.optDouble("breakLeft"));
        String tm=!rest&&lvl==9?"FINAL":String.format(Locale.US,"%02d:%02d",left/60,left%60);
        a.txt(rest?"휴식 시간":"블라인드 레벨  "+String.format(Locale.US,"%02d",lvl+1),60,117,17,MINT);
        a.text(tm,56,211,91,WHITE,clockFace,Paint.Align.LEFT);
        a.box(61,229,453,233,2,0xff30434d);
        float bar=rest?0:Math.min(1,left/240f);
        if(bar>0)a.box(61,229,61+392*bar,233,2,MINT);
        a.txt("현재 블라인드",61,265,15,GREY);
        a.text(a.fmt(a.g.optInt("sb"))+" / "+a.fmt(a.g.optInt("bb")),59,307,34,AMBER,a.nums,Paint.Align.LEFT);
        if(lvl+1<SMALL.length){int small=SMALL[lvl+1];a.txt("다음   "+a.fmt(small)+" / "+a.fmt(small*2),61,336,16,GREY);}
        String re=finalHand?"이번 핸드가 마지막 리바인입니다":t.optBoolean("rebuyOpen",lvl<=8)?"리바인 접수 중  ·  10,000 / 20,000까지":"리바인 마감";
        a.txt(re,61,370,15,finalHand?AMBER:MINT);
        a.line(479,108,479,379,0xff35434f,1);
        metric("생존 플레이어",String.valueOf(alive),506,118);
        metric("총 엔트리",String.valueOf(t.optInt("entries",a.g.optInt("entries"))),680,118);
        metric("리바인",String.valueOf(t.optInt("rebuys",a.g.optInt("rebuyCount"))),858,118);
        a.line(506,186,1017,186,0xff35434f,1);
        a.txt("평균 스택",506,212,15,GREY);
        a.text(a.fmt(t.optInt("average"))+" 칩",1017,213,21,WHITE,a.nums,Paint.Align.RIGHT);
        a.txt("상금 순위",506,254,15,MINT);
        JSONArray payouts=a.g.optJSONArray("payouts"),leaders=a.g.optJSONArray("leaders");
        for(int i=0;i<3;i++){
            float y=282+i*35;
            a.txt((i+1)+"위",506,y,15,i==0?AMBER:GREY);
            a.txt(a.fmt(payouts==null?0:payouts.optInt(i))+" P",566,y,18,WHITE);
        }
        a.line(748,240,748,362,0xff35434f,1);
        a.txt("칩 순위",770,254,15,MINT);
        for(int i=0;leaders!=null&&i<Math.min(3,leaders.length());i++){
            JSONObject p=leaders.optJSONObject(i);
            float y=282+i*35;
            a.txt(p.optInt("rank")+"  "+p.optString("name"),770,y,15,i==0?AMBER:WHITE);
            a.text(p.optString("display"),1017,y,16,WHITE,a.nums,Paint.Align.RIGHT);
        }
        a.line(57,394,1018,394,0xff41505d,1);
        a.txt("총상금",60,424,15,GREY);
        a.text(a.fmt(a.g.optInt("pool"))+" P",59,459,29,AMBER,a.nums,Paint.Align.LEFT);
        for(int i=0;i<a.stage.tableCount;i++){
            int idx=a.stage.tableOrder[i];
            JSONObject table=a.stage.tablesData().optJSONObject(idx);
            float x=380+i*159;
            a.txt((a.stage.tableCount==1?"파이널":"테이블 "+table.optInt("number"))+(idx==a.stage.heroTable?" · 내 자리":""),x,424,15,GREY);
            a.txt(table.optInt("seats")+"명  ·  "+a.stage.street(table),x,452,15,WHITE);
        }
    }
    private void metric(String label,String value,float x,float y){
        a.txt(label,x,y+4,15,GREY);a.text(value,x,y+51,42,WHITE,a.nums,Paint.Align.LEFT);
    }
    private void drawCash(){
        JSONObject stats=a.g.optJSONObject("cashStats"),table=a.stage.first();
        if(stats==null||table==null)return;
        a.txt("착석 플레이어",61,126,22,AMBER);
        a.txt("이름",61,166,15,GREY);a.txt("바이인",250,166,15,GREY);a.txt("현재 스택",391,166,15,GREY);
        JSONArray ps=table.optJSONArray("players");
        for(int i=0;ps!=null&&i<ps.length();i++){
            JSONObject p=ps.optJSONObject(i);float y=198+i*27;
            int color=p.optBoolean("active")?AMBER:WHITE;
            a.txt(p.optString("name"),61,y,17,color);
            a.text(a.fmt(p.optInt("buyin")),314,y,17,WHITE,a.nums,Paint.Align.RIGHT);
            a.text(a.fmt(p.optInt("stack")),466,y,17,color,a.nums,Paint.Align.RIGHT);
            if(p.optInt("id")>=0)a.hit(56,y-21,469,y+5,"person"+p.optInt("id"));
        }
        a.line(499,111,499,415,0xff35434f,1);
        a.txt("내 세션 정산",535,126,22,AMBER);
        cashLine("완료 핸드",stats.optInt("completed")+"핸드",169);
        cashLine("이번 핸드 레이크",a.fmt(stats.optInt("handRake"))+" P",207);
        cashLine("누적 레이크",a.fmt(stats.optInt("rake"))+" P",245);
        cashLine("다음 워싱까지",stats.optInt("washIn")+"핸드",283);
        cashLine("워싱으로 지갑에 지급",a.fmt(stats.optInt("banked"))+" P",321);
        int net=stats.optInt("net");
        cashLine("내 누적 손익",(net>0?"+":"")+a.fmt(net)+" P",359);
        a.line(58,430,1018,430,0xff41505d,1);
        a.txt("30핸드마다 70만 P를 초과한 보유 칩을 지갑으로 옮깁니다.",61,463,15,GREY);
    }
    private void cashLine(String label,String value,float y){
        a.txt(label,535,y,16,GREY);a.text(value,1009,y,18,WHITE,a.nums,Paint.Align.RIGHT);
    }
    private void drawSeats(){
        StageArt s=a.stage;
        int start=s.tablePage*2;
        for(int slot=start;slot<Math.min(start+2,s.tableCount);slot++){
            int idx=s.tableOrder[slot];JSONObject table=s.tablesData().optJSONObject(idx);
            float x=61+(slot-start)*492;
            a.txt("테이블 "+table.optInt("number")+(idx==s.heroTable?"   ·   내 자리":""),x,121,22,AMBER);
            a.txt(s.street(table)+"   ·   "+table.optInt("seats")+"명   ·   팟 "+a.fmt(table.optInt("pot")),x,149,15,GREY);
            a.txt("이름",x,179,13,MINT);a.txt("보유 칩",x+158,179,13,MINT);a.txt("리바인",x+286,179,13,MINT);
            JSONArray ps=table.optJSONArray("players");
            for(int j=0;ps!=null&&j<ps.length();j++){
                JSONObject p=ps.optJSONObject(j);float y=207+j*25;
                int color=p.optBoolean("fold")?GREY:WHITE;
                a.txt(p.optString("name"),x,y,16,color);
                a.text(a.fmt(p.optInt("stack")),x+223,y,16,color,a.nums,Paint.Align.RIGHT);
                a.txt(p.optInt("rebuys")+"회",x+290,y,14,GREY);
                if(p.optBoolean("active"))a.txt("결정 중",x+367,y,14,AMBER);
                else if(p.optBoolean("fold"))a.txt("폴드",x+367,y,14,GREY);
                if(p.optInt("id")>=0)a.hit(x-4,y-18,x+414,y+5,"person"+p.optInt("id"));
            }
        }
        a.line(57,426,1018,426,0xff41505d,1);
        JSONObject move=a.g.optJSONObject("lastTransfer");
        a.txt(move==null?"이름을 누르면 캐릭터를 볼 수 있습니다.":"최근 이동   "+move.optString("name")+"   "+move.optInt("from")+"번 → "+move.optInt("to")+"번",62,458,15,GREY);
        if(start>0){a.mid("‹ 이전",789,458,17,WHITE);a.hit(739,429,842,477,"tablePrev");}
        if(start+2<s.tableCount){a.mid("다음 ›",955,458,17,WHITE);a.hit(903,429,1019,477,"tableNext");}
    }
}
