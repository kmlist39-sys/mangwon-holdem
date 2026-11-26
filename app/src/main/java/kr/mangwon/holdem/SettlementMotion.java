package kr.mangwon.holdem;

import android.graphics.Paint;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Locale;

/** Each board sends its own awarded chips. Refunds and the house cut never enter the animation. */
final class SettlementMotion {
    private static final int LEAD=120, TRAVEL=720, STAGGER=19, BOARD_INTERVAL=1550, END=1510;
    private static final int GOLD=0xffefc47c, TEAL=0xffa4d6c5;
    private final Arena a;
    private final ArrayList<Wave> waves=new ArrayList<>();
    private String observed="";
    private long started;
    private boolean distribution;
    private int distributable;

    SettlementMotion(Arena arena){a=arena;}

    static final class Award {
        final int id,position,amount;
        final String label,name;
        int baseStack=-1;
        Award(int id,int position,int amount,String name){
            this.id=id;this.position=position;this.amount=amount;
            this.name=name;label=String.format(Locale.US,"+%,d",amount);
        }
    }
    static final class Wave {
        final int pot,board,seats;
        final boolean doubleBoard;
        final String caption,english;
        String recipients="";
        int total,potNet;
        long heard=-1,launch=-1;
        boolean sounded;
        final ArrayList<Award> awards=new ArrayList<>();
        Wave(int pot,int board,int seats,boolean doubleBoard){
            this.pot=pot;this.board=board;this.seats=seats;this.doubleBoard=doubleBoard;
            String name=pot==0?"메인 팟":pot==1?"사이드 팟":pot==2?"서드 팟":"사이드 팟 "+pot;
            caption=doubleBoard?(board==0?"위":"아래")+"  ·  "+name:name;
            String en=pot==0?"Main pot":"Side pot "+pot;
            english=doubleBoard?en+"  ·  "+(board==0?"Upper board":"Lower board"):en;
        }
    }
    static boolean handles(JSONObject game){
        JSONObject result=game==null?null:game.optJSONObject("settlement");
        JSONArray pots=result==null?null:result.optJSONArray("pots");
        return result!=null&&result.optBoolean("contestedShowdown")&&pots!=null&&pots.length()>0
                &&(game.optBoolean("double")||pots.length()>1);
    }
    private static int indexOf(JSONArray items,int id,boolean objects){
        if(items!=null)for(int i=0;i<items.length();i++){
            JSONObject entry=objects?items.optJSONObject(i):null;
            int found=objects?(entry==null?Integer.MIN_VALUE:entry.optInt("id",Integer.MIN_VALUE)):items.optInt(i,Integer.MIN_VALUE);
            if(found==id)return i;
        }
        return -1;
    }
    static ArrayList<Wave> sequence(JSONObject game) {
        ArrayList<Wave> payout=new ArrayList<>();
        if(!handles(game))return payout;
        JSONArray seats=game.optJSONArray("p");
        int hero=indexOf(seats,-1,true);
        if(hero<0||seats.length()<2||seats.length()>9)return payout;
        JSONArray pots=game.optJSONObject("settlement").optJSONArray("pots");
        for(int p=0;p<pots.length();p++){
            JSONObject pot=pots.optJSONObject(p);if(pot==null)continue;
            JSONArray boards=pot.optJSONArray("boards"),eligible=pot.optJSONArray("eligible");
            if(boards==null)continue;
            for(int wanted=0;wanted<boards.length();wanted++){
                JSONObject board=boards.optJSONObject(wanted);
                if(board==null)continue;
                if(board.optInt("index",wanted)!=wanted){
                    board=null;
                    for(int j=0;j<boards.length();j++){
                        JSONObject other=boards.optJSONObject(j);
                        if(other!=null&&other.optInt("index",-1)==wanted){board=other;break;}
                    }
                }
                if(board==null)continue;
                Wave wave=new Wave(pot.optInt("index",p),wanted,seats.length(),boards.length()==2);
                wave.potNet=pot.optInt("net");
                JSONArray winners=board.optJSONArray("winners");
                for(int i=0;winners!=null&&i<winners.length();i++){
                    JSONObject who=winners.optJSONObject(i);if(who==null)continue;
                    int id=who.optInt("id",Integer.MIN_VALUE),amount=who.optInt("amount");
                    if(id==Integer.MIN_VALUE||amount<=0)continue;
                    int index=indexOf(seats,id,true);
                    if(index<0||(eligible!=null&&indexOf(eligible,id,false)<0))continue;
                    wave.awards.add(new Award(id,(index-hero+seats.length())%seats.length(),amount,seats.optJSONObject(index).optString("name")));
                    wave.total+=amount;
                }
                if(!wave.awards.isEmpty())payout.add(wave);
            }
        }
        return payout;
    }
    void observe(JSONObject old,JSONObject game,long now){
        if(!handles(game)){waves.clear();distribution=false;distributable=0;return;}
        JSONObject settlement=game.optJSONObject("settlement");
        String session=game.optString("sessionId");
        int hand=game.optInt("hand");
        String id=session+"/"+hand+"/"+settlement.optInt("hand",hand);
        distribution=game.optBoolean("double")&&game.optBoolean("cash");
        if(id.equals(observed))return;
        observed=id;waves.clear();distributable=0;
        if(old==null||old.optInt("hand",-1)!=hand||!session.equals(old.optString("sessionId"))
                ||old.optJSONObject("settlement")!=null||"done".equals(old.optString("phase")))return;
        waves.addAll(sequence(game));started=now;
        JSONArray prior=old.optJSONArray("p");
        for(Wave wave:waves){
            distributable+=wave.total;
            StringBuilder winners=new StringBuilder();
            for(Award award:wave.awards){
                int index=indexOf(prior,award.id,true);
                if(index>=0)award.baseStack=prior.optJSONObject(index).optInt("stack",-1);
                if(winners.length()>0)winners.append("   /   ");
                winners.append(a!=null?a.tr(award.name):award.name).append("  ").append(award.label);
                if(distribution&&wave.potNet>0){
                    long numerator=100L*award.amount;
                    winners.append(" (");
                    if(numerator%wave.potNet!=0)winners.append("≈");
                    winners.append(Math.round(numerator/(double)wave.potNet)).append("%)");
                }
            }
            wave.recipients=winners.toString();
        }
    }
    void heard(String id,int pot,int board,long now){
        if(id.equals(observed))for(Wave w:waves)if(w.pot==pot&&w.board==board&&w.heard<0){w.heard=now;return;}
    }
    private void schedule(long now){
        long earliest=started+LEAD;
        for(Wave w:waves){
            if(w.launch<0){
                boolean waiting=distribution&&a!=null&&a.host.dealerVoice.expects(observed,w.pot,w.board);
                if(waiting&&w.heard<0&&now<earliest+16000)return;
                w.launch=Math.max(earliest,w.heard);
            }
            earliest=w.launch+BOARD_INTERVAL;
        }
    }
    boolean animating(long now){
        if(waves.isEmpty()||now<started)return false;
        schedule(now);long last=waves.get(waves.size()-1).launch;
        return last<0||now<last+END;
    }
    boolean presenting(long now){return distribution&&animating(now);}
    private static int arrival(int winner){return TRAVEL+150+winner*STAGGER;}
    int balance(int ordinary,long now){
        if(!distribution)return ordinary;
        schedule(now);int left=distributable;
        for(Wave w:waves)if(w.launch>=0)for(int i=0;i<w.awards.size();i++){
            if(now-w.launch>=arrival(i))left-=w.awards.get(i).amount;
        }
        return left;
    }
    int stack(JSONObject seat,long now){
        int stack=seat.optInt("stack"),won=seat.optInt("won");
        if(won<=0||!distribution||waves.isEmpty())return stack;
        int id=seat.optInt("id"),pending=0;
        for(Wave w:waves)for(int i=0;i<w.awards.size();i++){
            Award win=w.awards.get(i);
            if(win.id!=id)continue;
            // A wash or a separate add-on has its own credit animation.
            if(win.baseStack>=0&&win.baseStack+won!=stack)return stack;
            if(w.launch<0||now-w.launch<arrival(i))pending+=win.amount;
        }
        return Math.max(0,stack-pending);
    }
    private Wave current(long now){
        Wave latest=null;
        for(Wave w:waves){if(w.launch<0||now<w.launch)break;latest=w;}
        return latest;
    }
    boolean banner(float x,float width,long now){
        if(!presenting(now))return false;
        Wave w=current(now);
        String heading=w==null?(a.en?"Distributing pot":"팟 배분"):(a.en?w.english:w.caption);
        String bottom=w==null?(a.en?"Net pot  ":"지급 팟  ")+a.fmt(distributable):w.recipients;
        a.stage.bet.fitted(heading,x,476,width,17,w!=null&&w.board==1?TEAL:GOLD,Paint.Align.CENTER);
        a.stage.bet.fitted(bottom,x,500,width,17,a.paper,Paint.Align.CENTER);
        return true;
    }
    void rim(){
        if(!presenting(a.now))return;
        Wave w=current(a.now);
        if(w==null)return;
        float age=a.now-w.launch,f=ease(age/155f)*(1-ease((age-END+290)/290f));
        if(f<=0)return;
        float y=Arena.BOARD_TOP+w.board*48;
        int tint=w.board==0?GOLD:TEAL;
        a.stroke(439,y-3,641,y+47,6,alpha(tint,.17f*f),3.4f);
        a.stroke(440,y-2,640,y+46,5,alpha(tint,.8f*f),1);
    }
    private static float unit(float x){return Math.max(0,Math.min(1,x));}
    private static float ease(float x){x=unit(x);return x*x*(3-2*x);}
    private static int alpha(int rgb,float opacity){return ((int)(255*unit(opacity))<<24)|(rgb&0xffffff);}
    private float endX(Award award,float[] pos){
        return award.id==-1?322:pos[0]+(pos[0]<520?45:pos[0]>560?-45:27);
    }
    private float endY(Award award,float[] pos){
        return award.id==-1?414:Arena.plateY(pos[0],pos[1])+29;
    }
    void chips(){
        if(!animating(a.now))return;
        for(int w=0;w<waves.size();w++){
            Wave wave=waves.get(w);if(wave.launch<0)break;long age=a.now-wave.launch;
            if(age<0||age>1200)continue;
            if(!wave.sounded&&age>=TRAVEL*.8f){
                wave.sounded=true;
                if(distribution&&age<TRAVEL+150&&a.opt.optBoolean("sound")&&a.host.active&&a.panel.isEmpty())a.host.fx("chip",.37f);
            }
            for(int i=0;i<wave.awards.size();i++){
                Award award=wave.awards.get(i);float[] seat=a.position(award.position,wave.seats);
                float tx=endX(award,seat),ty=endY(award,seat);
                int units=ChipMotion.potLayers(award.amount);
                int chips=Math.min(7,Math.max(2,(units+6)/7));
                for(int k=0;k<chips;k++){
                    float t=(age-i*STAGGER-k*20-65)/(float)TRAVEL;
                    if(t<=0||t>=1)continue;
                    float f=ease(t),s=1-f;
                    float sx=Arena.POT_X+((k*13)%27-13),sy=Arena.POT_Y-2*k;
                    // The outer arc follows the corresponding edge of the board; card faces are painted above it.
                    float guideY=wave.doubleBoard&&wave.board==1?367:200;
                    float x=s*s*s*sx+3*s*s*f*(sx+27)+3*s*f*f*(tx*.72f+sx*.28f)+f*f*f*tx;
                    float y=s*s*s*sy+3*s*s*f*guideY+3*s*f*f*(guideY*.56f+ty*.44f)+f*f*f*ty;
                    y-=(float)Math.sin(Math.PI*f)*(5+k*1.4f);
                    float fade=ease(t/.07f)*(1-ease((t-.94f)/.06f));
                    float px=x+(k%3-1)*7*f,py=y-(k%2)*3*f,size=15+3.2f*(float)Math.sin(Math.PI*f);
                    int height=Math.min(5,Math.max(1,(units+chips-k-1)/chips));
                    for(int layer=0;layer<height;layer++)
                        a.stage.chips.disc(px,py-layer*1.45f,size,(k+w)%4,(int)(245*fade));
                }
            }
        }
    }
    void labels(){
        if(!animating(a.now))return;
        for(int w=0;w<waves.size();w++){
            Wave wave=waves.get(w);if(wave.launch<0)break;long age=a.now-wave.launch;
            if(age<0||age>END)continue;
            int tint=wave.board==1?TEAL:GOLD;
            if(!distribution&&age<1000){
                float opacity=ease(age/160f)*(1-ease((age-775)/225f));
                float x=Arena.POT_X,y=249;
                a.box(x-45,y-13,x+45,y+3,6,alpha(0x162b24,opacity*.92f));
                a.text(a.en?wave.english:wave.caption,x,y,11,alpha(tint,opacity),a.bold,Paint.Align.CENTER);
            }
            for(int i=0;i<wave.awards.size();i++){
                Award award=wave.awards.get(i);
                float local=age-arrival(i)+85;
                if(local<=0||local>=805)continue;
                float opacity=ease(local/160)*(1-ease((local-565)/240));
                float[] seat=a.position(award.position,wave.seats);
                float x=award.id==-1?393:seat[0];
                float y=(award.id==-1?384:Arena.plateY(seat[0],seat[1])-4)+3*(1-ease(local/200));
                float half=Math.max(27,award.label.length()*4.1f+7);
                a.box(x-half,y-14,x+half,y+2,7,alpha(0x172b24,.96f*opacity));
                a.text(award.label,x,y-1,13,alpha(tint,opacity),a.nums,Paint.Align.CENTER);
            }
        }
    }
}
