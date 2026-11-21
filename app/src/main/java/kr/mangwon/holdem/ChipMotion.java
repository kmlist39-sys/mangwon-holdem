package kr.mangwon.holdem;

import android.graphics.Bitmap;
import android.graphics.Rect;
import android.graphics.RectF;
import org.json.JSONObject;

/** Chips have separate flight, contact, resting and collection frames. */
final class ChipMotion {
    final Arena a;final StageArt stage;final Bitmap atlas;
    private final RectF dst=new RectF();private final Rect[] slots=new Rect[4];
    private final float[] pileX={314,333,352,370,382,326,347,367};
    private final float[] pileY={276,273,271,274,277,284,285,285};
    private final int[] pileColor={0,2,1,3,0,1,3,2};
    private final float[] scatterX={0,-12,12,4,-19,20,-7,12,-22,24,3,-4,17,-15};
    private final float[] scatterY={0,3,2,5,-2,-2,-4,-3,5,5,-5,8,7,7};
    // A stack breaks forward into the felt. These are individual resting places on the table.
    private final float[] spreadX={-34,14,-11,42,-46,29,2,-25,50,-4,36,-38,21,-18};
    private final float[] spreadY={-11,-25,-39,-8,-28,-44,-15,-52,-35,-59,-57,-43,-7,-24};
    private int hand=-1,paidPot=-1,sequence=-1,callStreet=-1,oldChips,newChips,seed;
    private boolean stackPush;
    private float fromLayers,toLayers;private long potAt,release,sweep;
    ChipMotion(StageArt stage){
        this.stage=stage;a=stage.a;atlas=a.image("bet-chips.png");
        if(atlas!=null)for(int i=0;i<4;i++)slots[i]=new Rect(i*atlas.getWidth()/4,0,(i+1)*atlas.getWidth()/4,atlas.getHeight());
    }
    static int callUnits(int paid,int bigBlind){
        if(paid<=0)return 0;
        int bySize=(int)Math.ceil(2*Math.log1p(paid/1600.0));
        int byBlinds=(int)Math.ceil(1.8*Math.log1p(paid/(double)Math.max(100,bigBlind)));
        return Math.min(14,Math.max(1,Math.max(bySize,byBlinds)));
    }
    static int potLayers(int amount){
        if(amount<=0)return 0;
        return Math.min(40,Math.max(1,(int)Math.round(Math.log1p(amount/250.0)*4.1)));
    }
    private float layers(long now){
        return fromLayers+(toLayers-fromLayers)*TitleSequence.out(TitleSequence.range(now-potAt,0,690));
    }
    void observe(JSONObject old,JSONObject g,long now){
        int h=g.optInt("hand"),pot=g.optInt("pot"),street=g.optInt("street");
        if(old==null||h!=hand){
            hand=h;sequence=-1;oldChips=newChips=0;sweep=0;callStreet=-1;
            paidPot=pot;fromLayers=toLayers=potLayers(pot);potAt=now-1000;
        }else if(pot!=paidPot){
            fromLayers=layers(now);toLayers=potLayers(pot);paidPot=pot;potAt=now;
        }
        JSONObject move=g.optJSONObject("lastHero");
        if(move!=null&&sequence!=move.optInt("seq")){
            sequence=move.optInt("seq");int eventStreet=move.optInt("street",street);
            String kind=move.optString("kind");
            if((kind.equals("call")||kind.equals("raise"))&&move.optInt("paid")>0){
                oldChips=sweep==0&&callStreet==eventStreet?Math.min(14,oldChips+newChips):0;
                newChips=callUnits(move.optInt("paid"),g.optInt("bb"));
                stackPush=kind.equals("raise")||move.optBoolean("allin")||move.optString("text").startsWith("올인");
                callStreet=eventStreet;release=old==null?now-2000:now;sweep=0;seed=sequence;
                if(old==null&&(street!=eventStreet||!g.optString("phase").equals("bet")))oldChips=newChips=0;
            }
        }
        String phase=g.optString("phase");
        if(newChips>0&&sweep==0&&(street!=callStreet||!phase.equals("bet")||g.optBoolean("showdownTitle")))
            sweep=Math.max(now+35,release+1670);
        if(sweep>0&&now-sweep>710){oldChips=newChips=0;sweep=0;}
    }
    void disc(float x,float y,float width,int color,int alpha){
        if(atlas==null){a.chip(x,y,1,0xffbd8763);return;}
        float height=width*.62f;dst.set(x-width/2,y-height/2,x+width/2,y+height/2);
        a.reset(-1);a.p.setAlpha(alpha);a.c.drawBitmap(atlas,slots[Math.floorMod(color,4)],dst,a.p);
    }
    void pot(){
        float visible=layers(a.now);int count=(int)Math.ceil(visible);
        for(int pile=0;pile<8;pile++){
            for(int chip=pile;chip<count;chip+=8){
                float f=Math.min(1,Math.max(0,visible-chip)),u=TitleSequence.smooth(f);
                float y=pileY[pile]-chip/8*2.15f;
                disc(pileX[pile]+(1-u)*7,Math.max(261,y-(1-u)*8),23.5f,pileColor[pile],(int)(255*u));
            }
        }
        a.stage.plate(291,291,401,316,0);
        a.mid("POT",313,307,10,a.gold);
        stage.bet.fitted(a.fmt(Math.max(0,paidPot)),361,308,66,15,a.paper,android.graphics.Paint.Align.CENTER);
    }
    void call(){
        if(newChips==0||a.deal.running())return;
        long age=a.now-release;
        float sourceX=684,sourceY=349,hx=sourceX-67,hy=sourceY-15;
        for(int j=0;j<oldChips+newChips;j++){
            boolean old=j<oldChips;int n=old?j:j-oldChips,index=Math.floorMod(n+(old?5:0),scatterX.length);
            if(!old&&stackPush)continue;
            float tx=sourceX+7+scatterX[index],ty=sourceY+scatterY[index]-(old?2:0);
            float delay=490+(newChips==1?0:n*180f/Math.max(1,newChips-1));
            float u=old?1:TitleSequence.range(age-delay,0,240);
            if(!old&&age<delay)continue;
            float slide=TitleSequence.out(u),bounce=(float)Math.sin(Math.PI*u);
            float x=sourceX+(tx-sourceX)*slide,y=sourceY+(ty-sourceY)*slide-bounce*(old?0:8+n%4);
            float collect=sweep==0?0:TitleSequence.smooth(TitleSequence.range(a.now-sweep-(n%5)*32,0,535));
            x+=(Arena.POT_X-x)*collect;y+=(Arena.POT_Y-y)*collect-(float)Math.sin(Math.PI*collect)*11;
            a.c.save();a.c.rotate((index%3-1)*9*(1-slide),x,y);
            disc(x,y,(old?16:18)-collect*3,(n+seed+(old?1:0))%4,(int)(255*(1-collect)));a.c.restore();
        }
        if(stackPush){pushStack(age,sourceX+58,sourceY);return;}
        if(age<0||age>1160)return;
        float enter=TitleSequence.out(TitleSequence.range(age,0,340)),leave=TitleSequence.smooth(TitleSequence.range(age,700,460));
        float y=hy+(1-enter)*125+leave*166;
        if(age<490)stage.rightHand(age<225?3:4,0,hx,y,134,150,255);
        else stage.rightHand(5,.18f,hx,y,134,150,255);
    }
    private void pushStack(long age,float anchorX,float anchorY){
        if(age<0)return;
        float enter=TitleSequence.out(TitleSequence.range(age,0,400));
        float approach=(1-enter)*125;
        float leave=TitleSequence.smooth(TitleSequence.range(age,610,550));
        if(age<1160){
            float left=anchorX-67+(1-enter)*18+leave*13;
            float top=anchorY-43+approach+leave*158;
            // Only the empty palm and sleeve. The upper 30% of this tile contains a baked-in stack.
            stage.rightHand(8,.30f,left,top,134,150,255);
        }
        for(int n=0;n<newChips;n++){
            int lane=n%3,level=n/3;
            float startX=anchorX+(lane-1)*11, startY=anchorY+(lane==1?2:0)-level*2.7f;
            int i=Math.floorMod(n+seed,spreadX.length);
            float slide=TitleSequence.range(age,410+lane*45+level*12,325);
            float travel=TitleSequence.out(slide);
            float x=startX+(anchorX+spreadX[i]-startX)*travel+(1-enter)*18;
            float y=startY+(anchorY+spreadY[i]-startY)*travel+approach-(float)Math.sin(Math.PI*slide)*(5+lane*2);
            float collect=sweep==0?0:TitleSequence.smooth(TitleSequence.range(a.now-sweep-(n%5)*32,0,535));
            x+=(Arena.POT_X-x)*collect;y+=(Arena.POT_Y-y)*collect-(float)Math.sin(Math.PI*collect)*11;
            disc(x,y,19-collect*3,n+seed,(int)(255*(1-collect)));
        }
    }
}
