package kr.mangwon.holdem;

import android.graphics.Bitmap;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;

/** Consecutive compartments of one painted chip rack double as the shop selection targets. */
final class Cashier {
    private final Arena a;
    private final Bitmap rack;
    Cashier(Arena arena){a=arena;rack=a.image("cashier-rack.jpg");}

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
        a.txt("내 지갑   "+a.fmt(a.snapshot.optLong("wallet"))+" P",139,109,16,a.paper);
        if(a.host.rewardAds.privacyRequired())a.brass("광고 개인정보",730,48,128,"adPrivacy");
        a.grad(106,322,974,379,0x001b231d,0xff1b231d,true);
        for(int i=0;i<a.points.length;i++){
            float x=122+i*211,w=204;
            if(("pack"+i).equals(a.held))a.box(x,336,x+w,427,6,0xff354237);
            a.mid(a.shortP(a.points[i])+" P",x+w/2,365,25,a.paper);
            a.stage.plate(x+29,375,x+w-29,418,0);
            a.mid(a.fmt(a.prices[i])+"원",x+w/2,403,17,a.gold);
            a.hit(x,147,x+w,418,"pack"+i);
        }
        a.txt("모의 충전 · 실제 결제 0원",139,132,13,a.muted);
        RewardAdDesk ads=a.host.rewardAds;
        a.line(137,421,943,421,0xff6c6349,1);
        a.text("50,000 P",139,462,27,a.gold,a.nums,Paint.Align.LEFT);
        a.txt(ads.caption(),283,458,16,a.paper);
        if(ads.enabled())a.key(ads.button(),711,430,232,47,"rewardAd",1);
        else {
            a.stage.plate(711,430,943,477,0);
            a.mid(ads.button(),827,460,17,a.muted);
        }
    }
    private void detail(){
        int i=a.pack;
        if(rack!=null){
            int w=rack.getWidth(),h=rack.getHeight();
            Rect src=new Rect(i*w/4,(int)(.31*h),(i+1)*w/4,(int)(.84*h));
            a.reset(-1);a.c.drawBitmap(rack,src,new RectF(138,126,446,402),a.p);
        }
        a.text("충전 확인",138,80,28,a.paper,a.title,Paint.Align.LEFT);
        a.txt("받을 포인트",495,165,16,a.muted);
        a.text(a.shortP(a.points[i])+" P",491,225,47,a.gold,a.nums,Paint.Align.LEFT);
        a.txt("설정 가격   "+a.fmt(a.prices[i])+"원",496,270,18,a.paper);
        a.txt("실제 결제 금액   0원",496,302,18,a.paper);
        a.key("포인트 받기",489,346,264,56,"credit",1);
        a.txt("모의 충전",141,455,15,a.muted);
    }
}
