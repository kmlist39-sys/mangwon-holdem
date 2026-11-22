package kr.mangwon.holdem;

import android.animation.ValueAnimator;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.LightingColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import org.json.JSONObject;

/** Fills the felt inside the acting seat's brass frame, behind its existing type. */
final class NameplateClock {
    private final Arena view;
    private final Path interior=new Path();
    private final Paint shine=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
    private final RectF spriteBounds=new RectF();
    private final Path burningEdge=new Path();
    private final LinearGradient heat=new LinearGradient(0,0,5.5f,0,
        new int[]{0xfffff6cf,0xfff9bf65,0xbddd8033,0x337e3422,0x007e3422},
        new float[]{0,.18f,.45f,.78f,1},Shader.TileMode.CLAMP);
    private final LinearGradient fringe=new LinearGradient(-5,0,6,0,
        new int[]{0x00ffffff,0x39ffffff,0xc9ffffff,0x57ffffff,0x00ffffff},
        new float[]{0,.32f,.455f,.60f,1},Shader.TileMode.CLAMP);
    private Bitmap glint;
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
        view.c.restore();
        if(!unlimited){
            float inset=Math.min(radius,Math.min(edge-x1,x2-edge));
            float half=(float)Math.sqrt(Math.max(0,inset*(2*radius-inset)));
            float middle=(y1+y2)*.5f;
            view.c.save();view.c.clipPath(interior);
            drawBurn(view.c,edge,middle-half,middle+half,(float)((1-fraction)*duration));
            view.c.restore();
            view.c.save();view.c.clipRect(left,top-2,right,bottom+2);
            drawEdge(view.c,edge,middle-half,middle+half,.75f+.25f*Math.min(1,inset/3f),(float)((1-fraction)*duration));
            view.c.restore();
        }
    }

    private void drawBurn(Canvas canvas,float x,float top,float bottom,float elapsed){
        float height=bottom-top;
        if(height<2)return;
        boolean animate=ValueAnimator.areAnimatorsEnabled();
        float time=animate?elapsed:0;
        canvas.save();canvas.translate(x,top);
        burningEdge.rewind();burningEdge.moveTo(0,0);
        // A narrow hot seam sheds embers into the part of the felt the clock has consumed.
        for(int i=0;i<=12;i++){
            float y=i/12f;
            float taper=4*y*(1-y);
            float wave=(float)(.5+.5*Math.sin(y*14-time*6+.65*Math.sin(y*9+time*3)));
            float width=taper*(1.8f+3.5f*wave*wave);
            burningEdge.lineTo(width,height*y);
        }
        burningEdge.lineTo(0,height);burningEdge.close();
        shine.setColor(0xffffffff);shine.setShader(heat);
        canvas.drawPath(burningEdge,shine);shine.setShader(null);
        if(animate){
            for(int i=0;i<3;i++){
                float phase=time/(.69f+.13f*i)+i*.37f;
                phase-=(float)Math.floor(phase);
                float opacity=Math.min(1,phase*7)*(1-phase);
                float dx=1+phase*(6.6f+1.4f*i);
                float dy=height*(.31f+.22f*i)-phase*(3.1f+i)+(float)Math.sin(phase*4+i)*.45f;
                float r=(1-phase*.5f)*(.62f+.12f*i);
                shine.setColor(0xffe78a3c);shine.setAlpha(Math.round(92*opacity));
                canvas.drawCircle(dx,dy,r*2,shine);
                shine.setColor(0xffffe9be);shine.setAlpha(Math.round(255*opacity));
                canvas.drawOval(dx-r*1.3f,dy-r*.7f,dx+r*1.3f,dy+r*.7f,shine);
            }
        }
        canvas.restore();
    }

    private void drawEdge(Canvas canvas,float x,float top,float bottom,float visibility,float elapsed){
        boolean animate=ValueAnimator.areAnimatorsEnabled();
        float pulse=animate?(float)(.5+.5*Math.cos(elapsed*2*Math.PI/.88)):.55f;
        canvas.save();canvas.translate(x,0);
        shine.setColor(0xffffffff);shine.setShader(fringe);shine.setAlpha(Math.round(235*visibility));
        canvas.drawRect(-5,top,6,bottom,shine);
        shine.setShader(null);shine.setAlpha(Math.round((228+27*pulse)*visibility));
        canvas.drawRect(-.9f,top,.9f,bottom,shine);
        if(glint==null)glint=view.image("clock-glint.png");
        if(glint!=null){
            float h=bottom-top;
            sparkle(canvas,0,top+Math.min(2.8f,h*.18f),9+4.5f*pulse,visibility);
            sparkle(canvas,0,bottom-Math.min(2.4f,h*.15f),8+3.4f*(1-pulse),visibility);
            if(animate){
                float trail=elapsed/.78f;
                trail-=Math.floor(trail);
                float glow=4*trail*(1-trail);
                sparkle(canvas,2+6*trail,top+h*(.37f-.13f*trail),3.4f,visibility*glow);
            }
        }
        canvas.restore();
    }

    private void sparkle(Canvas canvas,float x,float y,float radius,float opacity){
        spriteBounds.set(x-radius,y-radius,x+radius,y+radius);
        shine.setAlpha(Math.round(255*opacity));
        canvas.drawBitmap(glint,null,spriteBounds,shine);
    }
}
