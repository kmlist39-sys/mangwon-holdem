package kr.mangwon.holdem;

import android.graphics.Bitmap;
import android.graphics.LinearGradient;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;

/** Motion remains inside the back wall of each painted room. */
final class VenueScene {
    static final int NIGHT=0,SUNSET=1,RIVER=2,HALL=3;
    private final Arena a;
    private int cached=-1;
    private Bitmap room, moving;
    private static final int COLUMNS=26,ROWS=8;
    private final float[] vertices=new float[(COLUMNS+1)*(ROWS+1)*2];
    private final float[] origin=new float[vertices.length],xWave=new float[vertices.length],
        secondXWave=new float[vertices.length],yWave=new float[vertices.length];
    private final Path window=new Path(),fan=new Path();
    private final Rect rotorSource=new Rect();
    private final RectF rotorDestination=new RectF();
    private final RadialGradient glow=new RadialGradient(0,0,1,0xc7ffd684,0x00ffad52,Shader.TileMode.CLAMP);
    private final Path[] stageLights={new Path(),new Path()};
    private final LinearGradient beam=new LinearGradient(0,27,0,133,
        new int[]{0x06fbe8bd,0x19ebd3ad,0x0ce8bd87,0x00e6bd87},new float[]{0,.26f,.76f,1},Shader.TileMode.CLAMP);
    private float left,top,right,bottom;

    VenueScene(Arena a){
        this.a=a;fan.addCircle(632,42,13,Path.Direction.CW);
        for(int i=0;i<2;i++){
            float source=i==0?445:635,target=i==0?512:570;
            Path light=stageLights[i];light.moveTo(source-1,27);light.lineTo(source+1,27);
            light.lineTo(target+27,133);light.lineTo(target-27,133);light.close();
        }
    }
    static int roomFor(int pub,int entrants){
        if(pub==0)return entrants>=27?SUNSET:NIGHT;
        return pub==1?HALL:RIVER;
    }
    int sceneId(){
        if(a.screen.equals("table")&&a.g!=null&&!a.panel.equals("entry"))
            return roomFor(a.g.optInt("pub"),a.g.optInt("entrants"));
        return roomFor(a.venue,a.selectedTournamentTables()*9);
    }
    Bitmap backdrop(){return backdrop(sceneId());}
    Bitmap backdrop(int venue){
        if(venue==NIGHT)return a.table;
        if(venue!=cached){
            cached=venue;room=a.image(venue==SUNSET?"seongsan-room.jpg":venue==HALL?"mapo-room.jpg":"dangsan-room.jpg");
            moving=null;window.reset();
            if(room!=null&&venue!=HALL){
                left=venue==1?108:138;top=venue==1?0:108;
                right=venue==1?559:857;bottom=venue==1?107:151;
                int l=Math.round(left*room.getWidth()/1080),t=Math.round(top*room.getHeight()/520);
                int r=Math.round(right*room.getWidth()/1080),b=Math.round(bottom*room.getHeight()/520);
                moving=Bitmap.createBitmap(room,l,t,r-l,b-t);
                prepareMesh(venue);
                if(venue==1){
                    float sx=room.getWidth()/1080f,sy=room.getHeight()/520f;
                    rotorSource.set((int)(617*sx),(int)(27*sy),(int)Math.ceil(647*sx),(int)Math.ceil(57*sy));
                    rotorDestination.set(rotorSource.left/sx,rotorSource.top/sy,rotorSource.right/sx,rotorSource.bottom/sy);
                }
                if(venue==1)panes(new int[]{114,202,292,378,470},new int[]{194,282,368,456,546},0,114);
                else panes(new int[]{139,219,331,444,564,671,798},new int[]{202,312,427,548,653,783,854},58,155);
            }
        }
        return room!=null?room:a.table;
    }
    private void panes(int[] xs,int[] ends,int t,int b){
        for(int i=0;i<xs.length;i++)window.addRect(xs[i],t,ends[i],b,Path.Direction.CW);
    }
    private static void phase(float[] wave,int i,float amplitude,float phase){
        wave[i]=amplitude*(float)Math.cos(phase);
        wave[i+1]=amplitude*(float)Math.sin(phase);
    }
    private void prepareMesh(int venue){
        for(int j=0;j<=ROWS;j++)for(int i=0;i<=COLUMNS;i++){
            float u=i/(float)COLUMNS,v=j/(float)ROWS,x=left+(right-left)*u,y=top+(bottom-top)*v;
            float edge=(float)(Math.sin(Math.PI*u)*Math.sin(Math.PI*v));
            int k=(j*(COLUMNS+1)+i)*2;
            origin[k]=x;origin[k+1]=y;
            if(venue==1){
                phase(xWave,k,3.2f*edge,u*7);phase(secondXWave,k,1.4f*edge,-v*2);
                phase(yWave,k,1.6f*edge,u*9+v*3);
            }else{
                phase(xWave,k,edge*(2.8f+v*1.6f),v*22+u*8);
                secondXWave[k]=0;secondXWave[k+1]=0;
                phase(yWave,k,.85f*edge,v*19+u*4);
            }
        }
    }
    void draw(int venue,long elapsed){
        if(venue==HALL){
            if(cached==HALL&&room!=null)hallLights((elapsed%240000)/1000f);
            return;
        }
        if(moving==null||cached!=venue)return;
        float t=(elapsed%120000)/1000f;
        double x=t*(venue==1?1.24:1.62),y=t*(venue==1?1.32:2.03);
        float sinX=(float)Math.sin(x),cosX=(float)Math.cos(x),sinY=(float)Math.sin(y),cosY=(float)Math.cos(y);
        float sin2=venue==1?(float)Math.sin(t*2.05):0,cos2=venue==1?(float)Math.cos(t*2.05):0;
        for(int k=0;k<vertices.length;k+=2){
            vertices[k]=origin[k]+sinX*xWave[k]+cosX*xWave[k+1]+sin2*secondXWave[k]+cos2*secondXWave[k+1];
            vertices[k+1]=origin[k+1]+sinY*yWave[k]+cosY*yWave[k+1];
        }
        a.c.save();a.c.clipPath(window);a.reset(-1);
        a.c.drawBitmapMesh(moving,COLUMNS,ROWS,vertices,0,null,0,a.p);
        if(venue==2)traffic(t);
        a.c.restore();
        if(venue==1){
            // Rotate the small painted rotor inside its stationary brass frame.
            float cx=632,cy=42;
            a.c.save();a.c.clipPath(fan);a.c.rotate((elapsed%6000)*.14f,cx,cy);
            a.reset(-1);a.c.drawBitmap(room,rotorSource,rotorDestination,a.p);a.c.restore();
            a.line(cx-14,cy,cx+14,cy,0x78594428,1);
            a.line(cx,cy-12,cx,cy+13,0x79594428,1);
        }
        float cx=venue==1?942:1033,cy=venue==1?40:39;
        a.c.save();a.c.translate(cx,cy);a.c.scale(venue==1?59:33,venue==1?36:48);
        a.reset(-1);a.p.setShader(glow);a.p.setAlpha((int)(26+9*Math.sin(t*1.27)+5*Math.sin(t*2.43)));
        a.c.drawCircle(0,0,1,a.p);a.c.restore();
    }
    private void hallLights(float time){
        // Truss and pendant positions are measured in the painted venue. Keep
        // every light on the back wall above the playing rail.
        a.c.save();a.c.clipRect(0,0,1080,145);
        for(int i=0;i<stageLights.length;i++){
            a.c.save();a.c.rotate((float)Math.sin(time*.27+i*2.7)*1.65f,i==0?445:635,27);
            a.reset(-1);a.p.setShader(beam);a.c.drawPath(stageLights[i],a.p);a.c.restore();
        }
        for(int i=0;i<3;i++){
            float x=i==0?137:i==1?944:542,y=i==2?122:54;
            a.c.save();a.c.translate(x,y);a.c.scale(i==2?75:46,i==2?12:32);
            a.reset(-1);a.p.setShader(glow);
            a.p.setAlpha((int)(27+5*Math.sin(time*.53+i*1.7)));
            a.c.drawCircle(0,0,1,a.p);a.c.restore();
        }
        a.c.restore();
    }
    private void traffic(float t){
        for(int k=0;k<5;k++){
            float x=125+(t*(k%2==0?33:40)+k*179)%790;
            if(k%2==1)x=1048-x;
            float y=k%2==0?86:89;int color=k%2==0?0xfff6d38d:0xffda7048;
            a.line(x-6,y,x+3,y,0xb4312d2b,2.2f);
            a.line(x-2,y-.6f,x+2,y-.6f,color,1.6f);
            a.line(x-4,y+1,x-3,y+1,0xffd18f55,1);
        }
    }
}
