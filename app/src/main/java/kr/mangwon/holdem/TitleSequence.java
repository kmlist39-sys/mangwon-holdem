package kr.mangwon.holdem;

import android.graphics.*;

/** Drawn independently of rule ticks so each layer follows its own continuous motion. */
final class TitleSequence {
    final StageArt stage;
    final Arena a;
    final Bitmap letters, brush, props;
    final Path wipe=new Path();
    final RectF target=new RectF();
    final Rect[] glyph=new Rect[3];
    TitleSequence(StageArt stage){
        this.stage=stage;a=stage.a;
        letters=a.image(a.en?"showdown-en.webp":"show-letters.png");brush=a.image("show-brush.png");props=a.image("show-props.png");
        if(!a.en&&letters!=null)for(int i=0;i<3;i++)glyph[i]=glyphBounds(i);
    }
    // Remove transparent cell padding without trimming the inked outline.
    Rect glyphBounds(int i){
        int l=i*letters.getWidth()/3,r=(i+1)*letters.getWidth()/3,h=letters.getHeight();
        int minX=r,maxX=l,minY=h,maxY=0;
        int[] row=new int[r-l];
        for(int y=0;y<h;y+=3){
            letters.getPixels(row,0,row.length,l,y,row.length,1);
            for(int j=0;j<row.length;j+=3)if(Color.alpha(row[j])>80){
                minX=Math.min(minX,l+j);maxX=Math.max(maxX,l+j);minY=Math.min(minY,y);maxY=Math.max(maxY,y);
            }
        }
        return minX>maxX?new Rect(l,0,r,h):new Rect(Math.max(l,minX-3),Math.max(0,minY-3),Math.min(r,maxX+4),Math.min(h,maxY+4));
    }
    boolean available(){return letters!=null&&brush!=null;}
    static float range(float t,float start,float span){return Math.max(0,Math.min(1,(t-start)/span));}
    static float smooth(float x){return x*x*(3-2*x);}
    static float out(float x){return 1-(1-x)*(1-x)*(1-x);}
    void draw(long elapsed){
        float t=elapsed,enter=out(range(t,0,260)),exit=smooth(range(t,1930,440));
        float visibility=(1-exit)*smooth(range(t,0,120));if(visibility<=.001f)return;
        a.box(0,0,1080,520,0,((int)(99*visibility)<<24)|0x0c1711);
        a.c.saveLayerAlpha(90,65,1020,425,Math.round(255*(1-exit)));
        a.c.translate(32*exit,-15*exit);
        // A diagonal paint edge leads the lettering. The swash continues to widen during the hold.
        float edge=145+790*out(range(t,0,490));
        wipe.reset();wipe.moveTo(110,95);wipe.lineTo(edge+54,95);wipe.lineTo(edge-50,405);wipe.lineTo(110,405);wipe.close();
        a.c.save();a.c.clipPath(wipe);
        float breath=(float)Math.sin(Math.min(t,1460)/1460f*Math.PI);
        a.c.rotate(-1.8f*(1-enter)+.55f*breath,540,244);
        a.c.scale(.86f+.14f*enter+.025f*breath,.79f+.21f*enter+.04f*breath,540,245);
        a.photo(brush,205,124,876,365);a.c.restore();
        // Ink and physical playing pieces travel on different arcs behind the type.
        prop(2,t,95,355,218,-49,-27,-19,118);
        prop(3,t,155,724,253,56,18,17,123);
        prop(0,t,100,451,161,-44,-30,-25,60);
        prop(1,t,205,657,303,40,24,19,49);
        prop(0,t,170,385,285,-72,15,-36,43);
        if(a.en){
            float u=out(range(t,115,510));
            float birth=1-u;
            a.c.save();a.c.translate(-26*birth,-17*birth);a.c.rotate(-4*birth,540,242);
            a.c.scale(.86f+.14f*u,.86f+.14f*u,540,242);
            target.set(235,182,845,302);a.reset(-1);a.p.setAlpha(Math.round(255*u));
            a.c.drawBitmap(letters,null,target,a.p);a.c.restore();
        }else for(int i=0;i<3;i++){
            float time=range(t,115+i*72,465);
            if(time<=0)continue;
            float fly=out(time),land=(float)Math.sin(Math.PI*time)*(.98f-time);
            float cx=397+i*144,cy=244+(i==1?2:-2);
            float scale=.83f+.17f*fly+.16f*land;
            float dx=-(1-fly)*(51-i*8),dy=-(1-fly)*(38+i*6)+3*land;
            a.c.save();a.c.translate(dx,dy);a.c.rotate(-(1-fly)*(10-i*2)+land*2,cx,cy);
            a.c.scale(scale,scale,cx,cy);
            Rect source=glyph[i];float ratio=source.width()/(float)source.height();
            float gh=Math.min(150,156/ratio),gw=gh*ratio;
            target.set(cx-gw/2,cy-gh/2,cx+gw/2,cy+gh/2);
            a.reset(-1);a.p.setAlpha(Math.round(255*out(range(time,0,.42f))));
            a.c.drawBitmap(letters,source,target,a.p);a.c.restore();
        }
        a.c.restore();
    }
    void prop(int cell,float t,float delay,float x,float y,float dx,float dy,float angle,float size){
        float k=range(t,delay,700);if(k==0)return;
        float f=out(k),hang=(float)Math.sin(k*Math.PI);
        float cx=x+dx*f,cy=y+dy*f-24*hang;
        a.c.save();a.c.rotate(angle*f-24*(1-f)+hang*11,cx,cy);
        float scale=.4f+.6f*f;a.c.scale(scale*(1-.28f*hang),scale,cx,cy);
        stage.tile(props,2,2,cell,cx-size/2,cy-size/2,cx+size/2,cy+size/2);a.c.restore();
    }
}
