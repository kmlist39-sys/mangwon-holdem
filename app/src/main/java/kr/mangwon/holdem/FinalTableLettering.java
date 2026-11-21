package kr.mangwon.holdem;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;

/** The painted finals crest is assembled around six separately engraved Hangul syllables. */
final class FinalTableLettering {
    private static final int SCENE=0, CUP=1, SEAL=2, LETTER=3, RIBBON=9;
    private static final int[] START={175,390,598,832,1044,1257}, LENGTH={490,420,525,520,440,515};
    private static final PorterDuffXfermode CUT=new PorterDuffXfermode(PorterDuff.Mode.DST_IN);
    private static final PorterDuffXfermode GILD=new PorterDuffXfermode(PorterDuff.Mode.SRC_IN);
    private final Arena arena;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
    private final Paint cut=new Paint(Paint.FILTER_BITMAP_FLAG);
    private final ColorMatrixColorFilter[] transfer=new ColorMatrixColorFilter[129];
    private final LinearGradient gleam=new LinearGradient(-120,0,120,-64,
            new int[]{0x00fff9df,0x18fff3b2,0x72fff9de,0x21ffe5a0,0x00fff8db},
            new float[]{0,.33f,.50f,.65f,1},Shader.TileMode.CLAMP);
    private final Matrix local=new Matrix();
    private final Path spark=new Path();
    private volatile Piece[] pieces;

    FinalTableLettering(Arena arena){
        this.arena=arena;cut.setXfermode(CUT);
        for(int i=0;i<transfer.length;i++){
            // White on the mask means late brush arrival. A soft 12/255 edge travels with the pen.
            float cutoff=i*267f/(transfer.length-1);
            transfer[i]=new ColorMatrixColorFilter(new float[]{
                    0,0,0,0,255, 0,0,0,0,255, 0,0,0,0,255, -21.25f,0,0,0,21.25f*cutoff});
        }
        spark.moveTo(0,-1);spark.quadTo(.06f,-.12f,.82f,0);spark.quadTo(.07f,.08f,0,1);
        spark.quadTo(-.06f,.12f,-.82f,0);spark.quadTo(-.06f,-.08f,0,-1);spark.close();
    }

    private static final class Piece {
        final RectF bounds;
        final Bitmap art,arrival;
        Piece(Rect r,int[] pixels,int[] timing){
            bounds=new RectF(r);
            art=Bitmap.createBitmap(pixels,r.width(),r.height(),Bitmap.Config.ARGB_8888);
            arrival=timing==null?null:Bitmap.createBitmap(timing,r.width(),r.height(),Bitmap.Config.ARGB_8888);
        }
    }

    /** Called once on the same worker that decodes the original title. */
    void prepare(Bitmap original){
        if(original==null||pieces!=null)return;
        Bitmap map=arena.image("final-table-engraving.png");
        int w=original.getWidth(),h=original.getHeight();
        if(map==null||map.getWidth()!=w||map.getHeight()!=h)return;
        int[] source=new int[w*h],guide=new int[w*h];
        original.getPixels(source,0,w,0,0,w,h);map.getPixels(guide,0,w,0,0,w,h);map.recycle();
        Rect[] limits=new Rect[RIBBON+1];
        for(int i=0;i<limits.length;i++)limits[i]=new Rect(w,h,0,0);
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){
            int p=y*w+x;
            if((source[p]>>>24)==0)continue;
            int group=(guide[p]>>16)&255;
            if(group==SCENE&&y>=359)group=RIBBON;
            if(group>RIBBON)return;
            guide[p]=(guide[p]&0xff00)|(group<<16);
            Rect box=limits[group];box.left=Math.min(box.left,x);box.top=Math.min(box.top,y);
            box.right=Math.max(box.right,x+1);box.bottom=Math.max(box.bottom,y+1);
        }
        Piece[] ready=new Piece[limits.length];
        for(int group=0;group<limits.length;group++){
            Rect box=limits[group];if(box.isEmpty())return;
            int[] pixels=new int[box.width()*box.height()];
            int[] timing=group>=LETTER&&group<RIBBON?new int[pixels.length]:null;
            for(int y=box.top;y<box.bottom;y++)for(int x=box.left;x<box.right;x++){
                int p=y*w+x,index=(y-box.top)*box.width()+x-box.left;
                if(((guide[p]>>16)&255)!=group)continue;
                pixels[index]=source[p];
                if(timing!=null)timing[index]=0xff000000|(((guide[p]>>8)&255)*0x010101);
            }
            ready[group]=new Piece(box,pixels,timing);
        }
        pieces=ready;
    }

    private static float ease(float t){
        t=Math.min(1,Math.max(0,t));return t*t*(3-2*t);
    }
    private static int alpha(float a){return Math.round(255*Math.max(0,Math.min(1,a)));}

    void draw(Canvas c,Bitmap original,RectF bounds,float t,float opacity,boolean animate){
        Piece[] art=pieces;
        if(original==null||opacity<=0)return;
        if(!animate||art==null||t>=2220){
            paint.setColor(Color.WHITE);paint.setAlpha(alpha(opacity));c.drawBitmap(original,null,bounds,paint);return;
        }
        c.save();c.translate(bounds.left,bounds.top);
        c.scale(bounds.width()/original.getWidth(),bounds.height()/original.getHeight());
        float opening=ease((t-85)/490),cup=ease(t/650),ribbon=ease((t-245)/610);
        picture(c,art[SCENE],opacity*opening,0,-7*(1-opening),1);
        picture(c,art[RIBBON],opacity*ribbon,0,8*(1-ribbon),1);
        picture(c,art[CUP],opacity*ease(t/300),0,26*(1-cup),.91f+.09f*cup);
        float seal=ease((t-475)/590);
        picture(c,art[SEAL],opacity*seal,0,17*(1-seal),.81f+.19f*seal);
        for(int i=0;i<START.length;i++){
            float progress=(t-START[i])/LENGTH[i];if(progress<=0)continue;
            Piece piece=art[LETTER+i];float rise=1-ease(progress/.85f);
            c.save();c.translate(-3*rise,11*rise);
            if(progress>=1)picture(c,piece,opacity,0,0,1);
            else{
                c.saveLayer(piece.bounds,null);
                picture(c,piece,opacity,0,0,1);
                cut.setColorFilter(transfer[Math.min(128,Math.round(128*progress))]);
                c.drawBitmap(piece.arrival,null,piece.bounds,cut);c.restore();
            }
            c.restore();
        }
        if(t>=1710&&t<2160)polish(c,art,(t-1710)/450,opacity);
        glint(c,968,54,29,t,490,480,opacity);
        glint(c,620,86,11,t,773,335,opacity);
        glint(c,802,495,11,t,1210,370,opacity);
        glint(c,1465,247,16,t,1870,340,opacity);
        c.restore();
    }

    private void picture(Canvas canvas,Piece piece,float opacity,float dx,float dy,float scale){
        if(opacity<=0)return;
        canvas.save();canvas.translate(dx,dy);
        if(scale!=1)canvas.scale(scale,scale,piece.bounds.centerX(),piece.bounds.centerY());
        paint.setColor(Color.WHITE);paint.setAlpha(alpha(opacity));
        canvas.drawBitmap(piece.art,null,piece.bounds,paint);canvas.restore();
    }

    private void polish(Canvas canvas,Piece[] art,float phase,float opacity){
        canvas.saveLayer(55,211,1530,513,null);
        for(int i=0;i<START.length;i++)picture(canvas,art[LETTER+i],1,0,0,1);
        local.setTranslate(-210+phase*2140,335);gleam.setLocalMatrix(local);
        paint.setShader(gleam);paint.setXfermode(GILD);paint.setAlpha(alpha(opacity));
        canvas.drawRect(45,201,1540,523,paint);
        paint.setShader(null);paint.setXfermode(null);canvas.restore();
    }

    private void glint(Canvas canvas,float x,float y,float size,float time,float start,float length,float opacity){
        float phase=(time-start)/length;if(phase<=0||phase>=1)return;
        float light=(float)Math.sin(Math.PI*phase);light*=light;
        canvas.save();canvas.translate(x,y);canvas.rotate(11+12*phase);
        canvas.scale(size*light,size*light);paint.setColor(0xfffff3c6);paint.setAlpha(alpha(opacity*.88f*light));
        canvas.drawPath(spark,paint);canvas.restore();
    }
}
