package kr.mangwon.holdem;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;

/** Reuses the original crosshatched artwork for all pocket-size cards on the felt. */
final class CardBack {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final RectF target = new RectF();
    private Bitmap pocket;
    private int density;

    void draw(Canvas canvas, float x, float y, float w, float h, float angle, float scale) {
        canvas.save();
        canvas.rotate(angle, x+w/2, y+h/2);
        if (w == Arena.POCKET_WIDTH && h == Arena.POCKET_HEIGHT) {
            drawPocket(canvas,x,y,scale,255);
        } else {
            drawOriginal(canvas, x, y, w, h);
        }
        canvas.restore();
    }

    void drawPocket(Canvas canvas,float x,float y,float scale,int alpha) {
        float w=Arena.POCKET_WIDTH,h=Arena.POCKET_HEIGHT;
        int needed=Math.max(2,(int)Math.ceil(scale));
        if(pocket==null || density!=needed){
            density=needed;
            pocket=Bitmap.createBitmap((int)(w+4)*density,(int)(h+6)*density,Bitmap.Config.ARGB_8888);
            Canvas tile=new Canvas(pocket);
            tile.scale(density,density);
            drawOriginal(tile,1,1,w,h);
        }
        paint.setColor(0xffffffff);
        paint.setAlpha(alpha);
        target.set(x-1,y-1,x+w+3,y+h+5);
        canvas.drawBitmap(pocket,null,target,paint);
    }

    private void drawOriginal(Canvas canvas, float x, float y, float w, float h) {
        paint.setColor(0x86090d0b);
        canvas.drawRoundRect(x+1,y+3,x+w+1,y+h+3,5,5,paint);
        paint.setColor(0xfff6eddb);
        canvas.drawRoundRect(x,y,x+w,y+h,5,5,paint);
        paint.setColor(0xff693e3a);
        canvas.drawRoundRect(x+3,y+3,x+w-3,y+h-3,3,3,paint);
        canvas.save();
        canvas.clipRect(x+4,y+4,x+w-4,y+h-4);
        paint.setStrokeWidth(1);
        for(float j=-h;j<w+h;j+=8) {
            paint.setColor(0xff9e6756);
            canvas.drawLine(x+j,y,x+j+h,y+h,paint);
            paint.setColor(0xff8d5b4d);
            canvas.drawLine(x+j,y+h,x+j+h,y,paint);
        }
        canvas.restore();
    }
}
