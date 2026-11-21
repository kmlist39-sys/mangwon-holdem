package kr.mangwon.holdem;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;

/** The red channel of the engraved title's mask records when the brush crosses each pixel. */
final class HeadsUpLettering {
    private static final int COLS=54, ROWS=10;
    private static final int[] START={110,540,905,1420,1567,1714};
    private static final float[] CENTER={355,654,924,1104,1185,1262};
    private static final float[] EDGES={490,783,1059,1142,1223};
    private final float[] vertices=new float[(COLS+1)*(ROWS+1)*2];
    private final float[] lift=new float[6], squash=new float[6];
    private final Paint ink=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
    private final Paint gate=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
    private final ColorMatrixColorFilter[] levels=new ColorMatrixColorFilter[264];

    HeadsUpLettering(){
        gate.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
        float[] m={0,0,0,0,255, 0,0,0,0,255, 0,0,0,0,255, -42.5f,0,0,0,0};
        for(int i=0;i<levels.length;i++){
            m[19]=42.5f*i;
            levels[i]=new ColorMatrixColorFilter(m);
        }
    }

    void draw(Canvas canvas,Bitmap art,Bitmap timeMap,RectF dst,float ms,float opacity,boolean animate){
        ink.setAlpha(Math.round(255*opacity));
        if(!animate||ms>=2130){
            canvas.drawBitmap(art,null,dst,ink);
            return;
        }
        if(timeMap==null){
            int saved=canvas.save();
            canvas.clipRect(dst.left,dst.top,dst.left+dst.width()*clamp(ms/1900),dst.bottom);
            canvas.drawBitmap(art,null,dst,ink);canvas.restoreToCount(saved);
            return;
        }
        updateMesh(ms,dst);
        int layer=canvas.saveLayer(dst.left-dst.width()*.035f,dst.top-dst.height()*.16f,
                dst.right+dst.width()*.035f,dst.bottom+dst.height()*.16f,null);
        canvas.drawBitmapMesh(art,COLS,ROWS,vertices,0,null,0,ink);
        int threshold=Math.min(levels.length-1,Math.max(0,Math.round(ms*255/2000f)));
        gate.setColorFilter(levels[threshold]);
        canvas.drawBitmapMesh(timeMap,COLS,ROWS,vertices,0,null,0,gate);
        canvas.restoreToCount(layer);
    }

    private static float clamp(float x){return Math.max(0,Math.min(1,x));}
    private static float smooth(float t){t=clamp(t);return t*t*(3-2*t);}

    private void updateMesh(float ms,RectF dst){
        for(int i=0;i<START.length;i++){
            boolean bang=i>=3;
            float drop=clamp((ms-START[i])/(bang?148f:425f));
            float settle=clamp((ms-START[i]-(bang?145:408))/(bang?175f:212f));
            float rebound=(float)Math.sin(settle*Math.PI)*(1-settle);
            lift[i]=(1-smooth(drop))*(bang?-43:18) + rebound*(bang?-11:5);
            squash[i]=rebound*(bang?.126f:.048f);
        }
        float dx=dst.width()/1440f,dy=dst.height()/554f;
        int n=0;
        for(int row=0;row<=ROWS;row++){
            float y=554f*row/ROWS;
            for(int col=0;col<=COLS;col++){
                float x=1440f*col/COLS,lean=x+(y-275)*.30f;
                int i=0;while(i<EDGES.length&&lean>=EDGES[i]+20)i++;
                int j=Math.min(i+1,5);
                float blend=i==5?0:smooth((lean-EDGES[i]+20)/40);
                float dyLocal=lift[i]+(lift[j]-lift[i])*blend;
                float squashHere=squash[i]+(squash[j]-squash[i])*blend;
                float center=CENTER[i]+(CENTER[j]-CENTER[i])*blend;
                float influence=smooth((lean-100)/120)*smooth((1420-lean)/80);
                vertices[n++]=dst.left+dx*(x+influence*((x-center)*squashHere*.55f-dyLocal*.18f));
                vertices[n++]=dst.top+dy*(y+influence*(dyLocal-(y-365)*squashHere));
            }
        }
    }
}
