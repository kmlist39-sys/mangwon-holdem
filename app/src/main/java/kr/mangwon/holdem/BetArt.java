package kr.mangwon.holdem;

import android.graphics.*;
import android.view.WindowInsets;
import android.view.DisplayCutout;
import android.os.Build;
import org.json.*;

/** Betting controls and the table's action and result labels. */
final class BetArt {
    final Arena a;final StageArt stage;final Bitmap plaque,keys;
    final RectF dst=new RectF();final Rect src=new Rect();final Path portrait=new Path();
    static final String[] KEYS={"fold","call","quarter","half","pot","raise","allin"};
    private static final String[] LABELS={"폴드","콜","쿼터","하프","풀","레이즈","올인"};
    private static final int[] ACTIVE_FILL={
        0xff663e43,0xff305c72,0xff2b5955,0xff3d5742,0xff58465e,0xff6a512f,0xff743e3e
    };
    private int safeLeft,safeRight,safeBottom,dockHeight;
    BetArt(StageArt s){stage=s;a=s.a;plaque=a.image("result-plaque.png");keys=a.image("betting-keys.webp");}
    void part(Bitmap b,float l,float t,float r,float d,float x,float y,float xx,float yy,int alpha){
        if(b==null)return;src.set(Math.round(l*b.getWidth()),Math.round(t*b.getHeight()),Math.round(r*b.getWidth()),Math.round(d*b.getHeight()));
        dst.set(x,y,xx,yy);a.reset(-1);a.p.setAlpha(alpha);a.c.drawBitmap(b,src,dst,a.p);
    }
    int dockPixels(){
        safeLeft=safeRight=safeBottom=0;
        WindowInsets insets=a.getRootWindowInsets();
        if(insets!=null){
            int l=0,r=0,b=0;
            if(Build.VERSION.SDK_INT>=28){
                DisplayCutout cut=insets.getDisplayCutout();
                if(cut!=null){l=cut.getSafeInsetLeft();r=cut.getSafeInsetRight();b=cut.getSafeInsetBottom();}
            }
            if(Build.VERSION.SDK_INT>=30){
                android.graphics.Insets nav=insets.getInsets(WindowInsets.Type.navigationBars());
                l=Math.max(l,nav.left);r=Math.max(r,nav.right);b=Math.max(b,nav.bottom);
            }else{
                l=Math.max(l,insets.getSystemWindowInsetLeft());
                r=Math.max(r,insets.getSystemWindowInsetRight());
                b=Math.max(b,insets.getSystemWindowInsetBottom());
            }
            safeLeft=l;safeRight=r;safeBottom=b;
        }
        float scale=Math.min(a.getWidth()/1080f,a.getHeight()/520f);
        dockHeight=(int)Math.ceil(Math.max(48*a.getResources().getDisplayMetrics().density,44*scale));
        return dockHeight+safeBottom;
    }
    static boolean railKey(String key){
        if(key.equals("prefoldOn")||key.equals("prefoldOff"))return true;
        for(String k:KEYS)if(key.equals(k))return true;
        return false;
    }
    boolean tapStillValid(String key,float x,float y){
        JSONObject g=a.g;
        if(g==null||!a.screen.equals("table")||!a.panel.isEmpty()||a.deal.running()
            ||a.pressHand!=g.optInt("hand")||!a.pressSession.equals(g.optString("sessionId")))return false;
        if(key.equals("prefoldOn")){if(!g.optBoolean("canPreFold")||g.optBoolean("preFold"))return false;}
        else if(key.equals("prefoldOff")){if(!g.optBoolean("preFold"))return false;}
        else if(!g.optBoolean("own")||g.optBoolean("preFold"))return false;
        for(int i=a.hits.size()-1;i>=0;i--){
            Arena.Hit hit=a.hits.get(i);if(hit.r.contains(x,y))return key.equals(hit.key);
        }
        return false;
    }
    private void reservationMark(float x,float center,float size,boolean checked){
        float y=center-size/2,edge=Math.max(1,size*.085f);
        a.box(x,y,x+size,y+size,size*.18f,checked?0xffb39c61:0xff192d24);
        a.stroke(x+edge/2,y+edge/2,x+size-edge/2,y+size-edge/2,size*.18f,
            checked?0xfff7df9d:0xffd4c59b,edge);
        if(checked){
            a.line(x+size*.19f,y+size*.50f,x+size*.42f,y+size*.72f,0xff152b20,edge*1.9f);
            a.line(x+size*.42f,y+size*.72f,x+size*.82f,y+size*.28f,0xff152b20,edge*1.9f);
        }
    }
    void rail(){
        JSONObject q=a.g.optJSONObject("quick");
        boolean live=a.panel.isEmpty()&&!a.deal.running()&&!a.g.optBoolean("rebuy")&&!a.g.optBoolean("break");
        boolean queued=a.g.optBoolean("preFold");
        boolean reserve=live&&(queued||a.g.optBoolean("canPreFold"));
        boolean own=live&&a.g.optBoolean("own")&&q!=null&&!queued;
        float screenLeft=-a.offX/a.scale,screenRight=(a.getWidth()-a.offX)/a.scale;
        float screenBottom=(a.getHeight()-a.offY)/a.scale;
        float left=screenLeft+safeLeft/a.scale,right=screenRight-safeRight/a.scale;
        float bottom=screenBottom-safeBottom/a.scale;
        float dp=a.getResources().getDisplayMetrics().density/a.scale;
        float height=dockHeight/a.scale,top=bottom-height;
        float cell=(right-left)/KEYS.length;
        a.box(screenLeft,top,screenRight,screenBottom,0,0xff1d2423);
        a.hit(screenLeft,top,screenRight,screenBottom,"");
        for(int i=0;i<KEYS.length;i++){
            float l=left+i*cell,r=i==KEYS.length-1?right:left+(i+1)*cell,center=(l+r)/2;
            String id=KEYS[i];
            JSONObject bet=q==null?null:q.optJSONObject(id);
            boolean pre=i==0&&reserve;
            boolean enabled=pre||(own&&(i<2||(bet!=null&&bet.optBoolean("enabled"))));
            int paid=bet==null?(i==1?a.g.optInt("call"):0):bet.optInt("pay");
            boolean check=i==1&&own&&paid==0;
            int fill=check?0xff30604e:ACTIVE_FILL[i];
            String hitKey=pre?(queued?"prefoldOff":"prefoldOn"):id;
            boolean pressed=enabled&&hitKey.equals(a.held);
            float textWidth=Math.max(1,cell-5*dp);
            if(keys!=null){
                int key=check?7:i,state=enabled?(pressed?1:0):2;
                stage.tile(keys,4,6,(state*2+key/4)*4+key%4,l,top,r,bottom);
            }else{
                a.box(l,top,r,bottom,0,enabled?fill:0xff262b2d);
                fitted(check?"체크":LABELS[i],center,top+height*.54f,textWidth,height*.49f,
                    enabled?0xfffff4dc:0xff929997,Paint.Align.CENTER);
            }
            if(pre)reservationMark(l+cell*.105f,top+height*.355f,Math.min(height*.245f,cell*.135f),queued);
            String amount=pre?(queued?"예약됨":"미리 폴드"):enabled?(i==5?"최소 ":"")+a.fmt(paid):"—";
            fitted(amount,center,bottom-height*.115f+(pressed?dp:0),textWidth,height*.29f,
                enabled?0xffebe5d9:0xff87908b,Paint.Align.CENTER);
            if(enabled)a.hit(l,top,r,bottom,hitKey);
        }
    }
    void badge(JSONObject who,float x,float top,float maxWidth){
        String action=who.optString("action");if(action.isEmpty())return;
        int space=action.indexOf(' ');
        String label=space>0?action.substring(0,space):action;
        String amount=space>0?action.substring(space+1).trim():"";
        JSONObject move=who.optJSONObject("move");
        if(move!=null){
            String choice=move.optString("choice");
            if(move.optBoolean("allin"))label="올인";
            else if(choice.equals("raise")&&move.optBoolean("opening"))label="베팅";
            else if(choice.equals("quarter"))label="쿼터";
            else if(choice.equals("half"))label="하프";
            else if(choice.equals("pot"))label="풀팟";
            int chips=move.optString("kind").equals("raise")?move.optInt("total"):move.optInt("amount");
            amount=chips>0?a.fmt(chips):"";
        }else if(amount.isEmpty()&&(label.equals("레이즈")||label.equals("올인"))&&who.optInt("bet")>0){
            amount=a.fmt(who.optInt("bet"));
        }
        float font=12.5f,gap=amount.isEmpty()?0:4;
        a.reset(a.paper);a.p.setTypeface(a.bold);a.p.setTextSize(font);
        float labelW=a.p.measureText(label),amountW=a.p.measureText(amount);
        float scale=Math.min(1,(maxWidth-17)/(labelW+gap+amountW));
        font*=scale;labelW*=scale;amountW*=scale;gap*=scale;
        float textWidth=labelW+gap+amountW,width=Math.max(52,textWidth+17);
        stage.plate(x-width/2,top,x+width/2,top+20,0);
        float left=x-textWidth/2;
        a.text(label,left,top+14.5f,font,label.equals("폴드")?a.muted:a.paper,a.bold,Paint.Align.LEFT);
        if(!amount.isEmpty())a.text(amount,left+labelW+gap,top+14.5f,font,a.gold,a.bold,Paint.Align.LEFT);
    }
    void fitted(String value,float x,float y,float width,float font,int color,Paint.Align align){
        a.reset(color);a.p.setTypeface(a.bold);a.p.setTextSize(font);
        float measured=a.p.measureText(value);
        if(measured>width)font*=width/measured;
        a.text(value,x,y,font,color,a.bold,align);
    }
    void result(){
        float left=18,right=838,cx=(left+right)/2;
        part(plaque,.005f,.12f,.995f,.88f,left,447,right,518,255);
        String summary=a.g.optString("summary");
        String[] winners=summary.split("  /  ");
        String first=summary,second="";
        if(winners.length>=5){
            int half=(winners.length+1)/2;
            first=String.join("  /  ",java.util.Arrays.copyOfRange(winners,0,half));
            second=String.join("  /  ",java.util.Arrays.copyOfRange(winners,half,winners.length));
        }
        JSONArray boards=a.g.optJSONArray("boardWins");boolean split=boards!=null&&boards.length()==2;
        fitted(first,cx,second.isEmpty()?(split?477:489):468,right-left-76,19,a.gold,Paint.Align.CENTER);
        if(!second.isEmpty())fitted(second,cx,490,right-left-76,16,a.gold,Paint.Align.CENTER);
        if(split)fitted("위  "+boards.optString(0)+"     ·     아래  "+boards.optString(1),cx,507,right-left-76,14,a.paper,Paint.Align.CENTER);
    }
    void nameplate(int id,String feed){
        String name=id<0?"나":a.people.optJSONObject(id).optString("name");
        int comma=feed.indexOf(',');String description=comma>=0?feed.substring(comma+1).trim():feed;
        float width=760,l=540-width/2,t=447,h=70;
        part(plaque,.005f,.12f,.995f,.88f,l,t,l+width,t+h,255);
        float px=l+19,py=t+12,diam=h-24;
        if(id>=0){portrait.reset();portrait.addRoundRect(new RectF(px,py,px+diam,py+diam),6,6,Path.Direction.CW);
            a.c.save();a.c.clipPath(portrait);a.person(id,px,py,diam,true);a.c.restore();a.stroke(px,py,px+diam,py+diam,6,0xffb19a72,1);}
        else{a.card(0,px+10,py+2,29,45,-9,true);a.card(0,px+22,py+3,29,45,8,true);}
        a.txt(name,px+diam+15,t+29,16,a.gold);
        fitted(description,px+diam+15,t+52,width-diam-65,17,a.paper,Paint.Align.LEFT);
    }
}
