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
    private int entryPrizePage, livePrizePage, overviewPage, previousOfferPlaces;
    private static final int WHITE=0xffe9f0f4, GREY=0xffa3b3bf, AMBER=0xffffcd86, MINT=0xff8ed3ba;
    private final Typeface clockFace=Typeface.create("sans-serif-condensed",Typeface.BOLD);

    PubDisplay(Arena arena){a=arena;monitor=a.image("tournament-monitor.jpg");}

    void open(){seats=false;livePrizePage=0;overviewPage=0;a.stage.openTables();}
    boolean action(String key){
        switch(key){
            case "entryPrizePrev":entryPrizePage--;return true;
            case "entryPrizeNext":entryPrizePage++;return true;
            case "livePrizePrev":livePrizePage--;return true;
            case "livePrizeNext":livePrizePage++;return true;
            case "overviewPrev":overviewPage--;return true;
            case "overviewNext":overviewPage++;return true;
            default:return false;
        }
    }
    void entryPrizes(JSONObject offer){
        // Stretch the lacquered centre independently of the original brass end caps.
        if(a.stage.trim!=null){
            a.stage.bet.part(a.stage.trim,0,.103f,.079f,.48f,132,286,192,431,255);
            a.stage.bet.part(a.stage.trim,.079f,.103f,.421f,.48f,192,286,888,431,255);
            a.stage.bet.part(a.stage.trim,.421f,.103f,.5f,.48f,888,286,948,431,255);
        }else a.box(132,286,948,431,18,0xff142a22);
        a.txt("리바인 전 총상금",189,332,16,a.paper);
        a.text(a.fmt(offer.optLong("pool"))+" P",187,367,31,a.gold,a.nums,Paint.Align.LEFT);
        a.text("상위 "+offer.optInt("paidPlaces")+"명 머니인",189,399,17,a.paper,a.bold,Paint.Align.LEFT);
        a.line(416,315,416,402,0x685f806c,1);
        if(previousOfferPlaces!=offer.optInt("paidPlaces"))entryPrizePage=0;
        previousOfferPlaces=offer.optInt("paidPlaces");
        prizeRows(offer,441,896,328,25,a.paper,a.gold,0xffbfd0be,true);
    }

    private void prizeRows(JSONObject schedule,float left,float right,float top,float lineHeight,
                           int body,int first,int secondary,boolean entry){
        JSONArray amounts=schedule.optJSONArray("payouts"),percent=schedule.optJSONArray("payoutPercentages");
        if(amounts==null||amounts.length()==0)return;
        int count=amounts.length(),bands=0;
        for(int i=0;i<count;i++)if(i==0||differentAward(amounts,percent,i))bands++;
        int pages=(bands+5)/6,page=Math.max(0,Math.min(pages-1,entry?entryPrizePage:livePrizePage));
        if(entry)entryPrizePage=page;else livePrizePage=page;
        float width=(right-left-20)/2;
        int firstBand=page*6,rows=(Math.min(6,bands-firstBand)+1)/2;
        for(int begin=0,band=0;begin<count;band++){
            int end=begin+1;
            while(end<count&&!differentAward(amounts,percent,end))end++;
            int slot=band-firstBand;
            if(slot>=0&&slot<6){
                float x=left+(slot/rows)*(width+20);
                float y=top+(slot%rows)*lineHeight+(pages==1?(3-rows)*lineHeight*.5f+5:0);
                int color=begin==0?first:body;
                String rank=begin+1==end?end+"위":(begin+1)+"~"+end+"위";
                a.stage.bet.fitted(rank,x,y,57,16,color,Paint.Align.LEFT);
                if(percent!=null)a.stage.bet.fitted(percent.optInt(begin)+"%",x+84,y,26,14,secondary,Paint.Align.RIGHT);
                a.stage.bet.fitted(a.fmt(amounts.optLong(begin))+" P",x+width,y,width-90,19,color,Paint.Align.RIGHT);
            }
            begin=end;
        }
        if(pages>1)pager(entry?"entryPrize":"livePrize",page,pages,(left+right)/2,top+3*lineHeight,secondary);
    }
    private boolean differentAward(JSONArray amounts,JSONArray percents,int i){
        return amounts.optLong(i)!=amounts.optLong(i-1)||(percents!=null&&percents.optInt(i)!=percents.optInt(i-1));
    }
    private void pager(String key,int page,int count,float center,float y,int color){
        a.mid((page+1)+" / "+count,center,y,13,color);
        if(page>0){a.mid("‹ 이전",center-72,y,15,color);a.hit(center-114,y-18,center-32,y+7,key+"Prev");}
        if(page+1<count){a.mid("다음 ›",center+72,y,15,color);a.hit(center+32,y-18,center+114,y+7,key+"Next");}
    }
    void tab(String label,float x,float width,String key,boolean selected){
        if(selected)a.box(x,43,x+width,73,5,0xff293c45);
        a.mid(label,x+width/2,64,15,selected?WHITE:GREY);
        a.hit(x-2,35,x+width+2,81,key);
    }
    void draw(){
        a.hits.clear();a.box(0,0,1080,520,0,0xd30a0d0e);
        a.photo(monitor,8,7,1072,517);
        boolean cash=a.g.optBoolean("cash");
        a.text(a.pubs[a.g.optInt("pub")]+"   /   "+(cash?(a.g.optBoolean("double")?"더블보드":"원보드"):(a.g.optInt("pub")==0?"데일리":"토너먼트")),56,65,23,WHITE,a.bold,Paint.Align.LEFT);
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
        int left=Math.max(0,(int)Math.ceil(a.g.optDouble("levelSecondsRemaining",0)));
        if(rest)left=(int)Math.ceil(a.g.optDouble("breakLeft"));
        String tm=String.format(Locale.US,"%02d:%02d",left/60,left%60);
        a.txt(rest?"휴식 시간":"블라인드 레벨  "+String.format(Locale.US,"%02d",lvl+1),60,117,17,MINT);
        a.text(tm,56,211,91,WHITE,clockFace,Paint.Align.LEFT);
        a.box(61,229,453,233,2,0xff30434d);
        float bar=rest?0:Math.min(1,left/(float)Math.max(1,a.g.optInt("levelSeconds",240)));
        if(bar>0)a.box(61,229,61+392*bar,233,2,MINT);
        a.txt("현재 블라인드",61,265,15,GREY);
        a.text(a.fmt(a.g.optLong("sb"))+" / "+a.fmt(a.g.optLong("bb")),59,307,34,AMBER,a.nums,Paint.Align.LEFT);
        JSONObject next=a.g.optJSONObject("nextLevelBlinds");
        if(next!=null)a.txt("다음   "+a.fmt(next.optLong("sb"))+" / "+a.fmt(next.optLong("bb")),61,336,16,GREY);
        String re=finalHand?"이번 핸드가 마지막 리바인입니다":t.optBoolean("rebuyOpen")?"리바인 접수 중  ·  5,000 / 10,000까지":"리바인 마감";
        a.txt(re,61,370,15,finalHand?AMBER:MINT);
        a.line(479,108,479,379,0xff35434f,1);
        metric("생존 / 시작 인원",alive+" / "+t.optInt("initial"),506,114);
        metric("총 엔트리",String.valueOf(t.optInt("entries")),714,114);
        metric("리바인",String.valueOf(t.optInt("rebuys")),888,114);
        a.line(506,186,1017,186,0xff35434f,1);
        a.text("머니인 · 상위 "+t.optInt("paidPlaces")+"명",506,218,19,MINT,a.bold,Paint.Align.LEFT);
        a.text("총상금  "+a.fmt(t.optLong("pool"))+" P",1017,218,20,AMBER,a.nums,Paint.Align.RIGHT);
        prizeRows(t,506,1017,249,25,WHITE,AMBER,GREY,false);
        a.line(506,338,1017,338,0xff35434f,1);
        a.txt("평균 스택",506,368,15,GREY);
        a.text(a.fmt(t.optInt("average"))+" 칩",1017,369,20,WHITE,a.nums,Paint.Align.RIGHT);
        a.line(57,390,1018,390,0xff41505d,1);
        a.txt("칩 순위",60,416,15,MINT);
        JSONArray leaders=a.g.optJSONArray("leaders");
        for(int i=0;leaders!=null&&i<Math.min(3,leaders.length());i++){
            JSONObject player=leaders.optJSONObject(i);
            if(player==null)continue;
            float x=60+i*137;
            a.txt(player.optInt("rank")+"위 "+player.optString("name"),x,440,15,i==0?AMBER:WHITE);
            a.text(player.optString("display"),x,463,17,WHITE,a.nums,Paint.Align.LEFT);
        }
        a.line(479,405,479,469,0xff35434f,1);
        a.txt("테이블 현황",506,416,15,MINT);
        int pages=(a.stage.tableCount+3)/4;
        overviewPage=Math.max(0,Math.min(pages-1,overviewPage));
        if(pages>1)pager("overview",overviewPage,pages,895,417,GREY);
        for(int row=0;row<4&&overviewPage*4+row<a.stage.tableCount;row++){
            int i=overviewPage*4+row;
            int idx=a.stage.tableOrder[i];
            JSONObject table=a.stage.tablesData().optJSONObject(idx);
            float x=506+(row%2)*264,y=440+(row/2)*23;
            String name=a.stage.tableCount==1?"파이널":table.optInt("number")+"번";
            if(idx==a.stage.heroTable)name+=" · 내 자리";
            a.txt(name,x,y,15,idx==a.stage.heroTable?AMBER:GREY);
            a.text(table.optInt("seats")+"명  ·  "+a.stage.street(table),x+247,y,15,WHITE,a.normal,Paint.Align.RIGHT);
        }
    }
    private void metric(String label,String value,float x,float y){
        a.txt(label,x,y+4,15,GREY);a.text(value,x,y+51,42,WHITE,a.nums,Paint.Align.LEFT);
    }
    private void drawCash(){
        JSONObject stats=a.g.optJSONObject("cashStats"),table=a.stage.first();
        if(stats==null||table==null)return;
        a.txt("착석 플레이어",61,126,22,AMBER);
        JSONArray posts=a.g.optJSONArray("forcedPosts");
        a.txt("이름",61,166,15,GREY);
        if(posts!=null)a.txt("시작 납부",161,166,15,GREY);
        a.txt("바이인",259,166,15,GREY);a.txt("현재 스택",391,166,15,GREY);
        JSONArray ps=table.optJSONArray("players");
        for(int i=0;ps!=null&&i<ps.length();i++){
            JSONObject p=ps.optJSONObject(i);float y=198+i*27;
            int color=p.optBoolean("active")?AMBER:WHITE;
            a.txt(p.optString("name"),61,y,17,color);
            if(posts!=null){
                JSONObject post=posts.optJSONObject(i);
                if(post!=null&&post.optInt("id") == p.optInt("id"))
                    a.text(a.fmt(post.optInt("ante")+post.optInt("blind")),231,y,15,a.gold,a.nums,Paint.Align.RIGHT);
            }
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
        cashLine("이번 핸드",a.cashBlindLabel(),397);
        a.line(58,430,1018,430,0xff41505d,1);
        a.txt("30핸드마다 "+a.fmt(stats.optInt("washCap",700000))+" P를 초과한 보유 칩을 지갑으로 옮깁니다.",61,463,15,GREY);
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
        if(s.tableCount>2)a.mid((s.tablePage+1)+" / "+((s.tableCount+1)/2),871,457,14,GREY);
        if(start+2<s.tableCount){a.mid("다음 ›",955,458,17,WHITE);a.hit(903,429,1019,477,"tableNext");}
    }
}
