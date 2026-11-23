package kr.mangwon.holdem;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;

/** The four cards share a camera, but each has its own release, flip and flight. */
final class OpeningAces {
    static final int STREET_AT = 2870;
    static final int CLEAR_AT = 3820;
    private static final int FACE_AT = 860, FACE_STEP = 175, TURN_MS = 530;
    private static final float[] ROLL = {-13f,-4.5f,5f,14f};
    private static final float[] FLY_X = {-.69f,-.37f,.34f,.72f};
    private static final float[] FLY_Y = {-.26f,-1.18f,-1.11f,.48f};
    private static final float[] SPIN = {-81,-45,65,85};
    private static final float[] CARD = {-1,-1, 1,-1, 1,1, -1,1};
    private final float[] projected = new float[8];
    private final Rect[] sources = new Rect[5];
    private final RectF target = new RectF();
    private final Matrix project = new Matrix();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Bitmap cards, felt;
    private int cuesPlayed;

    OpeningAces(Bitmap cards, Bitmap felt) {
        this.cards = cards;
        this.felt = felt;
        for (int i = 0; i < sources.length; i++) {
            int column = i % 3, row = i / 3;
            sources[i] = new Rect(column * cards.getWidth()/3 + 2, row * cards.getHeight()/2 + 2,
                (column+1) * cards.getWidth()/3 - 2, (row+1) * cards.getHeight()/2 - 2);
        }
    }

    private static float smooth(float time, float start, float duration) {
        return TitleSequence.smooth(TitleSequence.range(time,start,duration));
    }

    void sound(Arena arena, long ms) {
        // Start the recorded swish slightly ahead of the printed face turning into view.
        int crossed = Math.min(4, Math.max(0,(int)Math.floor((ms-FACE_AT-75)/(float)FACE_STEP)+1));
        if (crossed > cuesPlayed && ms < FACE_AT + 4*FACE_STEP + TURN_MS && arena.opt.optBoolean("sound"))
            arena.host.fx("flip", .25f);
        cuesPlayed = Math.max(cuesPlayed, crossed);
    }

    void draw(Canvas c, int width, int height, long ms) {
        float t = ms;
        float fade = 1-smooth(t,STREET_AT,720);
        if (fade > 0) {
            paint.setColor(0xff101817);
            paint.setAlpha(Math.round(255*fade));
            c.drawRect(0,0,width,height,paint);
            if (felt != null) {
                float scale = Math.max(width/(float)felt.getWidth(), height/(float)felt.getHeight())
                    * (1.11f+.035f*smooth(t,0,STREET_AT));
                float halfW = felt.getWidth()*scale*.5f, halfH = felt.getHeight()*scale*.5f;
                target.set(width*.5f-halfW,height*.5f-halfH,width*.5f+halfW,height*.5f+halfH);
                paint.setColor(Color.WHITE);
                paint.setAlpha(Math.round(230*fade));
                c.drawBitmap(felt,null,target,paint);
            }
        }
        for (int i=0; i<4; i++) card(c,width,height,t,i);
    }

    private void card(Canvas canvas,int w,int h,float t,int i) {
        float fan = TitleSequence.out(TitleSequence.range(t,120+i*108,790));
        float turn = smooth(t,FACE_AT+i*FACE_STEP,TURN_MS);
        float fly = TitleSequence.range(t,2510+i*102,990);
        if (fly>=1) return;
        float halfH = Math.min(h*.252f,w*.129f), halfW = halfH*(2f/3);
        float side = i-1.5f, arc = (float)Math.sin(Math.PI*fly);
        float x = w*.5f + (1-fan)*i*1.4f + fan*side*halfW*1.92f + w*FLY_X[i]*fly*fly;
        float y = h*(.59f-.092f*fan) + halfH*.10f*side*side*fan - (1-fan)*i*2f
            - halfH*.14f*(float)Math.sin(Math.PI*turn) + h*(FLY_Y[i]*fly*fly-.23f*arc);
        float roll = (-11+i*1.3f)*(1-fan) + ROLL[i]*fan + SPIN[i]*fly;
        float yaw = (float)Math.PI*(1-turn) + fly*(i%2==0?5.8f:-5.1f);
        float pitch = .08f*(1-fan) + (i%2==0?.43f:-.36f)*arc;
        float zoom = .91f+.09f*fan + arc*(i%2==0?.16f:.22f);
        halfW *= zoom; halfH *= zoom;
        canvas.save();
        canvas.translate(x,y);
        canvas.rotate(roll);
        float cosY=(float)Math.cos(yaw), sinY=(float)Math.sin(yaw);
        float cosP=(float)Math.cos(pitch), sinP=(float)Math.sin(pitch);
        boolean back=cosY<0;
        float camera=halfH*5;
        for (int k=0;k<8;k+=2) {
            float u=CARD[k]*halfW*(back?-1:1), v=CARD[k+1]*halfH;
            float z=-u*sinY;
            float perspective=camera/(camera-(z*cosP+v*sinP));
            projected[k]=u*cosY*perspective;
            projected[k+1]=(v*cosP-z*sinP)*perspective;
        }
        if (Math.abs(cosY)>.012f && project.setPolyToPoly(CARD,0,projected,0,4)) {
            canvas.concat(project);
            target.set(-.98f,-.95f,1.022f,1.035f);
            paint.setColor(0x690d100d);
            canvas.drawRoundRect(target,.045f,.035f,paint);
            target.set(-1,-1,1,1);
            paint.setColor(Color.WHITE);
            canvas.drawBitmap(cards,sources[back?4:i],target,paint);
            if (Math.abs(cosY)<.8f) {
                paint.setColor(Color.argb((int)(42*(1-Math.abs(cosY))),42,30,20));
                canvas.drawRoundRect(target,.04f,.03f,paint);
            }
        }
        canvas.restore();
    }
}
