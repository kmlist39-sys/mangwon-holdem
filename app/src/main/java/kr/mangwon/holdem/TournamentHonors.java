package kr.mangwon.holdem;

import android.graphics.Bitmap;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.util.Log;
import org.json.JSONArray;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** The player's display cabinet and the funding choice on a tournament entry. */
final class TournamentHonors {
    private static final int IVORY=0xfff7ecd6, GOLD=0xffe5bd79, SOFT=0xffc6bdab;
    private static final int[] METALS={0xffefc87b,0xffc4d5db,0xffdfab83};
    private static final String[] MEDALS_KO={"금","은","동"}, MEDALS_EN={"Gold","Silver","Bronze"};
    private static final Paint.Align LEFT=Paint.Align.LEFT, CENTER=Paint.Align.CENTER, RIGHT=Paint.Align.RIGHT;
    private final Arena a;
    private final Rect source=new Rect();
    private final RectF dest=new RectF();
    private final Paint ink=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
    private SimpleDateFormat dates;
    private Bitmap room, pass, cups;
    private JSONObject career;
    private JSONArray awards;
    private int request,page,pages,selected;
    private boolean ticketTab,loading,loadFailed;
    private String useId="";
    private boolean entering;

    TournamentHonors(Arena a){this.a=a;}
    private String l(String ko,String en){return a.en?en:ko;}
    private String points(long n){return a.fmt(n)+" P";}
    private void label(String s,float x,float y,float width,float size,int color,Typeface face,Paint.Align align){
        ink.setColor(color);ink.setTypeface(face);ink.setTextAlign(align);ink.setTextSize(size);
        float actual=ink.measureText(s);
        if(actual>width)ink.setTextSize(size*width/actual);
        a.c.drawText(s,x,y,ink);
    }
    private JSONObject latest(){return a.snapshot.optJSONObject("career");}
    private static long amount(JSONObject obj,String key){return obj==null?0:obj.optLong(key);}
    private static JSONArray groups(JSONObject obj){return obj==null?null:obj.optJSONArray("tickets");}
    private static int count(JSONArray arr){return arr==null?0:arr.length();}
    private static int passCount(JSONArray groups){
        int total=0;
        for(int i=0;i<count(groups);i++){JSONObject group=groups.optJSONObject(i);if(group!=null)total+=group.optInt("count");}
        return total;
    }
    private boolean matches(JSONObject ticket){
        return ticket!=null&&ticket.optInt("pub",-1)==a.venue&&ticket.optInt("tables")==a.selectedTournamentTables()&&ticket.optInt("buyin")==a.fee;
    }
    private JSONObject matchingTicket(){
        JSONArray own=groups(latest());
        for(int i=0;i<count(own);i++){JSONObject t=own.optJSONObject(i);if(matches(t)&&t.optInt("count")>0)return t;}
        return null;
    }
    void resetFunding(){useId="";entering=false;}
    void answered(String change){if(!change.isEmpty())entering=false;}
    private Bitmap cups(){
        if(cups==null)cups=a.image("podium-trophies.webp");
        return cups;
    }
    Bitmap trophySheet(){return cups();}
    private void cup(int rank,float cx,float bottom,float maxWidth,float maxHeight){
        Bitmap b=cups();if(b==null||rank<1||rank>3)return;
        int w=b.getWidth()/3,h=b.getHeight();
        source.set((rank-1)*w,0,rank*w,h);
        float k=Math.min(maxWidth/w,maxHeight/h);
        dest.set(cx-w*k/2,bottom-h*k,cx+w*k/2,bottom);
        ink.setColor(0xffffffff);a.c.drawBitmap(b,source,dest,ink);
    }
    void lobbyButton(float x,float y){
        a.stage.plate(x,y,x+141,y+38,0);
        cup(1,x+28,y+32,27,29);
        label(l("나의 수상 이력","My awards"),x+84,y+25,92,15,IVORY,a.bold,CENTER);
        a.hit(x,y-3,x+141,y+41,"honorsOpen");
    }
    private void open(){
        if(room==null)room=a.image("honors-cabinet.webp");
        ticketTab=false;selected=0;a.panel="honors";a.hits.clear();a.held="";
        read(0);
    }
    private void read(int requestedPage){
        final int serial=++request;
        loading=true;loadFailed=false;awards=null;selected=0;
        career=latest();
        String query=GameActivity.args("op","awards","page",requestedPage).toString();
        a.host.worker.post(()->{
            try{
                JSONObject data=new JSONObject(GameActivity.call(query));
                a.host.ui.post(()->{
                    if(serial!=request||!a.panel.equals("honors"))return;
                    loading=false;awards=data.optJSONArray("awards");career=data.optJSONObject("career");
                    loadFailed=awards==null||career==null;page=data.optInt("page");pages=data.optInt("pages");
                    a.invalidate();
                });
            }catch(Exception e){
                Log.w("Mangwon","Reading tournament awards",e);
                a.host.ui.post(()->{if(serial==request){loading=false;loadFailed=true;a.invalidate();}});
            }
        });
        a.invalidate();
    }
    private String place(int rank,boolean tied){
        if(!a.en)return (tied?"공동 ":"")+rank+"위";
        int j=rank%10,k=rank%100;
        String suffix=k>=11&&k<=13?"th":j==1?"st":j==2?"nd":j==3?"rd":"th";
        return (tied?"T":"")+rank+suffix;
    }
    private String event(JSONObject row){
        String venue=row.optInt("pub")==0?l("망원동 데일리","Mangwon Daily"):l("마포구 홀덤대회","Mapo Championship");
        int tables=row.optInt("tables");return tables>0?venue+" · "+tables+l("테이블"," tables"):venue;
    }
    private String date(JSONObject row){
        long at=row.optLong("at");if(at<=0)return l("이전 대회","Previous event");
        if(dates==null)dates=new SimpleDateFormat("yyyy.MM.dd",Locale.ROOT);
        return dates.format(new Date(at));
    }
    void backdrop(int w,int h){
        a.c.drawColor(0xff17130e);
        a.cover(room,0,0,w,h);
    }
    void draw(){
        a.hits.clear();
        a.photo(room,0,0,1080,520);
        a.grad(0,0,1080,96,0x9e100e0c,0x00100e0c,true);
        a.grad(330,0,1080,520,0x44201710,0x971c1713,false);
        label(l("나의 수상 이력","My tournament awards"),391,54,483,31,IVORY,a.title,LEFT);
        a.brass(l("로비로","Lobby"),935,24,102,"close");
        JSONObject stats=career==null?latest():career;
        JSONArray medals=stats==null?null:stats.optJSONArray("podium"),tickets=groups(stats);
        JSONObject row=awards==null?null:awards.optJSONObject(selected);
        int best=0;
        for(int i=0;i<3;i++){if(medals!=null&&medals.optInt(i)>0){best=i+1;break;}}
        int heroRank=row!=null&&!ticketTab?row.optInt("rank"):best;
        if(heroRank>=1&&heroRank<=3)cup(heroRank,211,345,225,230);
        else if(row!=null&&!ticketTab){
            label(place(heroRank,row.optBoolean("tied")),212,252,216,54,GOLD,a.title,CENTER);
        }
        boolean specific=row!=null&&!ticketTab;
        String caption=specific?event(row):best>0?l("최고 성적","Personal best"):l("트로피 진열대","Trophy shelf");
        label(caption,212,403,209,15,SOFT,a.normal,CENTER);
        String summary=specific?place(heroRank,row.optBoolean("tied"))+"  ·  "+points(row.optLong("payout")):
            best>0?place(best,false):l("첫 입상을 기다리며","Awaiting your first podium");
        label(summary,212,429,209,21,IVORY,a.title,CENTER);
        for(int i=0;i<3;i++){
            float x=110+i*100;
            label((a.en?MEDALS_EN:MEDALS_KO)[i],x,452,83,12,METALS[i],a.normal,CENTER);
            label(""+(medals==null?0:medals.optInt(i)),x,474,82,21,IVORY,a.nums,CENTER);
        }
        metric(392,l("누적 상금","Lifetime prizes"),points(amount(stats,"prizes")),l("대회에서 획득한 상금","Tournament winnings"));
        metric(734,l("총 바이인","Total buy-ins"),points(amount(stats,"buyins")),l("포인트 결제 · 리바인 포함","Points paid, including rebuys"));
        a.line(711,86,711,143,0x665d5143,1);
        a.line(391,158,1034,158,0xa88f7753,1);
        tab(392,176,l("대회 입상 기록","Prize finishes"),!ticketTab,"honorsHistory");
        tab(618,176,l("보유 바이인권","Entry passes")+"  "+passCount(tickets),ticketTab,"honorsTickets");
        if(ticketTab)drawTickets(tickets);else drawRows();
    }
    private void metric(float x,String name,String value,String hint){
        label(name,x,86,296,15,GOLD,a.bold,LEFT);
        label(value,x,121,297,31,IVORY,a.nums,LEFT);
        label(hint,x,144,296,13,SOFT,a.normal,LEFT);
    }
    private void tab(float x,float y,String title,boolean active,String key){
        label(title,x+4,y+20,207,19,active?IVORY:SOFT,active?a.bold:a.normal,LEFT);
        if(active)a.line(x,y+31,x+204,y+31,GOLD,2);
        a.hit(x-3,y-12,x+211,y+33,key);
    }
    private void drawRows(){
        if(count(awards)==0){
            String text=loading?l("기록을 불러오는 중…","Loading your finishes…"):loadFailed?l("기록을 다시 불러와 주세요.","Could not read your finishes."):l("아직 입상 기록이 없습니다.","Your first prize finish belongs here.");
            label(text,694,326,600,22,IVORY,a.title,CENTER);
            if(loadFailed)a.brass(l("다시 시도","Try again"),630,349,128,"honorsRetry");
            else if(!loading)label(l("토너먼트에서 상금을 획득하면 기록됩니다.","Finish in the money at a tournament to add your first result."),694,358,594,15,SOFT,a.normal,CENTER);
            return;
        }
        for(int i=0;i<awards.length();i++){
            JSONObject row=awards.optJSONObject(i);if(row==null)continue;
            int rank=row.optInt("rank");float y=222+i*43;
            if(i==selected){a.box(388,y,1034,y+40,4,0xc1313229);a.box(388,y+4,391,y+36,1,GOLD);}
            else a.line(391,y+40,1028,y+40,0x674e493e,1);
            label(place(rank,row.optBoolean("tied")),406,y+26,63,18,rank>=1&&rank<=3?METALS[rank-1]:SOFT,a.bold,LEFT);
            label(event(row),476,y+17,310,16,IVORY,a.bold,LEFT);
            String stake=row.optInt("buyin")>0?"   ·   "+l("바이인 ","Buy-in ")+points(row.optLong("buyin")):"";
            label(date(row)+stake,476,y+34,307,12,SOFT,a.normal,LEFT);
            label(points(row.optLong("payout")),1015,y+25,227,19,GOLD,a.nums,RIGHT);
            a.hit(387,y,1034,y+41,"honorsRow"+i);
        }
        JSONObject sel=awards.optJSONObject(selected);
        if(sel!=null){
            boolean ticket=sel.optLong("ticketSpent")>0;
            String entry=ticket?l("참가권 사용 ","Pass used ")+points(sel.optLong("ticketSpent")):l("포인트 참가","Paid entry");
            JSONObject won=sel.optJSONObject("ticketAwarded");if(won!=null)entry+=l(" · 바이인권 획득"," · Pass won");
            String paid=sel.has("fees")&&!sel.isNull("fees")?"  ·  "+l("실결제 ","Points paid ")+points(sel.optLong("fees")):"";
            label(entry+paid,391,472,443,14,SOFT,a.normal,LEFT);
        }
        if(pages>1){
            if(page>0)a.brass("‹",847,448,52,"honorsPrev");
            label((page+1)+" / "+pages,933,470,62,14,IVORY,a.nums,CENTER);
            if(page+1<pages)a.brass("›",970,448,52,"honorsNext");
        }
    }
    private void drawTickets(JSONArray own){
        int n=count(own);
        if(n==0){
            voucher(12,100000,389,237,293,112,l("우승 보너스","Winner's bonus"));
            label(l("보유한 바이인권이 없습니다.","No entry passes yet."),703,273,318,21,IVORY,a.title,LEFT);
            label(l("망원동 5만 P 대회 우승 시","Win the 50K P Mangwon Daily"),703,308,318,15,SOFT,a.normal,LEFT);
            label(l("마포구 12테이블 10만 P권을 받습니다.","to earn a Mapo 12-table 100K P pass."),703,333,318,15,SOFT,a.normal,LEFT);
            return;
        }
        for(int i=0;i<n&&i<2;i++){
            JSONObject ticket=own.optJSONObject(i);if(ticket==null)continue;
            int y=222+i*121;
            voucher(ticket,389,y,283,110,l("바이인 1회권","Single entry"));
            boolean seated=a.savedVenue(ticket.optInt("pub"))!=null;
            label(l("보유 ","Available  ")+ticket.optInt("count")+l("장",""),689,y+32,335,20,IVORY,a.bold,LEFT);
            label(seated?l("진행 중인 마포구 대회가 있습니다.","You have an active Mapo tournament."):l("참가 시 지갑 차감 0 P","Wallet charge on entry  0 P"),689,y+58,335,15,SOFT,a.normal,LEFT);
            if(!seated)a.brass(l("이 입장권으로 참가","Enter with this pass"),686,y+70,225,"honorsUse"+i);
        }
        label(l("한 장으로 지정 대회에 1회 참가할 수 있습니다. 리바인은 포인트로 결제합니다.","Each pass covers one entry to its listed event. Rebuys use wallet points."),391,489,640,14,SOFT,a.normal,LEFT);
    }
    void voucher(JSONObject ticket,float x,float y,float w,float h,String caption){
        if(ticket==null)return;
        voucher(ticket.optInt("tables"),ticket.optInt("buyin"),x,y,w,h,caption);
    }
    private void voucher(int tables,int buyin,float x,float y,float w,float h,String caption){
        if(pass==null)pass=a.image("honors-pass.webp");
        float k=pass==null?1f:Math.min(w/pass.getWidth(),h/pass.getHeight());
        float tw=pass==null?w:pass.getWidth()*k,th=pass==null?h:pass.getHeight()*k;
        float l=x+(w-tw)/2,t=y+(h-th)/2;
        if(pass!=null)a.photo(pass,l,t,l+tw,t+th);
        else{a.box(l,t,l+tw,t+th,5,0xff244637);a.stroke(l+4,t+4,l+tw-4,t+th-4,4,GOLD,1);}
        float cx=l+tw*.64f;
        label(caption,cx,t+th*.31f,tw*.54f,Math.min(14,th*.20f),GOLD,a.bold,CENTER);
        label(a.shortP(buyin)+" P",cx,t+th*.61f,tw*.54f,Math.min(29,th*.28f),IVORY,a.title,CENTER);
        label(l("마포구","Mapo")+" · "+tables+l("테이블"," tables"),cx,t+th*.82f,tw*.54f,Math.min(15,th*.14f),IVORY,a.normal,CENTER);
    }
    void entryFooter(JSONObject offer){
        JSONObject ticket=matchingTicket();
        boolean requested=!useId.isEmpty(),use=requested&&ticket!=null&&useId.equals(ticket.optString("id"));
        boolean unavailable=requested&&!use;
        label(l("내 지갑  ","Wallet  ")+points(a.snapshot.optLong("wallet")),40,460,ticket==null?363:252,18,IVORY,a.bold,LEFT);
        if(ticket!=null||unavailable){
            a.key(l("포인트","Points"),293,431,106,38,"honorsPoints",use?0:1);
            a.key(l("바이인권 ","Pass  × ")+(ticket==null?0:ticket.optInt("count"))+l("장",""),404,431,155,38,ticket==null?"":"honorsPass",use?1:0);
            String charge=unavailable?l("결제 수단을 선택하세요.","Select a payment method."):l("지갑 차감  ","Wallet charge  ")+points(use?0:a.fee);
            label(charge,573,455,205,16,use?GOLD:IVORY,a.bold,LEFT);
        }else{
            JSONObject bonus=offer.optJSONObject("winnerTicket");
            if(bonus!=null){
                String bonusText=l("우승 특전  마포구 ","Winner's pass  Mapo ")+bonus.optInt("tables")+l("테이블 · "," tables · ")+a.shortP(bonus.optInt("buyin"))+" P";
                label(bonusText,330,459,457,16,GOLD,a.bold,LEFT);
            }
        }
        boolean can=use||a.snapshot.optLong("wallet")>=a.fee;
        String text=entering?l("입장 중…","Entering…"):unavailable?l("사용한 입장권","Pass unavailable"):use?l("바이인권으로 착석","Use pass & enter"):can?a.shortP(a.fee)+l(" P 바이인 · 착석"," P · Enter"):l("포인트 충전","Get points");
        a.key(text,806,432,236,58,entering||unavailable?"":can?"enter":"shop",unavailable?0:1);
    }
    void enter(){
        if(entering)return;
        if(a.enterExistingVenue(a.venue))return;
        if(!useId.isEmpty()){
            JSONObject ticket=matchingTicket();
            if(ticket==null||!useId.equals(ticket.optString("id"))){
                resetFunding();a.note(l("입장권 보유 수량을 확인해 주세요.","Check your pass availability."));return;
            }
        }
        entering=true;
        if(useId.isEmpty())a.host.command("enter","pub",a.venue,"buy",a.fee,"tables",a.selectedTournamentTables(),"fromLobby",true);
        else a.host.command("enter","pub",a.venue,"buy",a.fee,"tables",a.selectedTournamentTables(),"ticketId",useId,"fromLobby",true);
    }
    boolean action(String id){
        if(!id.startsWith("honors"))return false;
        if(id.equals("honorsOpen")){open();return true;}
        if(id.equals("honorsPoints")||id.equals("honorsPass")){
            if(a.panel.equals("entry")&&!entering){JSONObject t=matchingTicket();useId=id.equals("honorsPass")&&t!=null?t.optString("id"):"";}
            return true;
        }
        if(!a.panel.equals("honors"))return true;
        if(id.equals("honorsRetry"))read(page);
        else if(id.equals("honorsHistory"))ticketTab=false;
        else if(id.equals("honorsTickets"))ticketTab=true;
        else if(id.equals("honorsNext")&&page+1<pages)read(page+1);
        else if(id.equals("honorsPrev")&&page>0)read(page-1);
        else if(id.startsWith("honorsRow"))selected=Integer.parseInt(id.substring("honorsRow".length()));
        else if(id.startsWith("honorsUse")){
            int index=Integer.parseInt(id.substring("honorsUse".length()));
            JSONArray own=groups(career);JSONObject t=own==null?null:own.optJSONObject(index);
            if(t!=null&&t.optInt("pub")==1&&a.savedVenue(1)==null){
                a.openVenue(1);a.tablesChoice[1]=t.optInt("tables");a.fee=t.optInt("buyin");useId=t.optString("id");
            }
        }
        a.invalidate();return true;
    }
}
