package kr.mangwon.holdem;

import android.graphics.*;
import android.os.SystemClock;
import org.json.*;
import java.util.Locale;

/** Shared atlas rendering, action choreography and public tournament display. */
final class StageArt {
    final Arena a;final BetArt bet; final TitleSequence title; final RectF dst=new RectF(); final Rect srcRect=new Rect(); final long[] badgeTime=new long[50];
    final Bitmap buttons, trim, showdown, hands, props, bartender;
    final VenueScene place;final ChipMotion chips;final RebuyEntrance rebuys;
    final OpponentHands opponents;
    long accepted, gestureAt, revealAt;
    private long titleElapsed, lastTitleFrame;
    final int[] tableOrder=new int[4];
    int tableCount, tablePage, heroTable;
    int gesture=-1, serial=-1, hand=-1, shown=-1;
    StageArt(Arena arena){
        a=arena;buttons=a.image("controls.png");trim=a.image("trim.png");
        opponents=new OpponentHands(this);
        showdown=a.image("showdown.png");hands=a.image("gestures.png");props=a.image("ambience.png");bartender=a.image("bartender.png");title=new TitleSequence(this);bet=new BetArt(this);place=new VenueScene(a);chips=new ChipMotion(this);rebuys=new RebuyEntrance(this);
    }
    void tile(Bitmap b,int columns,int rows,int i,float l,float t,float r,float d){
        tileAlpha(b,columns,rows,i,l,t,r,d,255);
    }
    void tileAlpha(Bitmap b,int columns,int rows,int i,float l,float t,float r,float d,int alpha){
        if(b==null||i<0||alpha<=0)return;
        int x=i%columns,y=i/columns;
        srcRect.set(x*b.getWidth()/columns,y*b.getHeight()/rows,(x+1)*b.getWidth()/columns,(y+1)*b.getHeight()/rows);dst.set(l,t,r,d);a.reset(-1);a.p.setAlpha(alpha);a.c.drawBitmap(b,srcRect,dst,a.p);
    }
    // The atlas shows the back of a left hand. Mirror the hand in its own bounds;
    // loose cards and chips keep their independent positions on the table.
    void rightHand(int cell,float crop,float x,float y,float w,float h,int alpha){
        if(alpha<=0)return;
        a.c.save();a.c.scale(-1,1,x+w*.5f,y);
        if(crop==0)tileAlpha(hands,3,4,cell,x,y,x+w,y+h,alpha);
        else{
            int col=cell%3,row=cell/3;
            bet.part(hands,col/3f,(row+crop)/4f,(col+1)/3f,(row+1)/4f,
                x,y+crop*h,x+w,y+h,alpha);
        }
        a.c.restore();
    }
    void plate(float l,float t,float r,float b,int type){plate(l,t,r,b,type,null);}
    void plate(float l,float t,float r,float b,int type,ColorFilter tone){
        if(trim!=null){
            int cw=trim.getWidth()/2,ch=trim.getHeight()/2,xx=(type%2)*cw,yy=(type/2)*ch;
            int top=type<2?(int)(ch*.20):(int)(ch*.10),bottom=type<2?(int)(ch*.97):(int)(ch*.89);
            srcRect.set(xx,yy+top,xx+cw,yy+bottom);dst.set(l,t,r,b);a.reset(-1);a.p.setColorFilter(tone);a.c.drawBitmap(trim,srcRect,dst,a.p);
        }
        else a.box(l,t,r,b,4,0xf31a2a24);
    }
    void onSnapshot(JSONObject old,JSONObject g){
        accepted=SystemClock.uptimeMillis();chips.observe(old,g,accepted);orderTables(g.optJSONArray("tables"));
        opponents.observe(old,g,accepted);
        if(g.optBoolean("showdownTitle") && (old==null || old.optInt("hand")!=g.optInt("hand") || !old.optBoolean("showdownTitle"))){
            titleElapsed=g.optLong("showdownTitleMs",0);
            lastTitleFrame=0;
        }
        if(old==null||hand!=g.optInt("hand")){gesture=-1;serial=-1;hand=g.optInt("hand");}
        JSONObject ev=g.optJSONObject("lastHero");
        if(ev!=null&&serial!=ev.optInt("seq")){
            serial=ev.optInt("seq");String k=ev.optString("kind");
            gesture=k.equals("check")?0:k.equals("call")?1:k.equals("raise")?2:3;
            gestureAt=old==null?accepted-3000:accepted;
        }
        if(old!=null&&old.optInt("hand")==hand){JSONArray prev=old.optJSONArray("p"),players=g.optJSONArray("p");for(int j=0;j<Math.min(prev.length(),players.length());j++){JSONObject q=players.optJSONObject(j),before=prev.optJSONObject(j);int id=q.optInt("id");if(id>=0&&id==before.optInt("id")&&!q.optString("action").equals(before.optString("action")))badgeTime[id]=accepted;}}
        int next=g.optInt("show");if(next!=shown){shown=next;revealAt=accepted;}
    }
    private static final int[] POLISHING={0,0,1,2,3,2,3,2,4,5,5,4,1,0};
    void ambience(){
        boolean table=a.screen.equals("table")||a.panel.equals("entry");
        if(!table)return;
        long t=a.now-a.born;int venue=place.venue();
        if(venue!=0){place.draw(venue,t);return;}
        // The back counter in the 1080 x 520 room painting runs through y=93.
        // Hide the apron behind its lip, and plant the saucer on its upper face.
        int beat=(int)(t/230%POLISHING.length);
        a.c.save();
        a.c.clipRect(633,0,708,93);
        tile(bartender,3,2,POLISHING[beat],633,25,708,100);
        a.c.restore();
        tile(props,4,2,4+(int)(t/240%4),704,62,737,95);
        float pass=(t%11000)/11000f;
        if(pass<.38f){
            a.c.save();a.c.clipRect(297,37,625,84);
            float x=285+pass/.38f*365;
            a.grad(x-23,58,x+5,64,0x008ac3da,0x978ac3da,false);
            a.grad(x+9,58,x+33,64,0x948ac3da,0x008ac3da,false);
            a.c.restore();
        }
        // Only the rain-streaked window receives moving highlights.
        a.c.save();a.c.clipRect(294,0,647,83);
        for(int k=0;k<17;k++){
            float x=298+k*21+(k*11%9),y=(t*(.018f+k%4*.002f)+k*39)%113-20;
            a.line(x,y,x-2.4f,y+8+(k%4),0x658badb8,1);
        }
        a.c.restore();
    }
    void button(float x,float y,String label,String amount,String id){
        int index=id.equals("fold")?0:id.equals("raise")?3:label.equals("체크")?1:2;
        float y0=y+(id.equals(a.held)?3:0);
        tile(buttons,2,2,index,x-56,y0-31,x+56,y0+28);
        if(!amount.isEmpty()){a.box(x-35,y0+16,x+35,y0+33,8,0xf5223130);a.mid(amount,x,y0+29,13,a.paper);}
        a.hit(x-55,y-35,x+55,y+36,id);
    }
    void heroWagerHand(){chips.hand();}

    void heroGesture(){
        if(gesture<0||a.deal.running())return;
        long age=a.now-gestureAt;
        if(gesture==0&&age>=0&&age<1150){
            float enter=TitleSequence.out(TitleSequence.range(age,0,260));
            float leave=TitleSequence.smooth(TitleSequence.range(age,790,360));
            float tap=checkTap(age,300)+checkTap(age,555);
            float x=650+(1-enter)*70+leave*85,y=347+(1-enter)*146+leave*155+tap*5;
            a.c.save();a.c.rotate(6-tap*5,x+71,y+117);
            rightHand(0,0,x,y,133,150,(int)(255*(1-tap)));
            rightHand(1,0,x,y,133,150,(int)(255*tap));
            a.c.restore();
        }
    }
    private static float checkTap(long age,int start){
        float down=TitleSequence.smooth(TitleSequence.range(age,start,80));
        float up=TitleSequence.smooth(TitleSequence.range(age,start+94,103));
        return down*(1-up);
    }
    JSONArray tablesData(){return a.g==null?null:a.g.optJSONArray("tables");}
    void orderTables(JSONArray tables){
        tableCount=tables==null?0:Math.min(tables.length(),tableOrder.length);
        heroTable=0;
        for(int i=0;i<tableCount;i++){
            JSONObject t=tables.optJSONObject(i);
            int number=t.optInt("number",i+1),at=i;
            while(at>0){
                int previous=tableOrder[at-1];
                if(tables.optJSONObject(previous).optInt("number",previous+1)<=number)break;
                tableOrder[at]=previous;at--;
            }
            tableOrder[at]=i;
            JSONArray players=t.optJSONArray("players");
            if(players!=null)for(int j=0;j<players.length();j++){
                JSONObject p=players.optJSONObject(j);
                if(p.has("id")&&p.optInt("id")==-1){heroTable=i;break;}
            }
        }
        tablePage=Math.min(tablePage,Math.max(0,(tableCount-1)/2));
    }
    void openTables(){
        tablePage=0;
        for(int at=0;at<tableCount;at++)if(tableOrder[at]==heroTable){tablePage=at/2;break;}
    }
    void changePage(int by){tablePage=Math.max(0,Math.min((tableCount-1)/2,tablePage+by));}
    JSONObject first(){JSONArray ts=tablesData();return ts==null?null:ts.optJSONObject(0);}
    double time(JSONObject t){
        if(t==null)return 0;
        boolean foreground=t==first();
        boolean running=a.host.active&&a.tableOpen()&&(!foreground||(a.live()&&!a.panel.equals("wagers")&&!a.panel.equals("profile")&&a.host.talkingUntil<a.now));
        boolean pausedForInspection=a.panel.equals("tables")&&t.optBoolean("isMe");
        double extrapolate=running&&!pausedForInspection?Math.min(.19,Math.max(0,(a.now-accepted)/1000.)):0;
        return Math.max(0,t.optDouble("time")-extrapolate);
    }
    void gauge(JSONObject t,float x,float y,float w,boolean seconds){
        if(t==null||!t.optString("phase").equals("bet"))return;
        boolean free=t.optBoolean("isMe")&&!a.opt.optBoolean("timing");
        double left=time(t),limit=Math.max(.01,t.optDouble("max"));
        float f=free?1:(float)Math.min(1,left/limit);
        tile(trim,2,2,3,x-6,y-5,x+w+6,y+9);
        a.box(x,y,x+w,y+4,2,0xff514d37);
        if(f>0){int col=f<.25?0xffed795e:f<.5?0xffefbd6c:0xffa4d7a2;
            a.box(x,y,x+w*f,y+4,2,col);a.box(x,y,x+w*f,y+1,1,0xfff3e3ba);}
        if(seconds){String v=free?"∞":String.format(Locale.US,"%.1f초",left);
            a.box(x+w-14,y+5,x+w+22,y+18,5,0xf2202f24);a.mid(v,x+w+4,y+15,10,a.paper);}
    }

    String street(JSONObject t){
        String phase=t.optString("phase");if(phase.equals("done"))return "정산 완료";
        if(phase.equals("showdown"))return "쇼다운";return a.streets[t.optInt("stage")];
    }
    void tableTicker(){
        JSONArray ts=tablesData();if(ts==null)return;
        if(rebuys.ticket()){a.hit(13,372,275,436,"tables");return;}
        plate(13,372,275,436,0);
        a.c.save();a.c.clipRect(42,383,246,426);
        for(int slot=0;slot<tableCount;slot++){
            int index=tableOrder[slot];JSONObject t=ts.optJSONObject(index);
            int number=t.optInt("number",index+1),incoming=t.optInt("incoming");
            String count=t.optInt("seats")+(incoming>0?"+"+incoming:"")+"명";
            if(tableCount>2){
                float x=42+(slot%2)*104,y=400+(slot/2)*18;
                bet.fitted(number+"번 "+count,x,y,48,11.7f,a.gold,Paint.Align.LEFT);
                bet.fitted(t.optString("phase").equals("done")?"정산":street(t),x+52,y,44,11.7f,a.paper,Paint.Align.LEFT);
            }else{
                float y=tableCount==1?409:399+slot*19;
                boolean last=tableCount==1&&!a.g.optBoolean("cash");
                String label=(last?"파이널 ":"")+number+"번";
                bet.fitted(label+" "+count,43,y,62,12.1f,a.gold,Paint.Align.LEFT);
                bet.fitted(street(t),108,y,52,12.1f,a.paper,Paint.Align.LEFT);
                String who=t.optString("actor");
                bet.fitted(who.isEmpty()?"팟 "+a.fmt(t.optInt("pot")):who+" 차례",165,y,78,12.1f,who.isEmpty()?a.muted:a.paper,Paint.Align.LEFT);
            }
        }
        a.c.restore();a.hit(13,372,275,436,"tables");
    }
    void turnNotice(String label,float x,float centerY,boolean own){
        final float size=own?15.5f:14;
        a.reset(a.paper);a.p.setTypeface(a.title);a.p.setTextSize(size);
        float span=Math.min(166,Math.max(76,a.p.measureText(label)+33));
        bet.part(bet.plaque,.012f,.15f,.988f,.86f,x-span/2,centerY-13,x+span/2,centerY+13,own?255:215);
        a.text(label,x,centerY+size*.34f,size,own?0xfff6dfae:0xffddd8c5,a.title,Paint.Align.CENTER);
    }
    void nextHandButton(){
        if(a.g.isNull("autoNextLeft")){
            a.key("다음 핸드  ›",856,445,200,53,"next",1);return;
        }
        String action=a.g.optString("afterHand");
        String label=action.equals("rebuy")?"리바인 선택":action.equals("result")?"대회 결과":action.equals("break")?"휴식 시작":"다음 핸드";
        int seconds=Math.max(1,(int)Math.ceil(a.g.optDouble("autoNextLeft",3)));
        float top=445+("next".equals(a.held)?2:0);
        plate(856,top,1056,top+53,1);
        a.mid(label,956,top+23,18,a.ink);
        a.mid(seconds+"초 뒤 자동 진행",956,top+40,12,a.ink);
        a.hit(856,445,1056,501,"next");
    }
    void prizes(int pool,float center,float y,float gap){
        int[] pct={50,30,20};for(int i=0;i<3;i++)a.mid((i+1)+"위  "+a.shortP((int)((long)pool*pct[i]/100))+"P  ("+pct[i]+"%)",center+(i-1)*gap,y,15,i==0?a.gold:a.paper);
    }
    void tables(){
        JSONArray ts=tablesData();if(ts==null)return;a.hits.clear();
        a.box(0,0,1080,520,0,0xc30a120e);a.box(63,22,1017,498,12,0xff1a2d25);
        a.stroke(63,22,1017,498,12,0xffa2845b,1);
        a.text("테이블 현황",93,64,28,a.paper,a.title,Paint.Align.LEFT);
        a.brass("닫기",907,37,80,"close");
        ChipLeaders.draw(a,true);
        boolean cash=a.g.optBoolean("cash");
        a.txt(cash?(a.g.optBoolean("double")?"더블보드":"원보드")+"  ·  바이인 "+a.fmt(a.g.optInt("buyin"))+"P  ·  핸드별 팟 정산":
            "생존 "+a.g.optInt("left")+" / "+a.g.optInt("entrants")+"명      총 "+a.g.optInt("entries")+"바이인  (리바인 "+a.g.optInt("rebuyCount")+"회)      총상금 "+a.fmt(a.g.optInt("pool"))+"P",94,99,17,a.gold);
        int first=tablePage*2;
        for(int slot=first;slot<Math.min(first+2,tableCount);slot++){
            int index=tableOrder[slot];JSONObject t=ts.optJSONObject(index);
            float x=96+(slot-first)*458;
            String title=(tableCount==1&&!cash?"파이널  ·  ":"")+"테이블 "+t.optInt("number",index+1);
            a.txt(title+(index==heroTable?"  ·  내 자리":""),x,144,21,a.paper);
            a.txt(t.optInt("seats")+"명 착석   ·   "+street(t)+"   ·   남은 패 "+t.optInt("remain")+"명"+(t.optInt("incoming")>0?"   ·   착석 대기 "+t.optInt("incoming")+"명":""),x,170,15,a.muted);
            a.txt("팟  "+a.fmt(t.optInt("pot")),x,196,15,a.gold);
            JSONArray ps=t.optJSONArray("players");
            for(int k=0;k<ps.length();k++){
                JSONObject p=ps.optJSONObject(k);float y=223+k*(ps.length()==9?21:23);
                int id=p.optInt("id",-1);
                a.txt(p.optString("name"),x,y,15,p.optBoolean("fold")?a.muted:a.paper);
                if(id>=0){
                    if(p.optInt("tier")==2)a.txt("최상급",x+55,y,11,a.gold);
                    a.txt("›",x+96,y,16,a.gold);
                    a.hit(x-4,y-16,x+367,y+4,"person"+id);
                }
                a.text(a.fmt(p.optInt("stack")),x+173,y,15,a.paper,a.nums,Paint.Align.RIGHT);
                if(p.optBoolean("active")){a.txt("차례",x+194,y,13,a.gold);gauge(t,x+234,y-6,105,true);}
                else if(p.optBoolean("fold"))a.txt("폴드",x+194,y,12,a.muted);
            }
        }
        a.line(96,413,982,413,0xff71674b,1);
        if(!cash)prizes(a.g.optInt("pool"),540,443,273);
        JSONObject transfer=a.g.optJSONObject("lastTransfer");
        String footer=transfer!=null?"최근 이동  "+transfer.optString("name")+"   "+transfer.optInt("from")+"번 → "+transfer.optInt("to")+"번":
            "이름을 누르면 인물 소개를 봅니다.";
        if(tableCount>2){
            int pages=(tableCount+1)/2;
            if(tablePage>0)a.key("‹  이전",95,451,115,39,"tablePrev",0);
            if(tablePage+1<pages)a.key("다음  ›",870,451,115,39,"tableNext",0);
            footer=(tablePage+1)+" / "+pages+"쪽     ·     "+footer;
        }
        a.mid(footer,540,478,14,transfer==null?a.muted:a.paper);
    }
    void showdownTitle(){
        if(a.panel.length()>0 || !a.host.active)return;
        if(lastTitleFrame!=0)titleElapsed+=Math.max(0,Math.min(64,a.now-lastTitleFrame));
        lastTitleFrame=a.now;
        if(title.available()){title.draw(titleElapsed);return;}
        a.c.save();
        float t=Math.min(1,titleElapsed/310f),ease=1-(1-t)*(1-t)*(1-t);
        a.box(0,0,1080,520,0,0x6407110d);a.c.translate(110*(1-ease),0);
        float size=.76f+.24f*ease+.035f*(float)Math.sin(Math.PI*t);
        a.c.scale(size,size,540,246);a.photo(showdown,257,135,823,355);
        a.c.restore();
    }
    void showdownRank(JSONArray ps,String feed){
        int id=-1;for(int i=0;i<ps.length();i++)if(ps.optJSONObject(i).optBoolean("spot"))id=ps.optJSONObject(i).optInt("id");
        float u=Math.min(1,(a.now-revealAt)/230f),offset=(1-u)*(1-u)*50;
        a.c.save();a.c.translate(-offset,0);bet.nameplate(id,feed);a.c.restore();
    }
}
