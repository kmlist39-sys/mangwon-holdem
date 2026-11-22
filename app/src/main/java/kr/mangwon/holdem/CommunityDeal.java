package kr.mangwon.holdem;

import org.json.JSONArray;

/** The flop is a packet first. No face is drawn until all three backs have fanned out. */
final class CommunityDeal {
    private final Arena a;
    // Start the paper stroke before the midpoint so its edge snap lands as the face opens.
    private static final int FLIP_SOUND_LEAD=180;
    private static final int ROW_DELAY=160,FLOP_START=1420,FLOP_GAP=505,FLOP_FLIP=460;
    private static final int TURN_START=655,RIVER_START=925,LAST_FLIP=395;
    private int flipsHeard;
    private boolean restoring;
    CommunityDeal(Arena arena){a=arena;}
    void begin(boolean restored){
        flipsHeard=0;
        restoring=restored;
    }
    private int audibleFlips(int street,float elapsed){
        elapsed+=FLIP_SOUND_LEAD;
        if(street==1){
            float sinceFirst=elapsed-FLOP_START-FLOP_FLIP*.5f;
            return sinceFirst<0?0:Math.min(3,1+(int)(sinceFirst/FLOP_GAP));
        }
        float faceTime=(street==2?TURN_START:RIVER_START)+LAST_FLIP*.5f;
        return (street==2||street==3)&&elapsed>=faceTime?1:0;
    }
    void draw(JSONArray boards,boolean doubleBoard,float top){
        float w=doubleBoard?34:49,h=doubleBoard?44:69,step=w+6,start=540-(5*w+24)/2;
        int street=a.g.optInt("street");
        double hold=a.g.optDouble("boardHold",0);
        float duration=(street==1?3100:street==2?1160:1520)+(doubleBoard?280:0);
        float elapsed=hold>0?Math.max(0,duration-(float)(hold*1000)):duration+200;
        if(hold>0&&a.host.active&&a.live()&&a.host.talkingUntil<a.now)
            elapsed+=Math.min(180,Math.max(0,a.now-a.stage.accepted));
        if(!a.deal.running()){
            int due=audibleFlips(street,elapsed);
            if(doubleBoard)due+=audibleFlips(street,elapsed-ROW_DELAY);
            if(due>flipsHeard){
                int last=due-1;
                if(hold>0&&!restoring)
                    a.host.cardFx("flip",doubleBoard&&last%2==1?.37f:.50f);
                flipsHeard=due;
            }
            restoring=false;
        }
        for(int row=0;row<boards.length();row++){
            JSONArray cards=boards.optJSONArray(row);float y=top+(h+4)*row;
            for(int i=0;i<5;i++)a.stroke(start+i*step,y,start+i*step+w,y+h,4,0x44c6bea2,1);
            if(a.deal.running())continue;
            float rowTime=elapsed-row*ROW_DELAY;
            for(int i=0;i<cards.length();i++){
                int card=cards.optInt(i);float x=start+i*step;
                if(cards.length()==3&&rowTime<3100)flop(card,i,x,y,w,h,start+step,rowTime);
                else if(cards.length()>3&&i==cards.length()-1){
                    float arrive=TitleSequence.out(TitleSequence.range(rowTime,0,235));
                    float flip=TitleSequence.smooth(TitleSequence.range(rowTime,cards.length()==4?TURN_START:RIVER_START,LAST_FLIP));
                    render(card,x-45*(1-arrive),y-27*(1-arrive),w,h,-12*(1-arrive),flip);
                }else a.card(card,x,y,w,h,0,false);
            }
            if(doubleBoard)a.mid(row==0?"위":"아래",start-19,y+32,12,a.paper);
        }
    }
    private void flop(int card,int i,float slot,float y,float w,float h,float center,float ms){
        float arrivalTime=ms-112*i;if(arrivalTime<0)return;
        float arrive=TitleSequence.out(TitleSequence.range(arrivalTime,0,285));
        float fan=TitleSequence.smooth(TitleSequence.range(ms,790,435));
        float flip=TitleSequence.smooth(TitleSequence.range(ms,FLOP_START+i*FLOP_GAP,FLOP_FLIP));
        float pileX=center+(i-1)*3.4f,pileY=y-i*2;
        float x=pileX+(slot-pileX)*fan-(1-arrive)*109;
        float yy=pileY+(y-pileY)*fan-(1-arrive)*61;
        render(card,x,yy,w,h,(1-fan)*(i-1)*4-13*(1-arrive),flip);
    }
    private void render(int card,float x,float y,float w,float h,float tilt,float flip){
        float arc=(float)Math.sin(flip*Math.PI),width=.025f+.975f*Math.abs((float)Math.cos(flip*Math.PI));
        a.c.save();a.c.scale(width,1,x+w/2,y+h/2);a.card(card,x,y-arc*5,w,h,tilt,flip<.5f);a.c.restore();
    }
}
