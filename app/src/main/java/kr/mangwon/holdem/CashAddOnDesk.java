package kr.mangwon.holdem;

import android.graphics.Paint;
import org.json.JSONObject;

/** Buys chips for the next Dangsan hand against an exact, settled-stack quote. */
final class CashAddOnDesk {
    private final Arena a;
    private int amount;
    private String session="",token="";
    private int hand;
    private boolean submitting,shopping;

    CashAddOnDesk(Arena arena){a=arena;}
    private String wording(String ko,String en){return a.en?en:ko;}
    private JSONObject offer(){return a.g==null?null:a.g.optJSONObject("cashFunding");}
    private boolean rebuy(JSONObject q){return "rebought".equals(q.optString("kind"));}
    private String title(JSONObject q){return rebuy(q)?wording("리바인","Rebuy"):wording("애드온","Add chips");}
    boolean visible(){return offer()!=null;}
    void button(){
        JSONObject q=offer();if(q==null)return;
        float offset="fundOpen".equals(a.held)?2:0;
        a.stage.plate(645,445+offset,843,498+offset,0);
        for(int i=0;i<4;i++)a.stage.chips.disc(683,478+offset-i*2.3f,23,1,255);
        a.stage.bet.fitted(title(q),750,478+offset,111,18,a.paper,Paint.Align.CENTER);
        a.hit(645,445,843,501,"fundOpen");
    }
    private void open(){
        JSONObject q=offer();if(q==null)return;
        session=a.g.optString("sessionId");hand=a.g.optInt("hand");
        sync(q);a.panel="fundCash";
    }
    private void sync(JSONObject q){
        token=q.optString("token");amount=q.optBoolean("available")?q.optInt("min"):0;
        submitting=false;
    }
    void observe(JSONObject state){
        if(!"fundCash".equals(a.panel)&&!shopping)return;
        JSONObject q=offer();
        if(a.g==null||!session.equals(a.g.optString("sessionId"))||hand!=a.g.optInt("hand")){
            shopping=false;submitting=false;if("fundCash".equals(a.panel))a.panel="";return;
        }
        if(state.has("cashFundingApplied")){
            submitting=false;
            if(state.optBoolean("cashFundingApplied")){
                a.panel="";shopping=false;return;
            }
            a.note(wording("칩 추가 금액을 다시 확인해 주세요.","Check the amount and try again."));
        }
        if(q==null){if("fundCash".equals(a.panel))a.panel="";shopping=false;return;}
        if(!token.equals(q.optString("token")))sync(q);
    }
    boolean back(){
        if("fundCash".equals(a.panel)&&submitting)return true;
        if(!shopping||!"shop".equals(a.panel))return false;
        a.host.rewardAds.leave();shopping=false;
        if(offer()==null){a.panel="";return true;}
        open();a.invalidate();return true;
    }
    boolean action(String key){
        if("fundOpen".equals(key)){
            if(a.panel.isEmpty()&&a.g!=null&&handAtPress())open();
            return true;
        }
        if(!"fundCash".equals(a.panel)||!key.startsWith("fund"))return false;
        JSONObject q=offer();if(q==null||submitting)return true;
        switch(key){
        case "fundMin":amount=q.optInt("min");break;
        case "fundMax":amount=q.optInt("max");break;
        case "fundLess":amount=clamp(q,((amount-1)/1000)*1000);break;
        case "fundMore":amount=clamp(q,(amount/1000+1)*1000);break;
        case "fundShop":shopping=true;a.shopFromEntry=false;a.panel="shop";break;
        case "fundCommit":
            if(!q.optBoolean("available")||!token.equals(q.optString("token"))||!handAtPress())return true;
            submitting=true;a.host.command("cashAddOn","session",session,"hand",hand,"token",token,"amount",amount);
            break;
        }
        return true;
    }
    private boolean handAtPress(){return a.pressHand==a.g.optInt("hand")&&a.pressSession.equals(a.g.optString("sessionId"));}
    private int clamp(JSONObject q,int value){return Math.max(q.optInt("min"),Math.min(q.optInt("max"),value));}
    void slider(float x){
        JSONObject q=offer();if(q==null||submitting||!q.optBoolean("available"))return;
        int lo=q.optInt("min"),hi=q.optInt("max");
        float fraction=Math.max(0,Math.min(1,(x-375)/330));
        amount=fraction>=.998f?hi:clamp(q,Math.round((lo+(hi-lo)*fraction)/1000)*1000);
    }
    private void column(String label,String value,float x,boolean highlight){
        a.mid(label,x,222,14,a.muted);a.stage.bet.fitted(value,x,256,208,24,highlight?a.gold:a.paper,Paint.Align.CENTER);
    }
    private String points(long value){return a.fmt(value)+" P";}
    void draw(){
        JSONObject q=offer();if(q==null)return;
        a.hits.clear();a.box(0,0,1080,520,0,0xbf0a110e);
        a.box(169,30,911,491,12,0xff192922);
        a.c.save();a.c.clipRect(175,36,905,181);a.cashier.drawRack(175,-33,905,224);
        a.grad(175,36,905,181,0xfa141c16,0x26141c16,false);a.c.restore();
        a.stroke(169,30,911,491,12,0xff9f8152,1.5f);
        a.txt(a.g.optBoolean("double")?wording("당산  /  더블보드","DANGSAN  /  DOUBLE BOARD"):wording("당산  /  원보드","DANGSAN  /  SINGLE BOARD"),205,77,14,a.gold);
        a.text(title(q),203,125,37,a.paper,a.title,Paint.Align.LEFT);
        a.txt(wording("테이블 한도","Table maximum")+"  "+points(q.optLong("cap")),205,153,15,a.paper);
        if(!submitting)a.brass("닫기",808,46,79,"close");
        a.line(195,182,885,182,0xff756246,1);
        column(wording("내 지갑","My wallet"),points(q.optLong("wallet")),303,false);
        column(wording("현재 스택","Current stack"),points(q.optLong("stack")),540,false);
        column(wording("적용 후 스택","Stack after purchase"),points(q.optLong("stack")+amount),777,true);
        a.line(202,275,878,275,0xff574f3c,1);
        if(q.optBoolean("available")){
            a.mid(wording("추가할 칩","Chips to add")+"    "+points(amount),540,309,21,a.paper);
            a.brass("−",282,323,60,"fundLess");a.bar(375,339,705,"fundSlider",q.optInt("min"),q.optInt("max"),amount);a.brass("+",738,323,60,"fundMore");
            a.brass(wording("최소","Min"),353,369,87,"fundMin");
            a.brass(wording("최대","Max"),640,369,87,"fundMax");
            a.stage.bet.fitted(wording("적용 후 지갑","Wallet after")+"  "+points(q.optLong("wallet")-amount),540,390,189,13,a.muted,Paint.Align.CENTER);
            String confirm=submitting?wording("처리 중","Processing…"):title(q)+"   "+points(amount);
            a.key(confirm,373,419,334,51,"fundCommit",submitting?0:1);
        }else{
            String reason=rebuy(q)?wording("리바인에 필요한 최소 금액","Minimum rebuy"):wording("추가에 필요한 최소 금액","Minimum add-on");
            a.mid(reason+"   "+points(q.optInt("min")),540,326,19,a.gold);
            a.mid(wording("지갑 잔액이 부족합니다.","Your wallet has insufficient points."),540,365,17,a.paper);
            a.key(wording("포인트 충전","Open point shop"),390,414,300,53,"fundShop",1);
        }
    }
}
