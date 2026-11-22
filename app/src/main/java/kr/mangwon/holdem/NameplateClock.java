package kr.mangwon.holdem;

import android.animation.ValueAnimator;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LightingColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import org.json.JSONObject;

/** Fills the felt inside the acting seat's brass frame, behind its existing type. */
final class NameplateClock {
    private final Arena view;
    private final Path interior=new Path();
    private final Paint shine=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path star=new Path();
    private static final ColorFilter GREEN=new LightingColorFilter(0xffc6fbe3,0xff0e523d);
    private static final ColorFilter AMBER=new LightingColorFilter(0xffffd499,0xff664016);
    private static final ColorFilter RED=new LightingColorFilter(0xffffc6a4,0xff6b2019);

    NameplateClock(Arena arena){view=arena;}

    void paint(float left,float top,float right,float bottom,boolean active,int type,ColorFilter tone){
        StageArt stage=view.stage;
        stage.plate(left,top,right,bottom,type,tone);
        if(!active)return;
        JSONObject turn=stage.first();
        if(turn==null||!"bet".equals(turn.optString("phase")))return;
        boolean unlimited=turn.optBoolean("isMe")&&!view.opt.optBoolean("timing");
        double duration=Math.max(.01,turn.optDouble("max",15));
        double remaining=unlimited?1:stage.time(turn)/duration;
        float fraction=(float)Math.max(0,Math.min(1,remaining));
        if(fraction<=0)return;
        // Follow the original inset capsule. The filigree ends and brass rim never move.
        float x1=left+6.1f,x2=right-6.1f,y1=top+2.5f,y2=bottom-2.5f;
        float radius=(y2-y1)*.5f,edge=x1+(x2-x1)*fraction;
        interior.reset();interior.addRoundRect(x1,y1,x2,y2,radius,radius,Path.Direction.CW);
        view.c.save();view.c.clipPath(interior);
        view.c.save();view.c.clipRect(x1,y1,edge,y2);
        ColorFilter ink=fraction<=.2f?RED:fraction<=.4f?AMBER:GREEN;
        stage.plate(left,top,right,bottom,0,ink);
        view.c.restore();
        if(!unlimited){
            float visibility=Math.min(1,Math.min(edge-x1,x2-edge)/7f);
            drawEdge(view.c,edge,y1,y2,visibility,(float)((1-remaining)*duration));
        }
        view.c.restore();
    }

    private void drawEdge(Canvas canvas,float x,float top,float bottom,float visibility,float elapsed){
        if(visibility<=0)return;
        shine.setColor(0xffffd89a);
        shine.setAlpha(Math.round(95*visibility));
        shine.setStrokeWidth(.75f);
        canvas.drawLine(x,top+1,x,bottom-1,shine);
        if(!ValueAnimator.areAnimatorsEnabled())return;

        // The tip and its two trailing flecks follow the clock, including any pause.
        float phase=elapsed/1.52f;
        phase-=Math.floor(phase);
        float pulse=(float)(.5-.5*Math.cos(phase*2*Math.PI));
        float y=top+3.7f,span=1.35f+1.6f*pulse;
        shine.setColor(0xffffca78);
        shine.setAlpha(Math.round(50*visibility*pulse));
        canvas.drawCircle(x,y,span+1f,shine);
        shine.setColor(0xffffedbe);
        shine.setAlpha(Math.round((90+152*pulse)*visibility));
        star.rewind();star.moveTo(x,y-span);
        star.lineTo(x+.43f,y-.43f);star.lineTo(x+span*.78f,y);
        star.lineTo(x+.43f,y+.43f);star.lineTo(x,y+span);
        star.lineTo(x-.43f,y+.43f);star.lineTo(x-span*.78f,y);
        star.lineTo(x-.43f,y-.43f);star.close();canvas.drawPath(star,shine);
        for(int i=0;i<2;i++){
            float age=(phase+i*.5f)%1f;
            float glow=4*age*(1-age);
            shine.setAlpha(Math.round(150*visibility*glow));
            canvas.drawCircle(x+1.4f+age*(3+i),y+1+age*(2+i),.46f+.22f*glow,shine);
        }
    }
}
