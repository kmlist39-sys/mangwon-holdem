package kr.mangwon.holdem;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.Arrays;

/** Draws each opponent's anatomical right hand in that seat's local table coordinates. */
final class OpponentHands {
    private static final int CHECK=1, CALL=2, RAISE=3;
    private static final int[] CHIP_SIDE={0,-5,7,-13,4,12,-9,16,-17,3,-2,10};
    private static final int[] CHIP_FORWARD={0,5,2,-2,12,7,11,-3,6,16,21,18};
    private final StageArt stage;
    private final Arena arena;
    private final Motion[] moves=new Motion[50];
    private int lastEvent=-1;

    private static final class Motion {
        int pose,street,chips,priorChips;
        long start,collect;
    }

    OpponentHands(StageArt stage){this.stage=stage;arena=stage.a;}

    void observe(JSONObject old,JSONObject game,long now){
        JSONObject ev=game.optJSONObject("announcement");
        int seq=ev==null?-1:ev.optInt("seq");
        boolean fresh=old==null||old.optInt("hand")!=game.optInt("hand")
                ||!old.optString("sessionId").equals(game.optString("sessionId"));
        if(fresh||!arena.screen.equals("table")||!arena.host.active){
            Arrays.fill(moves,null);
            lastEvent=seq;
            return;
        }
        int street=game.optInt("street");
        if(ev!=null&&seq!=lastEvent){
            lastEvent=seq;
            int id=ev.optInt("id",-1);
            if(id>=0&&id<moves.length){
                String kind=ev.optString("kind");
                if(!kind.equals("fold")){
                    JSONArray players=game.optJSONArray("p");
                    for(int i=0;i<players.length();i++){
                        JSONObject player=players.optJSONObject(i);
                        if(player.optInt("id")!=id)continue;
                        JSONObject action=player.optJSONObject("move");
                        int paid=action==null?0:action.optInt("amount");
                        int eventStreet=action==null?street:action.optInt("street",street);
                        boolean check=kind.equals("check");
                        boolean push=kind.equals("allin")||kind.equals("raise")||
                                (action!=null&&action.optString("kind").equals("raise"));
                        Motion prev=moves[id],m=new Motion();
                        m.pose=check?CHECK:push?RAISE:CALL;
                        m.street=eventStreet;
                        m.start=now;
                        m.chips=check?0:Math.max(1,Math.min(12,ChipMotion.callUnits(paid,game.optInt("bb"))));
                        if(prev!=null&&prev.collect==0&&prev.street==eventStreet)
                            m.priorChips=Math.min(6,prev.chips+prev.priorChips);
                        moves[id]=m;
                        break;
                    }
                }
            }
        }
        boolean closed=!game.optString("phase").equals("bet");
        for(Motion m:moves)if(m!=null&&m.collect==0&&(closed||m.street!=street))
            m.collect=Math.max(now,m.start+960);
    }

    void draw(JSONArray players,int hero){
        if(arena.deal.running()||stage.hands==null)return;
        for(int i=0;i<players.length();i++){
            if(i==hero)continue;
            int id=players.optJSONObject(i).optInt("id",-1);
            if(id<0||id>=moves.length)continue;
            Motion m=moves[id];
            if(m==null)continue;
            long age=arena.now-m.start;
            if((m.collect!=0&&arena.now>m.collect+680)||(m.chips+m.priorChips==0&&age>1100)){
                moves[id]=null;continue;
            }
            float[] seat=arena.position((i-hero+players.length())%players.length(),players.length());
            drawSeat(m,seat[0],seat[1],age,id);
        }
    }

    private static float ease(long age,int from,int length){
        float t=Math.max(0,Math.min(1,(age-from)/(float)length));
        return t*t*(3-2*t);
    }

    private void drawSeat(Motion m,float x,float y,long age,int id){
        float dx=540-x,dy=(Arena.frontWing(x)?295:337)-y;
        float len=(float)Math.hypot(dx,dy);
        if(len==0)return;
        float nx=dx/len,ny=dy/len;
        // Screen space y grows downwards. This perpendicular is the seated player's right.
        float sideX=-ny,sideY=nx;
        float elbowX=x+sideX*18,elbowY=y+sideY*12+2;
        boolean check=m.pose==CHECK;
        boolean raise=m.pose==RAISE;
        float reach=Arena.frontWing(x)?80:64;
        if(raise)reach+=10;
        float enter=ease(age,0,raise?385:320);
        float release=ease(age,raise?390:345,raise?280:190);
        float leave=ease(age,check?640:635,check?330:415);
        float tap=check?(ease(age,270,66)-ease(age,341,74)+ease(age,469,57)-ease(age,535,80)):0;
        float along=(reach*enter+(check?0:4)*release)*(1-leave);
        float arc=7*(float)Math.sin(Math.PI*enter)*(1-leave);
        float tipX=elbowX+nx*along+sideX*arc;
        float tipY=elbowY+ny*along+sideY*arc+tap*3;
        float size=Arena.frontWing(x)?59:51;
        // Chips leave the fingertips at contact and remain in felt coordinates during recovery.
        for(int j=-m.priorChips;j<m.chips;j++){
            boolean prior=j<0;
            int slot=(j<0?-j:j)%CHIP_SIDE.length;
            long delay=slot%3*23L;
            float scatter=prior?1:ease(age-delay,raise?340:305,raise?330:225);
            float start=prior?1:enter;
            float span=raise?1:.46f;
            float forward=reach*start+7+scatter*CHIP_FORWARD[slot]*span-(prior?9:0);
            float side=(prior?-5:0)+CHIP_SIDE[slot]*scatter*span;
            float cx=elbowX+nx*forward+sideX*side;
            float cy=elbowY+ny*forward+sideY*side-1.25f*(slot%3)*(1-scatter);
            float collect=m.collect==0?0:ease(arena.now-m.collect-delay,0,555);
            cx+=(Arena.POT_X-cx)*collect;
            cy+=(Arena.POT_Y-cy)*collect;
            float opacity=prior?1:ease(age,80,150);
            stage.chips.disc(cx,cy,10.5f,(id+slot)%4,(int)(230*opacity*(1-collect)));
        }
        if(age<0||leave>=1)return;
        // Clip against the rail tangent: the cuff disappears back behind its own seat.
        float angle=(float)Math.toDegrees(Math.atan2(ny,nx))+90;
        arena.c.save();
        arena.c.translate(elbowX,elbowY);arena.c.rotate(angle);
        arena.c.clipRect(-size*1.2f,-reach-size,size*1.2f,1);
        arena.c.rotate(-angle);arena.c.translate(tipX-elbowX,tipY-elbowY);
        float followThrough=release*(1-leave);
        arena.c.rotate(angle+(check?tap*7:5*(1-enter)-(raise?8:4)*followThrough));
        // Use one bare right hand for the whole stroke. Contact changes wrist angle
        // and foreshortening; the separate chips stay at their point of release.
        arena.c.scale(1+(check?tap*.018f:followThrough*.025f),
                      1-(check?tap*.065f:followThrough*.035f));
        stage.rightHand(check?0:11,0,-size*.5f,-size*.13f,size,size,255);
        arena.c.restore();
    }
}
