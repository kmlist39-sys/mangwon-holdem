package kr.mangwon.holdem;

import android.graphics.ColorFilter;
import android.graphics.LightingColorFilter;
import android.graphics.Path;
import org.json.JSONObject;

/** Fills the felt inside the acting seat's brass frame, behind its existing type. */
final class NameplateClock {
    private final Arena view;
    private final Path interior=new Path();
    private static final ColorFilter GREEN=new LightingColorFilter(0xffc6fbe3,0xff0e523d);
    private static final ColorFilter AMBER=new LightingColorFilter(0xffffd499,0xff664016);
    private static final ColorFilter RED=new LightingColorFilter(0xffffc6a4,0xff6b2019);

    NameplateClock(Arena arena){view=arena;}

    void paint(float left,float top,float right,float bottom,boolean active,int type,ColorFilter tone){
        StageArt stage=view.stage;
        stage.plate(left,top,right,bottom,type,tone);
        if(!active)return;
        JSONObject turn=stage.first();
        if(turn==null)return;
        boolean unlimited=turn.optBoolean("isMe")&&!view.opt.optBoolean("timing");
        double remaining=unlimited?1:stage.time(turn)/Math.max(.01,turn.optDouble("max",15));
        float fraction=(float)Math.max(0,Math.min(1,remaining));
        if(fraction<=0)return;
        // Follow the original inset capsule. The filigree ends and brass rim never move.
        float x1=left+6.1f,x2=right-6.1f,y1=top+2.5f,y2=bottom-2.5f;
        float radius=(y2-y1)*.5f,edge=x1+(x2-x1)*fraction;
        interior.reset();interior.addRoundRect(x1,y1,x2,y2,radius,radius,Path.Direction.CW);
        view.c.save();view.c.clipPath(interior);view.c.clipRect(x1,y1,edge,y2);
        ColorFilter ink=fraction<=.2f?RED:fraction<=.4f?AMBER:GREEN;
        stage.plate(left,top,right,bottom,0,ink);
        if(fraction<.995f)view.line(edge-.65f,y1,edge-.65f,y2,0x9fdfdbc4,1.3f);
        view.c.restore();
    }
}
