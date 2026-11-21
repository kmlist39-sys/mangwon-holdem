package kr.mangwon.holdem;

import android.graphics.Paint;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Locale;

/** Each board sends its own awarded chips. Refunds and the house cut never enter the animation. */
final class SettlementMotion {
    private static final int LEAD=100, TRAVEL=685, STAGGER=25, BOARD_INTERVAL=1280, END=1570;
    private static final int GOLD=0xffefc47c, TEAL=0xffa4d6c5;
    private final Arena a;
    private final ArrayList<Wave> waves=new ArrayList<>();
    private String observed="";
    private long started;

    SettlementMotion(Arena arena){a=arena;}

    static final class Award {
        final int id,position,amount;
        final String label;
        Award(int id,int position,int amount){
            this.id=id;this.position=position;this.amount=amount;
            label=String.format(Locale.US,"+%,d",amount);
        }
    }
    static final class Wave {
        final int pot,board,seats;
        final boolean doubleBoard;
        final String caption;
        final ArrayList<Award> awards=new ArrayList<>();
        Wave(int pot,int board,int seats,boolean doubleBoard){
            this.pot=pot;this.board=board;this.seats=seats;this.doubleBoard=doubleBoard;
            String name=pot==0?"메인 팟":pot==1?"사이드 팟":pot==2?"서드 팟":"사이드 팟 "+pot;
            caption=doubleBoard?(board==0?"위":"아래")+"  ·  "+name:name;
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
                JSONArray winners=board.optJSONArray("winners");
                for(int i=0;winners!=null&&i<winners.length();i++){
                    JSONObject who=winners.optJSONObject(i);if(who==null)continue;
                    int id=who.optInt("id",Integer.MIN_VALUE),amount=who.optInt("amount");
                    if(id==Integer.MIN_VALUE||amount<=0)continue;
                    int index=indexOf(seats,id,true);
                    if(index<0||(eligible!=null&&indexOf(eligible,id,false)<0))continue;
                    wave.awards.add(new Award(id,(index-hero+seats.length())%seats.length(),amount));
                }
                if(!wave.awards.isEmpty())payout.add(wave);
            }
        }
        return payout;
    }
    void observe(JSONObject old,JSONObject game,long now){
        if(!handles(game)){waves.clear();return;}
        JSONObject settlement=game.optJSONObject("settlement");
        String session=game.optString("sessionId");
        int hand=game.optInt("hand");
        String id=session+"/"+hand+"/"+settlement.optInt("hand",hand);
        if(id.equals(observed))return;
        observed=id;waves.clear();
        if(old==null||old.optInt("hand",-1)!=hand||!session.equals(old.optString("sessionId"))
                ||old.optJSONObject("settlement")!=null||"done".equals(old.optString("phase")))return;
        waves.addAll(sequence(game));started=now;
    }
    boolean animating(long now){
        return !waves.isEmpty()&&now>=started&&now-started<LEAD+(waves.size()-1)*BOARD_INTERVAL+END;
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
        long elapsed=a.now-started-LEAD;
        for(int w=0;w<waves.size();w++){
            Wave wave=waves.get(w);long age=elapsed-(long)w*BOARD_INTERVAL;
            if(age<0||age>1200)continue;
            for(int i=0;i<wave.awards.size();i++){
                Award award=wave.awards.get(i);float[] seat=a.position(award.position,wave.seats);
                float tx=endX(award,seat),ty=endY(award,seat);
                int chips=award.amount<2000?2:award.amount<50000?3:award.amount<200000?4:5;
                for(int k=0;k<chips;k++){
                    float t=(age-i*STAGGER-k*32)/(float)TRAVEL;
                    if(t<=0||t>=1)continue;
                    float f=ease(t),s=1-f;
                    float sx=Arena.POT_X+((k*13)%19-9),sy=Arena.POT_Y-3*k;
                    // The outer arc follows the corresponding edge of the board; card faces are painted above it.
                    float guideY=wave.doubleBoard&&wave.board==1?367:200;
                    float x=s*s*s*sx+3*s*s*f*(sx+27)+3*s*f*f*(tx*.72f+sx*.28f)+f*f*f*tx;
                    float y=s*s*s*sy+3*s*s*f*guideY+3*s*f*f*(guideY*.56f+ty*.44f)+f*f*f*ty;
                    y-=(float)Math.sin(Math.PI*f)*(5+k*1.4f);
                    float fade=ease(t/.13f)*(1-ease((t-.86f)/.14f));
                    a.stage.chips.disc(x+(k%3-1)*3*f,y-(k%2)*2*f,15+2.8f*(float)Math.sin(Math.PI*f),k%3==0?0:wave.board==1?2:3,(int)(248*fade));
                }
            }
        }
    }
    void labels(){
        if(!animating(a.now))return;
        long elapsed=a.now-started-LEAD;
        for(int w=0;w<waves.size();w++){
            Wave wave=waves.get(w);long age=elapsed-(long)w*BOARD_INTERVAL;
            if(age<0||age>END)continue;
            int tint=wave.board==1?TEAL:GOLD;
            if(age<1000){
                float opacity=ease(age/160f)*(1-ease((age-775)/225f));
                float x=Arena.POT_X,y=249;
                a.box(x-45,y-13,x+45,y+3,6,alpha(0x162b24,opacity*.92f));
                a.text(wave.caption,x,y,11,alpha(tint,opacity),a.bold,Paint.Align.CENTER);
            }
            for(int i=0;i<wave.awards.size();i++){
                Award award=wave.awards.get(i);
                float local=age-i*STAGGER-TRAVEL+100;
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
