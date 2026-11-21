package kr.mangwon.holdem;

import android.animation.ValueAnimator;
import android.graphics.Bitmap;
import android.graphics.Paint;
import android.os.SystemClock;
import java.time.LocalDate;
import org.json.JSONObject;

/** A visit to the club lobby offers one dated pass. The wallet changes only on collection. */
final class AttendanceDesk {
    private static final long DAY=86400000L,KOREA=9*3600000L;
    private static final int BRASS=0xffe4c58b,IVORY=0xffffefd4,MUTED=0xffb9c6b2;
    private static final String[] WEEK={"월","화","수","목","금","토","일"};
    private final Arena a;
    private long deferred=-1,requested=-1,scheduled=-1,presented=-1,opened,paidAt=-1;
    private boolean claiming,animate;
    private Bitmap pass;
    private final Paint ink=new Paint(Paint.ANTI_ALIAS_FLAG);
    private String printedDate="";

    AttendanceDesk(Arena arena){a=arena;}

    void refresh(){
        if(!a.screen.equals("lobby")||a.panel.equals("entry")){deferred=-1;return;}
        if(!a.host.loaded||!a.host.active||(!a.panel.isEmpty()&&!a.panel.equals("attendance")))return;
        JSONObject offer=a.snapshot.optJSONObject("attendance");
        long now=System.currentTimeMillis(),day=Math.floorDiv(now+KOREA,DAY);
        if(scheduled!=day){
            scheduled=day;
            a.postInvalidateDelayed(Math.max(100,(day+1)*DAY-KOREA-now+75));
        }
        if(offer==null||offer.optLong("day",-1)!=day){
            if(!claiming&&requested!=day){requested=day;a.host.attendanceStatus();}
            return;
        }
        requested=-1;
        if(paidAt>=0&&a.panel.equals("attendance")){
            if(a.now-paidAt>=1100){a.panel="";paidAt=-1;a.invalidate();}
            return;
        }
        if(!offer.optBoolean("available")){
            if(!claiming&&a.panel.equals("attendance"))a.panel="";
            return;
        }
        if(deferred==day)return;
        if(!a.panel.equals("attendance")||presented!=day){
            a.panel="attendance";a.hits.clear();a.held="";
            presented=day;opened=a.now;paidAt=-1;claiming=false;
            animate=ValueAnimator.areAnimatorsEnabled();
            LocalDate date=LocalDate.ofEpochDay(day);
            printedDate=date.getMonthValue()+"월 "+date.getDayOfMonth()+"일 "+WEEK[date.getDayOfWeek().getValue()-1]+"요일";
            if(pass==null)pass=a.image("attendance-pass.webp");
        }
    }
    void observe(JSONObject state){
        if(state.optBoolean("attendancePaid")&&a.panel.equals("attendance")){
            paidAt=SystemClock.uptimeMillis();
            if(a.opt.optBoolean("sound"))a.host.fx("chip",.40f);
        }
    }
    void finished(boolean error){
        claiming=false;
        if(error)a.note("저장하지 못했습니다. 다시 눌러 주세요.");
        a.invalidate();
    }
    boolean dismiss(){
        if(!a.panel.equals("attendance"))return false;
        if(!claiming){deferred=presented;paidAt=-1;a.panel="";a.invalidate();}
        return true;
    }
    boolean action(String key){
        if(!a.panel.equals("attendance"))return false;
        if(key.equals("attendanceLater"))dismiss();
        else if(key.equals("attendanceClaim")&&!claiming&&paidAt<0){
            claiming=true;a.held="";a.host.claimAttendance(presented);a.invalidate();
        }
        return true;
    }
    void draw(){
        a.hits.clear();a.box(-a.offX/a.scale,-a.offY/a.scale,(a.getWidth()-a.offX)/a.scale,
                (a.getHeight()-a.offY)/a.scale,0,0xb008100e);
        float enter=animate?Math.max(0,1-(a.now-opened)/260f):0;
        a.c.save();a.c.translate(0,10*enter*enter);
        if(pass!=null)a.photo(pass,185,77,895,443);
        else a.stage.plate(185,77,895,443,0);
        a.txt(printedDate,526,156,17,MUTED);
        a.text(paidAt>=0?"출석 완료":"오늘의 출석 보상",523,210,36,IVORY,a.title,Paint.Align.LEFT);
        if(paidAt>=0){
            stamp();
            a.text("+10,000 P",524,279,43,BRASS,a.nums,Paint.Align.LEFT);
            a.txt("내 지갑  "+a.fmt(a.snapshot.optLong("wallet"))+" P",526,330,19,IVORY);
        }else{
            a.text("10,000 P",524,272,45,BRASS,a.nums,Paint.Align.LEFT);
            if(claiming){
                a.stage.plate(510,300,804,360,0);
                a.mid("받는 중",657,338,18,IVORY);
            }else{
                a.key("1만 P 받기",510,300,294,60,"attendanceClaim",1);
                a.mid("나중에",657,391,15,MUTED);a.hit(592,363,722,409,"attendanceLater");
            }
        }
        a.c.restore();
    }
    private void stamp(){
        float t=animate?Math.min(1,Math.max(0,(a.now-paidAt)/220f)):1;
        float land=1-t*t*(3-2*t);
        a.c.save();a.c.translate(411,340);a.c.rotate(-12);a.c.scale(1+.32f*land,1+.32f*land);
        int ivory=(Math.round(255*t)<<24)|0xf1dbab;
        ink.setColor((Math.round(240*t)<<24)|0x6b312a);ink.setStyle(Paint.Style.FILL);
        a.c.drawRoundRect(-48,-24,48,23,4,4,ink);
        ink.setStyle(Paint.Style.STROKE);ink.setStrokeWidth(1.6f);ink.setColor(ivory);
        a.c.drawRoundRect(-46,-22,46,21,3,3,ink);ink.setStrokeWidth(.6f);
        a.c.drawRoundRect(-42,-18,42,17,2,2,ink);
        a.text("출 석",0,8,22,ivory,a.title,Paint.Align.CENTER);a.c.restore();
    }
}
