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
    private static final int HISTORY=0, PASSES=1, REWARDS=2;
    private int request,page,pages,selected,tab,passPage;
    private boolean loading,loadFailed,entering,fromCabinet;
    private JSONObject pendingPass;

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
    void resetFunding(){pendingPass=null;entering=false;fromCabinet=false;}
    void answered(String change){if(!change.isEmpty())resetFunding();}
    boolean dismiss(){
        if(pendingPass==null||!a.panel.equals("entry"))return false;
        if(!entering){
            if(fromCabinet){a.panel="honors";tab=PASSES;read(page);}
            resetFunding();a.hits.clear();a.held="";a.invalidate();
        }
        return true;
    }
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
        tab=HISTORY;passPage=0;selected=0;a.panel="honors";a.hits.clear();a.held="";
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
        int heroRank=row!=null&&tab==HISTORY?row.optInt("rank"):best;
        if(heroRank>=1&&heroRank<=3)cup(heroRank,211,345,225,230);
        else if(row!=null&&tab==HISTORY){
            label(place(heroRank,row.optBoolean("tied")),212,252,216,54,GOLD,a.title,CENTER);
        }
        boolean specific=row!=null&&tab==HISTORY;
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
        tab(392,176,l("대회 입상 기록","Prize finishes"),tab==HISTORY,"honorsHistory");
        tab(609,176,l("보유 바이인권","My passes")+"  "+passCount(tickets),tab==PASSES,"honorsTickets");
        tab(826,176,l("우승 보상표","Winner's passes"),tab==REWARDS,"honorsRules");
        if(tab==HISTORY)drawRows();
        else if(tab==REWARDS||count(tickets)==0)drawRewards(tab==PASSES);
        else drawTickets(tickets);
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
    private void drawRewards(boolean empty){
        JSONArray entries=a.snapshot.optJSONArray("tournamentEntries");
        JSONObject mangwon=entries==null?null:entries.optJSONObject(0);
        JSONArray formats=mangwon==null?null:mangwon.optJSONArray("formats");
        if(count(formats)==0)return;
        String heading=empty?l("보유권이 없습니다 · 망원동 우승 시 받는 마포구 바이인권","No passes yet · Win a Mangwon event to earn a Mapo pass")
                            :l("망원동 대회 우승 시 받는 마포구 바이인권","Mapo entry passes awarded to Mangwon champions");
        label(heading,394,240,633,17,IVORY,a.title,LEFT);
        label(l("망원동 대회","Mangwon field"),394,273,119,14,SOFT,a.bold,LEFT);
        JSONObject first=formats.optJSONObject(0);
        JSONArray stakes=first==null?null:first.optJSONArray("offers");
        for(int j=0;j<count(stakes);j++){
            JSONObject offer=stakes.optJSONObject(j);
            if(offer!=null)label(a.shortP(offer.optInt("buyin"))+l(" P 바이인"," P buy-in"),645+232*j,273,220,16,GOLD,a.bold,CENTER);
        }
        a.line(392,279,1027,279,0xaa8f7753,1);
        for(int i=0;i<count(formats);i++){
            JSONObject format=formats.optJSONObject(i);if(format==null)continue;
            int y=283+65*i;
            label(format.optInt("tables")+l("테이블"," tables"),394,y+34,127,18,IVORY,a.bold,LEFT);
            JSONArray offers=format.optJSONArray("offers");
            for(int j=0;j<count(offers);j++){
                JSONObject offer=offers.optJSONObject(j),reward=offer==null?null:offer.optJSONObject("winnerTicket");
                if(reward!=null)voucher(reward,534+232*j,y,222,60,"");
            }
            if(i+1<count(formats))a.line(393,y+63,1027,y+63,0x5c827258,1);
        }
        label(l("상금과 함께 지급 · 지정 대회 첫 바이인 1회","Awarded with your prize money · One entry to the listed event"),
                394,498,630,14,SOFT,a.normal,LEFT);
    }
    private void drawTickets(JSONArray own){
        int n=count(own),lastPage=(n-1)/6;passPage=Math.min(passPage,lastPage);
        label(a.savedVenue(1)==null?l("참가할 대회의 바이인권을 선택하세요.","Select a pass to enter its tournament.")
                                  :l("마포구 대회 진행 중 · 선택하면 내 자리로 돌아갑니다.","Mapo tournament in progress · Select a pass to return to your seat."),
              392,236,635,16,SOFT,a.normal,LEFT);
        for(int i=passPage*6;i<Math.min(n,passPage*6+6);i++){
            JSONObject ticket=own.optJSONObject(i);if(ticket==null)continue;
            int col=i%2,row=(i%6)/2,x=394+321*col,y=239+82*row;
            String caption=a.en?"Available ×"+ticket.optInt("count"):"보유 "+ticket.optInt("count")+"장";
            voucher(ticket,x,y,295,78,caption);
            a.hit(x,y,x+295,y+78,"honorsUse"+i);
        }
        label(l("리바인은 해당 대회의 참가비로 결제합니다.","Rebuys use the tournament's entry fee."),394,502,414,14,SOFT,a.normal,LEFT);
        if(lastPage>0){
            if(passPage>0)a.brass("‹",847,475,52,"honorsPrevPass");
            label((passPage+1)+" / "+(lastPage+1),933,497,62,14,IVORY,a.nums,CENTER);
            if(passPage<lastPage)a.brass("›",970,475,52,"honorsNextPass");
        }
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
        boolean compact=caption.isEmpty();
        if(!compact)label(caption,cx,t+th*.31f,tw*.56f,Math.min(14,th*.20f),GOLD,a.bold,CENTER);
        label(a.shortP(buyin)+" P",cx,t+th*(compact?.49f:.61f),tw*.56f,Math.min(29,th*(compact?.34f:.28f)),IVORY,a.title,CENTER);
        label(l("마포구","Mapo")+" · "+tables+l("테이블"," tables"),cx,t+th*(compact?.77f:.82f),tw*.61f,Math.min(15,th*(compact?.21f:.17f)),IVORY,a.normal,CENTER);
    }
    void entryFooter(JSONObject offer){
        JSONObject ticket=matchingTicket();
        long wallet=a.snapshot.optLong("wallet");
        label(l("내 지갑  ","Wallet  ")+points(wallet),40,460,280,18,IVORY,a.bold,LEFT);
        if(ticket!=null){
            String available=a.en?"Matching entry passes   ×"+ticket.optInt("count"):"사용 가능한 바이인권   "+ticket.optInt("count")+"장";
            label(available,330,459,457,17,GOLD,a.bold,LEFT);
        }else{
            JSONObject bonus=offer.optJSONObject("winnerTicket");
            if(bonus!=null){
                String bonusText=l("우승 특전  마포구 ","Winner's pass  Mapo ")+bonus.optInt("tables")+l("테이블 · "," tables · ")+a.shortP(bonus.optInt("buyin"))+" P";
                label(bonusText,330,459,457,16,GOLD,a.bold,LEFT);
            }
        }
        boolean can=ticket!=null||wallet>=a.fee;
        String text=entering?l("입장 중…","Entering…"):can?a.shortP(a.fee)+l(" P 바이인 · 착석"," P · Enter"):l("포인트 충전","Get points");
        a.key(text,806,432,236,58,entering?"":can?"enter":"shop",1);
        if(pendingPass!=null)drawPrompt(wallet);
    }
    private void drawPrompt(long wallet){
        a.hits.clear();
        a.box(0,0,1080,520,0,0xaa080d0b);
        a.box(217,95,863,417,16,0xff17241f);
        a.stroke(217,95,863,417,16,0xff997e51,1.5f);
        label(l("바이인권을 사용하시겠습니까?","Use your entry pass?"),540,155,560,30,IVORY,a.title,CENTER);
        voucher(pendingPass,254,181,272,108,l("바이인 1회권","Single entry"));
        label(l("지갑 차감  0 P","Wallet charge  0 P"),555,216,252,23,GOLD,a.bold,LEFT);
        label(l("내 지갑  ","Wallet  ")+points(wallet),555,249,255,17,IVORY,a.nums,LEFT);
        label(l("사용하면 바이인권 1장이 차감됩니다.","Redeeming uses one matching pass."),555,276,255,14,SOFT,a.normal,LEFT);
        a.line(258,305,822,305,0x8b837456,1);
        String pay=wallet>=a.fee?(a.en?"Pay "+a.shortP(a.fee)+" P":a.shortP(a.fee)+" P로 결제"):l("포인트 부족","Insufficient points");
        a.key(l("취소","Cancel"),311,341,139,47,entering?"":"honorsCancel",0);
        a.key(pay,459,341,155,47,entering||wallet<a.fee?"":"honorsPay",0);
        if(wallet<a.fee)a.box(459,341,614,388,20,0x6e18201a);
        a.key(entering?l("입장 중…","Entering…"):l("바이인권 사용","Use pass"),624,341,181,47,entering?"":"honorsRedeem",1);
    }
    private void prompt(JSONObject ticket,boolean shortcut){
        pendingPass=ticket;fromCabinet=shortcut;a.hits.clear();a.held="";a.invalidate();
    }
    void enter(){
        if(entering||pendingPass!=null||a.enterExistingVenue(a.venue))return;
        JSONObject ticket=matchingTicket();
        if(ticket!=null)prompt(ticket,false);
        else if(a.snapshot.optLong("wallet")>=a.fee)submit("");
    }
    private void confirm(boolean redeem){
        if(pendingPass==null||entering||!a.panel.equals("entry"))return;
        if(a.enterExistingVenue(a.venue)){resetFunding();return;}
        JSONObject current=matchingTicket();
        if(current==null||!current.optString("id").equals(pendingPass.optString("id"))){
            dismiss();a.note(l("바이인권 보유 수량을 다시 확인해 주세요.","Please check your pass availability."));return;
        }
        if(!redeem&&a.snapshot.optLong("wallet")<a.fee)return;
        submit(redeem?current.optString("id"):"");
    }
    private void submit(String ticketId){
        entering=true;
        a.hits.clear();a.held="";a.invalidate();
        if(ticketId.isEmpty())a.host.command("enter","pub",a.venue,"buy",a.fee,"tables",a.selectedTournamentTables(),"fromLobby",true);
        else a.host.command("enter","pub",a.venue,"buy",a.fee,"tables",a.selectedTournamentTables(),"ticketId",ticketId,"fromLobby",true);
    }
    boolean action(String id){
        if(!id.startsWith("honors"))return false;
        if(id.equals("honorsOpen")){open();return true;}
        if(id.equals("honorsCancel")){dismiss();return true;}
        if(id.equals("honorsPay")||id.equals("honorsRedeem")){confirm(id.equals("honorsRedeem"));return true;}
        if(!a.panel.equals("honors"))return true;
        if(id.equals("honorsRetry"))read(page);
        else if(id.equals("honorsHistory"))tab=HISTORY;
        else if(id.equals("honorsTickets")){tab=PASSES;passPage=0;}
        else if(id.equals("honorsRules"))tab=REWARDS;
        else if(id.equals("honorsNextPass"))passPage++;
        else if(id.equals("honorsPrevPass"))passPage=Math.max(0,passPage-1);
        else if(id.equals("honorsNext")&&page+1<pages)read(page+1);
        else if(id.equals("honorsPrev")&&page>0)read(page-1);
        else if(id.startsWith("honorsRow"))selected=Integer.parseInt(id.substring("honorsRow".length()));
        else if(id.startsWith("honorsUse")){
            int index=Integer.parseInt(id.substring("honorsUse".length()));
            JSONArray own=groups(career);JSONObject t=own==null?null:own.optJSONObject(index);
            if(t!=null&&t.optInt("pub")==1){
                if(a.enterExistingVenue(1))return true;
                a.openVenue(1);a.tablesChoice[1]=t.optInt("tables");a.fee=t.optInt("buyin");prompt(t,true);
            }
        }
        a.invalidate();return true;
    }
}
