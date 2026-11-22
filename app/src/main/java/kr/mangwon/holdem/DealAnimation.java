package kr.mangwon.holdem;

import android.graphics.*;
import org.json.*;

/** Deals clockwise, one lap per hole card. The rules clock waits for the last landing. */
final class DealAnimation {
    final Arena view;
    Bitmap dealer;
    private int seats, cards, button, interval, lastRelease;
    private float elapsed,peekReach,peekLift;
    private long lastFrame;
    private static final int WINDUP = 240, FLIGHT = 380, TURN = 210;
    DealAnimation(Arena view){this.view=view;}
    void begin(JSONObject hand, boolean animate){
        seats=hand.optJSONArray("p").length();
        cards=hand.optBoolean("double")?3:2;
        button=hand.optInt("btn");
        interval=view.opt.optInt("speed",1)==2?112:160;
        elapsed=animate?0:end();
        peekReach=peekLift=0;
        lastRelease=-1;
        lastFrame=android.os.SystemClock.uptimeMillis();
    }
    private int end(){return WINDUP+(seats*cards-1)*interval+FLIGHT+TURN+100;}
    boolean running(){return seats>0&&elapsed<end();}
    void update(long now, boolean playing){
        long step=Math.max(0,Math.min(64,now-lastFrame));
        view.holeSqueeze.update(now,playing);
        if(playing&&!running()&&view.held.equals("peek")){
            peekReach=Math.min(1,peekReach+step/170f);
            peekLift=Math.min(.22f,view.peel/500f+.1f);
        }else peekReach=Math.max(0,peekReach-step/190f);
        if(running()&&playing){
            elapsed+=step;
            int released=Math.min(seats*cards-1,(int)Math.floor((elapsed-WINDUP)/interval));
            if(released>=0&&released!=lastRelease){lastRelease=released;if(view.opt.optBoolean("sound"))view.host.fx("deal",.36f);}
            if(!running())view.boardAt=now;
        }
        lastFrame=now;
    }
    static float clamp(float v){return Math.max(0,Math.min(1,v));}
    void draw(JSONArray players,int hero,JSONArray hole){
        if(seats!=players.length())return;
        final float ox=418,oy=92;
        if(running()){
            for(int k=0;k<4;k++)view.card(0,ox-19,oy-11-k*1.8f,31,43,-14,true);
            if(dealer!=null){
                float motion=Math.max(0,elapsed-WINDUP+interval*.52f)/interval;
                int frame=elapsed<WINDUP?0:Math.min(5,(int)((motion%1)*6));
                int w=dealer.getWidth()/3,h=dealer.getHeight()/2;
                view.reset(-1);
                view.c.drawBitmap(dealer,new Rect(frame%3*w,frame/3*h,(frame%3+1)*w,(frame/3+1)*h),new RectF(329,-16,449,104),view.p);
            }
        }
        for(int lap=0;lap<cards;lap++)for(int k=0;k<seats;k++){
            int seat=(button+1+k)%seats,order=lap*seats+k;
            JSONObject player=players.optJSONObject(seat);
            boolean mine=seat==hero;
            float age=elapsed-WINDUP-order*interval;
            if(age<0)continue;
            if(player.optBoolean("fold")||(!mine&&player.optJSONArray("hole").length()>0))continue;
            float[] pos=view.position((seat-hero+seats)%seats,seats);
            float targetX=mine?522-(cards-1)*23+lap*43+30:Arena.pocketCardX(pos[0],lap,cards);
            float targetY=mine?387+(lap==1?0:4):Arena.pocketY(pos[0],pos[1],!player.optString("action").isEmpty());
            float destAngle=mine?(lap-(cards-1)/2f)*10:(lap-(cards-1)/2f)*2;
            if(!mine&&age>=FLIGHT){
                view.card(0,targetX-Arena.POCKET_WIDTH/2,targetY-Arena.POCKET_HEIGHT/2,
                    Arena.POCKET_WIDTH,Arena.POCKET_HEIGHT,destAngle,true);
                continue;
            }
            float fraction=clamp(age/FLIGHT),travel=1-(1-fraction)*(1-fraction);
            float arc=(float)Math.sin(Math.PI*fraction);
            float x=ox+(targetX-ox)*travel,y=oy+(targetY-oy)*travel-arc*34;
            float tilt=destAngle*travel-20*(1-travel)+(seat%2==0?1:-1)*arc*24;
            float width=30+((mine?61:Arena.POCKET_WIDTH)-30)*travel,height=43+((mine?83:Arena.POCKET_HEIGHT)-43)*travel;
            if(arc>.02f){view.reset((int)(55*arc)<<24);view.c.drawOval(x-width*.6f,y+height*.24f+arc*16,x+width*.57f,y+height*.46f+arc*16,view.p);}
            float turn=clamp((age-FLIGHT)/TURN);
            boolean masked=mine&&view.holeSqueeze.covers(),front=mine&&!masked&&turn>=.5f;
            float squeeze=1;
            if(mine&&!masked){
                float flip=(float)Math.cos(turn*Math.PI);
                squeeze=Math.max(.035f,Math.abs(flip));
                y-=6*(float)Math.sin(turn*Math.PI);
                if(peekReach>0){
                    float lift=peekLift*TitleSequence.smooth(peekReach);
                    width*=1+lift;height*=1+lift*(cards==3?.25f:1);
                    if(cards==3){tilt*=1-.5f*peekReach;y-=peekReach;}else y-=8*peekReach;
                }
            }
            view.c.save();view.c.scale(squeeze,1,x,y);
            if(masked)view.holeSqueeze.card(hole==null?-1:hole.optInt(lap,-1),x-width/2,y-height/2,width,height,tilt,lap,cards);
            else view.card(mine?hole.optInt(lap):0,x-width/2,y-height/2,width,height,tilt,!front);
            view.c.restore();
        }
        boolean folded=players.optJSONObject(hero).optBoolean("fold");
        int heroOrder=(hero-button-1+seats)%seats;
        boolean landed=elapsed>=WINDUP+((cards-1)*seats+heroOrder)*interval+FLIGHT;
        view.holeSqueeze.controls(landed,folded,cards);
        if(!running()&&!folded&&!view.holeSqueeze.covers()){
            if(peekReach>0){
                float reach=TitleSequence.smooth(peekReach);
                float tipX=FoldMotion.startX(true,540,cards-1,cards)+17+(1-reach)*28;
                float tipY=(cards==3?420:414)+(1-reach)*107;
                view.c.save();view.c.rotate(-21,tipX,tipY);
                view.stage.rightHand(11,0,tipX-45,tipY-19,90,96,255);
                view.c.restore();
            }
            view.hit(438,350,657,420,"peek");
        }
    }
}
