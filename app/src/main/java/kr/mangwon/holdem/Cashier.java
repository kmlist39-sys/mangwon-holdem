package kr.mangwon.holdem;

import android.graphics.Bitmap;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import org.json.JSONArray;
import org.json.JSONObject;

/** Consecutive compartments of one painted chip rack double as the shop selection targets. */
final class Cashier {
    private final Arena a;
    private final Bitmap rack;
    private int selection;
    private long pendingPurchase;
    Cashier(Arena arena){a=arena;rack=a.image("cashier-rack.jpg");}
    void drawRack(float x,float y,float right,float bottom){a.photo(rack,x,y,right,bottom);}

    private JSONObject catalogue(){return a.snapshot.optJSONObject("cashier");}
    private JSONObject product(int index){
        JSONObject catalogue=catalogue();
        JSONArray packs=catalogue==null?null:catalogue.optJSONArray("packs");
        return packs==null?null:packs.optJSONObject(index);
    }

    boolean rewardEligible(){
        return a.snapshot.optBoolean("rewardEligible") &&
            a.snapshot.optLong("purchaseSequence")>=pendingPurchase &&
            a.snapshot.optLong("rewardedViews")>=a.host.getPreferences(0).getLong("completedAds",0);
    }

    void select(int index){
        if(!"shop".equals(a.panel)||product(index)==null)return;
        a.host.rewardAds.leave();
        selection=index;
        a.panel="confirm";
    }

    void purchase(){
        JSONObject item=product(selection);
        if(!"confirm".equals(a.panel)||item==null)return;
        long previous=a.snapshot.optLong("purchaseSequence");
        if(previous<pendingPurchase)return;
        pendingPurchase=previous+1;
        a.panel="shop";
        a.host.command("purchasePack","id",item.optString("id"),"sequence",pendingPurchase);
    }

    void draw(boolean confirm){
        a.hits.clear();a.box(0,0,1080,520,0,0xc00d120f);
        a.box(101,30,979,490,12,0xff1b231d);
        a.c.save();a.c.clipRect(106,35,974,485);
        if(confirm){detail();}else{selection();}
        a.c.restore();
        a.stroke(102,31,978,489,11,0xff725d42,1.2f);
        a.brass("닫기",864,48,87,"close");
    }
    private void selection(){
        a.photo(rack,106,47,974,377);
        a.grad(106,35,974,171,0xf319201a,0x0019201a,true);
        a.text("포인트 충전",137,79,28,a.paper,a.title,Paint.Align.LEFT);
        a.brass(a.host.studyBilling.storeLabel(),557,48,155,"gtoStore");
        a.txt("내 지갑   "+a.fmt(a.snapshot.optLong("wallet"))+" P",139,109,16,a.paper);
        if(a.host.rewardAds.privacyRequired())a.brass("광고 개인정보",730,48,128,"adPrivacy");
        a.grad(106,322,974,379,0x001b231d,0xff1b231d,true);
        a.txt("모의 충전 · 실제 결제 0원",139,132,13,a.muted);
        JSONObject catalogue=catalogue();
        if(catalogue==null)return;
        for(int i=0;i<4;i++){
            JSONObject item=product(i);
            if(item==null)continue;
            float x=122+i*211,w=204;
            String target="pack"+i;
            if(target.equals(a.held))a.box(x,336,x+w,418,6,0xff354237);
            a.mid(a.shortP(item.optInt("points"))+" P",x+w/2,365,25,a.paper);
            a.stage.plate(x+29,375,x+w-29,418,0);
            a.mid(a.fmt(item.optInt("price"))+"원",x+w/2,403,17,a.gold);
            a.hit(x,147,x+w,418,target);
        }
        JSONObject reward=catalogue.optJSONObject("reward");
        if(reward==null)return;
        RewardAdDesk ads=a.host.rewardAds;
        boolean eligible=a.snapshot.optBoolean("rewardEligible");
        boolean canWatch=eligible&&ads.enabled()&&rewardEligible();
        boolean korean="ko".equals(a.getResources().getConfiguration().getLocales().get(0).getLanguage());
        int threshold=reward.optInt("maxWallet"),points=reward.optInt("points");
        String limit=korean&&threshold%10000==0?threshold/10000+"만":a.fmt(threshold);
        String amount=korean&&points%10000==0?points/10000+"만":a.fmt(points);
        a.line(137,428,943,428,0xff6c6349,1);
        a.txt(a.getResources().getString(R.string.reward_watch_points,amount),139,451,17,a.gold);
        a.stage.bet.fitted(a.getResources().getString(R.string.reward_wallet_rule,limit),139,473,420,14,a.muted,Paint.Align.LEFT);
        if(eligible)a.stage.bet.fitted(ads.caption(),742,460,186,12,a.muted,Paint.Align.RIGHT);
        a.stage.plate(756,435,946,476,canWatch?1:0);
        String button=eligible?ads.button():a.getResources().getString(R.string.reward_over_limit);
        a.stage.bet.fitted(button,851,461,165,15,canWatch?a.ink:a.muted,Paint.Align.CENTER);
        if(canWatch)a.hit(750,429,952,481,"rewardAd");
    }
    private void detail(){
        JSONObject item=product(selection);
        if(item==null)return;
        int i=selection;
        if(rack!=null){
            int w=rack.getWidth(),h=rack.getHeight();
            Rect src=new Rect(i*w/4,(int)(.31*h),(i+1)*w/4,(int)(.84*h));
            a.reset(-1);a.c.drawBitmap(rack,src,new RectF(138,126,446,402),a.p);
        }
        a.text("충전 확인",138,80,28,a.paper,a.title,Paint.Align.LEFT);
        a.txt("받을 포인트",495,165,16,a.muted);
        a.text(a.shortP(item.optInt("points"))+" P",491,225,47,a.gold,a.nums,Paint.Align.LEFT);
        a.txt("설정 가격   "+a.fmt(item.optInt("price"))+"원",496,270,18,a.paper);
        a.txt("실제 결제 금액   0원",496,302,18,a.paper);
        a.key("포인트 받기",489,346,264,56,"credit",1);
        a.txt("모의 충전",141,455,15,a.muted);
    }
}
