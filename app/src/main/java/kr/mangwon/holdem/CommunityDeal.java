package kr.mangwon.holdem;

import org.json.JSONArray;

/** The flop is a packet first. No face is drawn until all three backs have fanned out. */
final class CommunityDeal {
    private final Arena a;
    private int lastHand=-1,lastStreet=-1,flipsHeard;
    CommunityDeal(Arena arena){a=arena;}
    void draw(JSONArray boards,boolean doubleBoard,float top){
        float w=doubleBoard?34:49,h=doubleBoard?44:69,step=w+6,start=540-(5*w+24)/2;
        int street=a.g.optInt("street"),hand=a.g.optInt("hand");
        double hold=a.g.optDouble("boardHold",0);
        float duration=(street==1?3100:street==2?1160:1520)+(doubleBoard?280:0);
        float elapsed=hold>0?Math.max(0,duration-(float)(hold*1000)):duration+200;
        if(hold>0&&a.host.active&&a.live()&&a.host.talkingUntil<a.now)
            elapsed+=Math.min(180,Math.max(0,a.now-a.stage.accepted));
        if(hand!=lastHand||street!=lastStreet){lastHand=hand;lastStreet=street;flipsHeard=0;}
        if(street==1&&hold>0&&!a.deal.running()&&a.panel.isEmpty()){
            int visible=elapsed<1650?0:Math.min(3,1+(int)((elapsed-1650)/505));
            if(visible>flipsHeard){if(a.opt.optBoolean("sound")&&a.host.active)a.host.fx("deal",.38f);flipsHeard=visible;}
        }
        for(int row=0;row<boards.length();row++){
            JSONArray cards=boards.optJSONArray(row);float y=top+(h+4)*row;
            for(int i=0;i<5;i++)a.stroke(start+i*step,y,start+i*step+w,y+h,4,0x44c6bea2,1);
            if(a.deal.running())continue;
            float rowTime=elapsed-row*160;
            for(int i=0;i<cards.length();i++){
                int card=cards.optInt(i);float x=start+i*step;
                if(cards.length()==3&&rowTime<3100)flop(card,i,x,y,w,h,start+step,rowTime);
                else if(cards.length()>3&&i==cards.length()-1){
                    float arrive=TitleSequence.out(TitleSequence.range(rowTime,0,235));
                    float flip=TitleSequence.smooth(TitleSequence.range(rowTime,cards.length()==4?655:925,395));
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
        float flip=TitleSequence.smooth(TitleSequence.range(ms,1420+i*505,460));
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
