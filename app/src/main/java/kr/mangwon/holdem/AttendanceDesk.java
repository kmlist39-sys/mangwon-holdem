package kr.mangwon.holdem;

import android.animation.ValueAnimator;
import android.graphics.Bitmap;
import android.graphics.Paint;
import android.os.SystemClock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import org.json.JSONObject;

/** A dated check-in pass, including the five-day award and its collection receipt. */
final class AttendanceDesk {
    private static final long DAY=86400000L,KOREA=9*3600000L;
    private static final int BRASS=0xffe4c58b,IVORY=0xffffefd4,MUTED=0xffb9c6b2;
    private final Arena a;
    private final boolean english;
    private final DateTimeFormatter dateFormat;
    private long deferred=-1,requested=-1,scheduled=-1,presented=-1,opened,paidAt=-1;
    private boolean claiming,animate;
    private Bitmap pass;
    private JSONObject receipt;
    private final Paint ink=new Paint(Paint.ANTI_ALIAS_FLAG);
    private String printedDate="";

    AttendanceDesk(Arena arena){
        a=arena;
        english=!"ko".equals(a.getResources().getConfiguration().getLocales().get(0).getLanguage());
        dateFormat=english?DateTimeFormatter.ofPattern("EEE, MMM d",Locale.ENGLISH):
                DateTimeFormatter.ofPattern("M월 d일 E요일",Locale.KOREAN);
    }
    private String label(int id,Object... values){return a.getResources().getString(id,values);}
    private JSONObject offer(){return a.snapshot.optJSONObject("attendance");}
    private boolean paid(JSONObject day){return receipt!=null||!day.optBoolean("available");}
    private void show(JSONObject day){
        receipt=null;paidAt=-1;claiming=false;
        presented=day.optLong("day");printedDate=LocalDate.ofEpochDay(presented).format(dateFormat);
        a.panel="attendance";a.hits.clear();a.held="";opened=a.now;
        animate=ValueAnimator.areAnimatorsEnabled();
        if(pass==null)pass=a.image("attendance-pass.webp");
        a.invalidate();
    }
    void lobbyButton(float x,float y){
        JSONObject day=offer();if(day==null)return;
        a.brass(label(day.optBoolean("available")?R.string.daily_open:R.string.daily_collected_short),x,y,94,"attendanceOpen");
    }
    void refresh(){
        if(!a.screen.equals("lobby")||!a.host.loaded||!a.host.active||(!a.panel.isEmpty()&&!a.panel.equals("attendance")))return;
        long now=System.currentTimeMillis(),today=Math.floorDiv(now+KOREA,DAY);
        if(scheduled!=today){
            scheduled=today;
            a.postInvalidateDelayed(Math.max(100,(today+1)*DAY-KOREA-now+75));
        }
        JSONObject day=offer();
        if(day==null||day.optLong("day",-1)!=today){
            if(!claiming&&requested!=today){requested=today;a.host.attendanceStatus();}
            return;
        }
        requested=-1;
        if(a.panel.equals("attendance")&&(receipt!=null||claiming||presented==today))return;
        if(day.optBoolean("available")&&deferred!=today)show(day);
    }
    void observe(JSONObject state){
        if(state.optBoolean("attendancePaid")&&a.panel.equals("attendance")){
            receipt=state.optJSONObject("attendanceReceipt");
            if(receipt==null)receipt=state.optJSONObject("attendance");
            if(receipt!=null){
                presented=receipt.optLong("day",presented);
                printedDate=LocalDate.ofEpochDay(presented).format(dateFormat);
            }
            paidAt=SystemClock.uptimeMillis();
            if(a.opt.optBoolean("sound"))a.host.fx("chip",.40f);
        }
    }
    void finished(boolean error){
        claiming=false;
        if(error)a.note(label(R.string.daily_error));
        a.invalidate();
    }
    boolean dismiss(){
        if(!a.panel.equals("attendance"))return false;
        if(!claiming){deferred=presented;paidAt=-1;receipt=null;a.panel="";a.invalidate();}
        return true;
    }
    boolean action(String key){
        JSONObject day=offer();
        if(key.equals("attendanceOpen")&&a.screen.equals("lobby")&&a.panel.isEmpty()){
            if(day!=null)show(day);return true;
        }
        if(!a.panel.equals("attendance"))return false;
        if(key.equals("attendanceLater"))dismiss();
        else if(key.equals("attendanceClaim")&&day!=null&&!claiming&&!paid(day)&&day.optLong("day")==presented){
            claiming=true;a.held="";a.host.claimAttendance(presented);a.invalidate();
        }
        return true;
    }
    void draw(){
        JSONObject day=receipt!=null?receipt:offer();if(day==null)return;
        boolean collected=paid(day);
        int streak=Math.max(1,day.optInt("streak",1)),cycle=Math.max(1,Math.min(5,day.optInt("cycleDay",1)));
        String points=a.fmt(day.optLong("points",10000));
        a.hits.clear();a.box(-a.offX/a.scale,-a.offY/a.scale,(a.getWidth()-a.offX)/a.scale,
                (a.getHeight()-a.offY)/a.scale,0,0xb90a100c);
        float enter=animate?Math.max(0,1-(a.now-opened)/260f):0;
        a.c.save();a.c.translate(0,10*enter*enter);
        if(pass!=null)a.photo(pass,130,49,950,471);
        else a.stage.plate(130,49,950,471,0);
        a.txt(printedDate,433,116,15,MUTED);
        a.text(label(R.string.daily_streak,streak),892,116,18,BRASS,a.bold,Paint.Align.RIGHT);
        a.text(label(collected?R.string.daily_collected:R.string.daily_title),431,169,32,IVORY,a.title,Paint.Align.LEFT);
        a.text((collected?"+":"")+points+" P",428,230,48,BRASS,a.nums,Paint.Align.LEFT);
        a.line(431,249,892,249,0xff8b7147,.8f);
        for(int i=1;i<=5;i++)slot(i,streak-cycle+i,cycle,collected);
        a.stage.bet.fitted(label(R.string.daily_cycle_note),432,350,462,14,MUTED,Paint.Align.LEFT);
        if(collected){
            stamp();
            a.txt(label(R.string.daily_wallet,a.fmt(a.snapshot.optLong("wallet"))),433,406,17,IVORY);
            a.key(label(R.string.daily_close),743,371,152,55,"attendanceLater",0);
        }else{
            if(claiming){
                a.stage.plate(433,371,739,426,0);a.mid(label(R.string.daily_collecting),586,405,17,IVORY);
            }else{
                a.key(label(R.string.daily_claim,points),433,371,306,55,"attendanceClaim",1);
                a.brass(label(R.string.daily_later),768,383,120,"attendanceLater");
            }
        }
        a.c.restore();
    }
    private void slot(int index,int streakDay,int cycle,boolean collected){
        float x=431+(index-1)*94;
        boolean current=index==cycle,done=index<cycle||current&&collected;
        a.box(x,264,x+86,325,7,current?0xff514730:done?0xff2e4234:0xff192e24);
        a.stroke(x,264,x+86,325,7,current?BRASS:done?0xff768367:0xff4a5a47,current?1.6f:.8f);
        a.mid(label(R.string.daily_slot,streakDay),x+43,287,15,current?IVORY:done?0xccdfddc4:MUTED);
        String value=english?(index==5?"200K":"10K"):(index==5?"20만":"1만");
        a.mid(value+" P",x+43,311,14,current||index==5?BRASS:done?IVORY:MUTED);
        if(done){a.line(x+6,273,x+9,276,BRASS,1.6f);a.line(x+9,276,x+14,269,BRASS,1.6f);}
    }
    private void stamp(){
        float t=animate&&paidAt>=0?Math.min(1,Math.max(0,(a.now-paidAt)/220f)):1;
        float land=1-t*t*(3-2*t);
        a.c.save();a.c.translate(324,343);a.c.rotate(-12);a.c.scale(1+.32f*land,1+.32f*land);
        int ivory=(Math.round(255*t)<<24)|0xf1dbab;
        ink.setColor((Math.round(240*t)<<24)|0x6b312a);ink.setStyle(Paint.Style.FILL);
        a.c.drawRoundRect(-48,-23,48,23,4,4,ink);
        ink.setStyle(Paint.Style.STROKE);ink.setStrokeWidth(1.6f);ink.setColor(ivory);
        a.c.drawRoundRect(-46,-21,46,21,3,3,ink);
        a.text(label(R.string.daily_stamp),0,8,21,ivory,a.title,Paint.Align.CENTER);a.c.restore();
    }
}
