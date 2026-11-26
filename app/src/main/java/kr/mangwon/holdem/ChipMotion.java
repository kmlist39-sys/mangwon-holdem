package kr.mangwon.holdem;

import android.graphics.Bitmap;
import android.graphics.Rect;
import android.graphics.RectF;
import java.util.ArrayList;
import org.json.JSONObject;

/** Right-hand wagers and their independent stacks on the felt. */
final class ChipMotion {
    private static final int REACH=510, RETREAT=735, END=1460, REST=1670;
    private static final int COLS=8, ROWS=9;
    private static final float[] PILE_X={314,333,352,370,382,326,347,367};
    private static final float[] PILE_Y={276,273,271,274,277,284,285,285};
    private static final int[] PILE_COLOR={0,2,1,3,0,1,3,2};
    private static final float[] SPREAD_X={-34,14,-11,42,-46,29,2,-25,50,-4,36,-38,21,-18};
    private static final float[] SPREAD_Y={-11,-25,-39,-8,-28,-44,-15,-52,-35,-59,-57,-43,-7,-24};
    final Arena a;
    final StageArt stage;
    final Bitmap atlas;
    private final Bitmap hand;
    private final RectF dst=new RectF();
    private final Rect[] slots=new Rect[4];
    private final ArrayList<Wager> wagers=new ArrayList<>();
    private final float[] mesh=new float[(COLS+1)*(ROWS+1)*2];
    private String session="";
    private int handNumber=-1, sequence=-1, paidPot=-1;
    private float fromLayers,toLayers;
    private long potAt;

    static final class Wager {
        final int street,count,seed;
        final long start;
        final boolean push;
        final float x,y;
        long sweep;
        Wager(int street,int count,int seed,boolean push,long start) {
            this.street=street;this.count=count;this.seed=seed;this.push=push;this.start=start;
            x=push?741:715;y=346;
        }
        long age(long now){return now-start;}
        boolean handVisible(long now){long age=age(now);return age>=0&&age<END;}
    }

    ChipMotion(StageArt stage){
        this.stage=stage;a=stage.a;atlas=a.image("bet-chips.png");
        if(atlas!=null)for(int i=0;i<4;i++)slots[i]=new Rect(i*atlas.getWidth()/4,0,(i+1)*atlas.getWidth()/4,atlas.getHeight());
        // Cell 11 contains only the open hand and jacket. The chip silhouettes in
        // the betting cels are baked into those images and must never be carried back.
        Bitmap hands=stage.hands;
        hand=hands==null?null:Bitmap.createBitmap(hands,hands.getWidth()*2/3,hands.getHeight()*3/4,
            hands.getWidth()-hands.getWidth()*2/3,hands.getHeight()-hands.getHeight()*3/4);
    }

    static int callUnits(int paid,int bigBlind){
        if(paid<=0)return 0;
        double value=Math.log1p(paid/3500.0);
        double blinds=Math.log1p(paid/(double)Math.max(100,bigBlind));
        return Math.min(72,Math.max(1,(int)Math.ceil(1.3*value*value+Math.min(6,blinds))));
    }
    static int potLayers(int amount){
        if(amount<=0)return 0;
        double value=Math.log1p(amount/1000.0);
        return Math.min(88,Math.max(1,(int)Math.ceil(2.4*value+.56*value*value)));
    }
    private float layers(long now){
        return fromLayers+(toLayers-fromLayers)*TitleSequence.out(TitleSequence.range(now-potAt,0,690));
    }

    void observe(JSONObject old,JSONObject game,long now){
        int nextHand=game.optInt("hand"),pot=game.optInt("pot"),street=game.optInt("street");
        String nextSession=game.optString("sessionId");
        boolean reset=old==null||nextHand!=handNumber||!nextSession.equals(session);
        if(reset){
            wagers.clear();sequence=-1;handNumber=nextHand;session=nextSession;
            paidPot=pot;fromLayers=toLayers=potLayers(pot);potAt=now-1000;
        }else if(pot!=paidPot){
            fromLayers=layers(now);toLayers=potLayers(pot);paidPot=pot;potAt=now;
        }
        JSONObject move=game.optJSONObject("lastHero");
        boolean betting="bet".equals(game.optString("phase"))&&!game.optBoolean("showdownTitle");
        if(move!=null&&move.optInt("hand",nextHand)==nextHand&&move.optInt("seq")>sequence){
            sequence=move.optInt("seq");
            String kind=move.optString("kind");
            int round=move.optInt("street",street),count=callUnits(move.optInt("paid"),game.optInt("bb"));
            if(count>0&&(kind.equals("call")||kind.equals("raise"))&&(!reset||betting&&round==street)){
                boolean push=kind.equals("raise")||move.optBoolean("allin")||move.optString("text").startsWith("올인")||count>=8;
                long start=now;
                if(reset)start-=END+300;
                else if(!wagers.isEmpty())start=Math.max(start,wagers.get(wagers.size()-1).start+END);
                wagers.add(new Wager(round,count,sequence,push,start));
            }
        }
        for(int i=wagers.size()-1;i>=0;i--){
            Wager wager=wagers.get(i);
            if(wager.sweep==0&&(!betting||wager.street!=street))wager.sweep=Math.max(now+35,wager.start+REST);
            if(wager.sweep>0&&now-wager.sweep>=710)wagers.remove(i);
        }
    }

    void disc(float x,float y,float width,int color,int alpha){
        if(alpha<=0)return;
        if(atlas==null){a.chip(x,y,1,0xffbd8763);return;}
        float height=width*.62f;dst.set(x-width/2,y-height/2,x+width/2,y+height/2);
        a.reset(-1);a.p.setAlpha(alpha);a.c.drawBitmap(atlas,slots[Math.floorMod(color,4)],dst,a.p);
    }
    void pot(){
        int balance=a.payouts.balance(paidPot,a.now);
        float visible=balance==paidPot?layers(a.now):potLayers(balance);
        int count=(int)Math.ceil(visible);
        for(int pile=0;pile<PILE_X.length;pile++){
            for(int chip=pile;chip<count;chip+=8){
                float f=Math.min(1,Math.max(0,visible-chip)),u=TitleSequence.smooth(f);
                int level=chip/8;
                float y=PILE_Y[pile]-level*2.35f;
                float offset=((chip*7+pile)%5-2)*.65f;
                float settle=(float)Math.sin(Math.PI*u);
                disc(PILE_X[pile]+offset+(1-u)*8,y-(1-u)*10-settle*2,23.5f,PILE_COLOR[pile],(int)(255*u));
            }
        }
        a.stage.plate(291,291,401,316,0);
        a.mid("POT",313,307,10,a.gold);
        stage.bet.fitted(a.fmt(Math.max(0,balance)),361,308,66,15,a.paper,android.graphics.Paint.Align.CENTER);
    }

    private static float ease(long age,int from,int duration){
        return TitleSequence.smooth(TitleSequence.range(age,from,duration));
    }
    private static float offX(long age){
        return (1-ease(age,0,REACH))*90+ease(age,RETREAT,END-RETREAT)*98;
    }
    private static float offY(long age){
        return (1-ease(age,0,REACH))*212+ease(age,RETREAT,END-RETREAT)*220;
    }

    private void palm(Wager w,long age){
        if(hand==null)return;
        float approach=ease(age,0,REACH),away=ease(age,RETREAT,END-RETREAT);
        float open=ease(age,450,315)*(1-away);
        float press=(float)Math.sin(Math.PI*ease(age,390,340));
        float angle=8-10*approach+14*away;
        int i=0;
        for(int row=0;row<=ROWS;row++){
            float v=row/(float)ROWS,tipWeight=Math.max(0,1-v/.55f);
            float sleeve=Math.max(0,(v-.68f)/.32f);
            for(int col=0;col<=COLS;col++){
                float u=col/(float)COLS;
                mesh[i++]=u*136+(u-.48f)*13*open*tipWeight-33*sleeve*sleeve;
                mesh[i++]=v*150+tipWeight*(5*press-3*open)+98*sleeve*sleeve;
            }
        }
        a.c.save();
        a.c.translate(w.x+offX(age),w.y+5+offY(age));
        a.c.rotate(angle);a.c.scale(-1,1);a.c.translate(-.50f*136,-.18f*150);
        a.reset(-1);a.c.drawBitmapMesh(hand,COLS,ROWS,mesh,0,null,0,a.p);a.c.restore();
    }

    private void stack(Wager w,long now){
        long age=w.age(now);
        if(age<0)return;
        int gripStacks=Math.min(6,1+w.count/6);
        int landingStacks=Math.min(SPREAD_X.length,2+w.count/2);
        for(int n=0;n<w.count;n++){
            int lane=n%gripStacks,level=n/gripStacks,pile=n%landingStacks;
            int index=Math.floorMod(pile*3+w.seed,SPREAD_X.length);
            int release=REACH+lane*15+(level%5)*9;
            float flight=TitleSequence.range(age,release,w.push?350:255);
            float travel=TitleSequence.out(flight);
            float fromX=w.x+(lane%3-(Math.min(3,gripStacks)-1)*.5f)*13;
            float fromY=w.y+(lane/3)*8-level*2.55f;
            int landedLevel=n/landingStacks;
            float fan=w.push?Math.min(1,.62f+w.count*.009f):.43f;
            float toX=w.x+SPREAD_X[index]*fan+((landedLevel*7+index)%5-2)*.8f;
            float toY=w.y+SPREAD_Y[index]*fan-landedLevel*2.4f;
            // Every chip has its own release instant. Afterward its position no longer consults the wrist.
            float x=fromX+(toX-fromX)*travel,y=fromY+(toY-fromY)*travel-(float)Math.sin(Math.PI*flight)*(4+lane);
            if(age<release){x+=offX(Math.min(age,REACH));y+=offY(Math.min(age,REACH));}
            float collect=w.sweep==0?0:ease(now-w.sweep,(n%5)*32,535);
            x+=(Arena.POT_X-x)*collect;y+=(Arena.POT_Y-y)*collect-(float)Math.sin(Math.PI*collect)*11;
            disc(x,y,(w.push?19:17)-collect*3,index+w.seed,Math.round(255*(1-collect)));
        }
    }

    /** Table chips render under the HUD once the wrist has left the frame. */
    void call(){
        if(a.deal.running())return;
        for(int i=0;i<wagers.size();i++){
            Wager w=wagers.get(i);
            if(!w.handVisible(a.now))stack(w,a.now);
        }
    }

    /** A single palm and its released chips pass in front of the wager ribbon together. */
    void hand(){
        if(a.deal.running())return;
        for(int i=0;i<wagers.size();i++){
            Wager w=wagers.get(i);
            if(w.handVisible(a.now)){palm(w,w.age(a.now));stack(w,a.now);}
        }
    }
}
