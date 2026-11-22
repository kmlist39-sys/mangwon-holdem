package kr.mangwon.holdem;

import org.json.JSONArray;
import org.json.JSONObject;

/** A discarded hand settles on its own patch of felt before fading away. */
final class FoldMotion {
    private final Arena a;
    private final boolean[] hadBadge=new boolean[CastArt.COUNT+1];
    private final Pose pose=new Pose();

    FoldMotion(Arena arena){a=arena;}

    void observe(JSONObject old,JSONObject game,long now,boolean newHand){
        if(newHand)return; // Folded seats in a restored snapshot are already at rest.
        JSONArray before=old.optJSONArray("p"),after=game.optJSONArray("p");
        for(int i=0;i<Math.min(before.length(),after.length());i++){
            JSONObject prev=before.optJSONObject(i),next=after.optJSONObject(i);
            int id=next.optInt("id");
            if(id!=prev.optInt("id")||!next.optBoolean("fold")||prev.optBoolean("fold"))continue;
            a.foldStarted[id+1]=now;
            hadBadge[id+1]=!prev.optString("action").isEmpty();
        }
    }

    static final class Pose {
        float x,y,angle,w,h;
    }

    private static float range(float v,float from,float to){return Math.max(0,Math.min(1,(v-from)/(to-from)));}
    private static float ease(float f){return f*f*(3-2*f);}
    private static float mix(float a,float b,float u){return a+(b-a)*u;}
    private static float stopTime(int i){return 1587+91*i;}

    static int cardAlpha(boolean mine,int count,float age){
        float landed=stopTime(count-1)/(mine?1f:2.05f);
        float fadeStart=landed+160;
        return Math.round(255*(1-ease(range(age,fadeStart,fadeStart+650))));
    }
    static float startX(boolean mine,float seatX,int card,int count){
        return mine?552-(count-1)*23+43*card:Arena.pocketCardX(seatX,card,count);
    }
    static float startY(boolean mine,float seatX,float seatY,int card,boolean badge){
        return mine?(card==1?387:391):Arena.pocketY(seatX,seatY,badge);
    }

    // The far rim has a narrow strip above the boards. The wings have deeper empty felt.
    static float landX(boolean mine,float x,int card,int count){
        float spread=card-(count-1)*.5f;
        if(mine)return (count==2?402:385)+spread*25;
        float side=x<=540?-1:1;
        if(Arena.frontWing(x))return (x<540?246:834)+spread*12;
        if(Arena.endSeat(x))return x-side*34+spread*12;
        return x+side*33+spread*12;
    }
    static float landY(boolean mine,float x,float y,int card,int count){
        if(mine)return (count==2?352:354)+card*2;
        if(Arena.frontWing(x))return 351+card*2;
        if(Arena.endSeat(x))return 283+card*2;
        return Math.min(237,y+52)+card*1.5f;
    }

    static void sample(Pose p,boolean mine,float x,float y,boolean badge,int i,int count,float ms){
        float left=i-(count-1)*.5f;
        float sx=startX(mine,x,i,count),sy=startY(mine,x,y,i,badge);
        float tx=landX(mine,x,i,count),ty=landY(mine,x,y,i,count);
        float direction=tx>=sx?1:-1;
        float rotation=mine?-(56-22*i):direction*(58-i*18);
        float sourceW=mine?61:Arena.POCKET_WIDTH,sourceH=mine?83:Arena.POCKET_HEIGHT;
        float endW=mine?43:17,endH=mine?51:23;
        // AI hands use the same phases with a tighter wrist action.
        float time=mine?ms:ms*2.05f;
        float launch=910+i*43,detach=launch+137,stop=stopTime(i);
        if(time>=stop){p.x=tx;p.y=ty;p.angle=rotation;p.w=endW;p.h=endH;return;}
        float gather=ease(range(time,270,745)),wind=ease(range(time,745,910));
        float packetX=mine?550:Arena.pocketX(x,count),packetY=mine?385:sy;
        float gx=mix(sx,packetX+left*(mine?10:5),gather)-direction*(mine?5:2)*wind;
        float gy=mix(sy,packetY+left*2,gather)+(mine?2:-1)*wind;
        float initialAngle=left*(mine?10:2);
        p.angle=mix(initialAngle,left*6,gather)-direction*4*wind;
        p.w=mix(sourceW,mine?52:24,gather);
        p.h=mix(sourceH,mine?66:32,gather);
        if(time<launch){p.x=gx;p.y=gy;return;}

        float push=range(time,launch,detach);
        push=.13f*push+.87f*push*push;
        float impulse=mine?.22f:.30f;
        float rx=mix(gx,tx,impulse),ry=mix(gy,ty,impulse);
        p.x=mix(gx,rx,push);p.y=mix(gy,ry,push);
        p.angle+=direction*15*push;
        if(time<detach)return;

        float u=range(time,detach,stop);
        float glide=1-(float)Math.pow(1-u,2.45);
        p.x=mix(rx,tx,glide);p.y=mix(ry,ty,glide);
        // Let the three-card packet skim along the lower edge of the second board.
        float bow=mine&&count==3?12:mine?-4:-2;
        p.y+=bow*4*glide*(1-glide);
        p.angle=mix(p.angle,rotation,1-(float)Math.pow(1-u,2.1));
        p.w=mix(p.w,endW,glide);p.h=mix(p.h,endH,glide);
    }

    void draw(JSONArray seats,int hero,boolean doubleBoard){
        int count=doubleBoard?3:2;
        for(int seat=0;seat<seats.length();seat++){
            JSONObject player=seats.optJSONObject(seat);
            if(!player.optBoolean("fold"))continue;
            int id=player.optInt("id"),index=id+1;
            boolean mine=seat==hero,badge=hadBadge[index];
            float[] anchor=a.position((seat-hero+seats.length())%seats.length(),seats.length());
            float x=anchor[0],y=anchor[1];
            long born=a.foldStarted[index];
            if(born==0)continue;
            float ms=Math.max(0,a.now-born);
            int alpha=cardAlpha(mine,count,ms);
            if(alpha==0)continue;
            for(int i=0;i<count;i++){
                sample(pose,mine,x,y,badge,i,count,ms);
                float turn=mine?range(ms,110,290):1;
                a.c.save();a.c.translate(pose.x,pose.y);a.c.rotate(pose.angle);
                if(turn<1)a.c.scale(Math.max(.045f,Math.abs((float)Math.cos(turn*Math.PI))),1);
                if(turn<.5f){
                    a.card(player.optJSONArray("hole").optInt(i),-pose.w/2,-pose.h/2,pose.w,pose.h,0,false);
                }else{
                    a.c.scale(pose.w/Arena.POCKET_WIDTH,pose.h/Arena.POCKET_HEIGHT);
                    a.cardBack.drawPocket(a.c,-Arena.POCKET_WIDTH/2,-Arena.POCKET_HEIGHT/2,a.scale,alpha);
                }
                a.c.restore();
            }
            float time=mine?ms:ms*2.05f;
            if(time<1740)hand(mine,x,y,badge,count,time);
        }
    }

    private void hand(boolean mine,float x,float y,boolean badge,int count,float t){
        if(t<120)return;
        float enter=ease(range(t,120,630)),wind=ease(range(t,700,910));
        float flick=ease(range(t,900,1100)),release=ease(range(t,1085,1240));
        float retreat=ease(range(t,1205,1740));
        float tipX,tipY,rotation,dim;
        if(mine){
            tipX=632-56*enter+6*wind-46*flick-4*release+73*retreat;
            tipY=507-124*enter+2*wind-9*flick+6*release+181*retreat;
            rotation=-(6+19*enter-3*wind+14*flick-16*release-4*retreat);
            dim=180;
        }else{
            float px=Arena.pocketX(x,count),py=startY(false,x,y,0,badge);
            float tx=landX(false,x,0,count),ty=landY(false,x,y,0,count);
            float dx=tx-px,dy=ty-py;
            float handX=x,handY=y-4;
            tipX=mix(handX,px,enter)+dx*(-.045f*wind+.30f*flick+.05f*release);
            tipY=mix(handY,py,enter)+dy*(-.045f*wind+.30f*flick+.05f*release);
            tipX=mix(tipX,handX,retreat);tipY=mix(tipY,handY,retreat);
            // The wrist is rooted behind this seat; the thumb turns with the table.
            rotation=(float)Math.toDegrees(Math.atan2(handY-py,handX-px))-90;
            rotation+=(dx<0?-1:1)*(6*wind-15*flick+9*release);
            dim=Arena.frontWing(x)?63:51;
        }
        a.c.save();
        if(!mine){
            if(Arena.frontWing(x)){
                if(x<540)a.c.clipRect(x,0,1080,520);else a.c.clipRect(0,0,x,520);
            }else a.c.clipRect(0,y,1080,520);
        }
        a.c.translate(tipX,tipY);a.c.rotate(rotation);a.c.scale(dim,dim);
        // Continue the cuff using the original sleeve fabric, behind the cropped atlas hand.
        a.c.save();a.c.scale(-1,1);
        a.stage.bet.part(a.stage.hands,790/1086f,1433/1448f,945/1086f,1437/1448f,
            -.318f,.75f,.11f,1.56f,255);
        a.c.restore();
        a.stage.rightHand(11,0,-.497f,-.205f,1,1,255);
        a.c.restore();
    }
}
