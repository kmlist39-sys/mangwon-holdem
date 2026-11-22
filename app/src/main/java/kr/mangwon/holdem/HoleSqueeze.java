package kr.mangwon.holdem;

import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.view.MotionEvent;
import org.json.JSONArray;
import org.json.JSONObject;

/** The bottom edge of the hero's cards follows one upward drag. Release commits the reveal. */
final class HoleSqueeze {
    private static final float DISTANCE=69,MAX_PEEK=.78f;
    private static final String HIT="squeezeHole";
    private final Arena a;
    private final String quickLabel;
    private float quickLabelWidth;
    private final Paint stock=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path cover=new Path(),lip=new Path();
    private final Matrix shadeMatrix=new Matrix();
    private final LinearGradient paper=new LinearGradient(0,0,0,1,
        new int[]{0xff705646,0xffb99f80,0xffead8bb,0xfffbefda},new float[]{0,.24f,.62f,1},Shader.TileMode.CLAMP);
    private int hand=-1,pointer=-1;
    private String session="";
    private boolean hidden,allowed,discarded,pending;
    private int requestedOption=-1;
    private long optionTime;
    private long previous,opening=-1,sent;
    private float anchorX,anchorY,target,progress;

    HoleSqueeze(Arena arena){a=arena;quickLabel=a.getResources().getString(R.string.squeeze_quick);}
    private void clear(){
        pointer=-1;progress=target=0;pending=false;discarded=false;opening=-1;
    }
    void observe(JSONObject game){
        int nextHand=game==null?-1:game.optInt("hand");
        String nextSession=game==null?"":game.optString("sessionId");
        if(nextHand!=hand||!nextSession.equals(session)){clear();hidden=false;hand=nextHand;session=nextSession;}
        boolean nextHidden=game!=null&&game.optBoolean("heroHoleHidden");
        boolean folded=false;
        JSONArray seats=game==null?null:game.optJSONArray("p");
        for(int i=0;seats!=null&&i<seats.length();i++){
            JSONObject seat=seats.optJSONObject(i);
            if(seat!=null&&seat.optInt("id")==-1){folded=seat.optBoolean("fold");break;}
        }
        allowed=nextHidden&&!folded&&game.optBoolean("canSqueeze");
        if(hidden&&!nextHidden&&pending&&!folded){opening=android.os.SystemClock.uptimeMillis();pending=false;}
        if(folded||game==null)clear();
        if(!allowed&&pointer!=-1){discarded=true;target=0;}
        hidden=nextHidden;
    }
    boolean covers(){return hidden||opening>=0;}
    void tableSwitch(){
        JSONObject game=a.g;
        if(game==null||!a.panel.isEmpty()||game.optBoolean("showdownTitle"))return;
        boolean checked=a.opt.optBoolean("squeeze");
        String phase=game.optString("phase");
        float left=657,width=165;
        if(game.optBoolean("rebuy"))left=517;
        else if("done".equals(phase)&&game.optJSONObject("cashFunding")!=null){left=482;width=153;}
        else if("showdown".equals(phase)&&game.optInt("show")>0&&!game.optBoolean("awaitingSqueeze")){left=925;width=143;}
        float y="tableSqueeze".equals(a.held)?484:483;
        if(quickLabelWidth==0){
            a.reset(a.paper);a.p.setTypeface(a.bold);a.p.setTextSize(14);
            quickLabelWidth=a.p.measureText(quickLabel);
        }
        float body=Math.min(width-8,quickLabelWidth+49),start=left+(width-body)/2;
        a.box(start,y-14,start+body,y+14,6,checked?0xf123342b:0xe91d2521);
        a.stroke(start,y-14,start+body,y+14,6,checked?0xff99805a:0xff5a5648,.85f);
        a.stage.bet.reservationMark(start+12,y,15,checked);
        a.stage.bet.fitted(quickLabel,start+36,y+5,body-47,14,checked?a.paper:a.muted,Paint.Align.LEFT);
        a.hit(left,451,left+width,514,"tableSqueeze");
    }
    void toggle(boolean atTable){
        if(atTable&&(a.g==null||!a.screen.equals("table")||!a.panel.isEmpty()||
                !a.pressSession.equals(a.g.optString("sessionId"))))return;
        long now=android.os.SystemClock.uptimeMillis();
        int current=a.opt.optBoolean("squeeze")?1:0;
        if(requestedOption!=-1&&requestedOption!=current&&now-optionTime<1200)return;
        requestedOption=1-current;optionTime=now;
        a.host.command("option","name","squeeze");
        a.note(a.getResources().getString(current==0?R.string.squeeze_next:R.string.squeeze_auto));
    }
    void cancel(){if(pointer>=0){discarded=true;target=0;pointer=-1;}}
    void update(long now,boolean active){
        long elapsed=Math.min(60,Math.max(0,now-previous));previous=now;
        if(!active&&pointer!=-1){discarded=true;target=0;}
        if(opening>=0){
            float t=Math.min(1,(now-opening)/205f);progress=MAX_PEEK+(1-MAX_PEEK)*t*(2-t);
            if(t==1){opening=-1;target=progress=0;}return;
        }
        if(pending&&now-sent>1600){pending=false;target=0;}
        float weight=1-(float)Math.exp(-elapsed/(target>progress?23.0:53.0));
        progress+=(target-progress)*weight;if(Math.abs(progress-target)<.002f)progress=target;
    }
    void controls(boolean landed,boolean folded,int count){
        if(landed&&!folded&&progress>.005f&&(hidden||opening>=0)){
            float reach=Math.min(1,progress/.42f);
            float tipX=(count==3?626:607)+(1-reach)*22;
            float tipY=423+(1-reach)*78-progress*48;
            int alpha=opening>=0?Math.max(0,Math.min(245,Math.round((1-progress)*1100))):Math.round(245*reach);
            a.c.save();a.c.rotate(-20+progress*8,tipX,tipY);
            a.stage.rightHand(11,0,tipX-43,tipY-17,84,88,alpha);a.c.restore();
        }
        if(!landed||folded||!allowed||!a.panel.isEmpty())return;
        boolean wait=a.g.optBoolean("awaitingSqueeze");
        int label=wait?R.string.squeeze_wait:R.string.squeeze_hint;
        float cx=count==3?549:550;
        a.stage.plate(cx-(wait?106:78),427,cx+(wait?106:78),450,0);
        a.stage.bet.fitted(a.getResources().getString(label),cx,443,wait?183:135,12.5f,a.paper,Paint.Align.CENTER);
        // The lower halves of all dealt hole cards and their label share a touch target.
        a.hit(count==3?470:488,348,count==3?638:622,450,HIT);
    }
    private boolean inTarget(float x,float y){
        for(int i=a.hits.size()-1;i>=0;i--){
            Arena.Hit hit=a.hits.get(i);if(hit.r.contains(x,y))return HIT.equals(hit.key);
        }
        return false;
    }
    private boolean valid(){
        return allowed&&a.g!=null&&a.host.active&&a.screen.equals("table")&&a.panel.isEmpty()&&
            a.g.optInt("hand")==hand&&a.g.optString("sessionId").equals(session);
    }
    boolean touch(MotionEvent event){
        int action=event.getActionMasked();
        if(action==MotionEvent.ACTION_DOWN){
            float x=(event.getX()-a.offX)/a.scale,y=(event.getY()-a.offY)/a.scale;
            if(!valid()||pending||!inTarget(x,y))return false;
            pointer=event.getPointerId(0);anchorX=x;anchorY=y;target=0;discarded=false;a.held="";return true;
        }
        if(pointer<0)return false;
        if(action==MotionEvent.ACTION_CANCEL||action==MotionEvent.ACTION_POINTER_DOWN||
                action==MotionEvent.ACTION_POINTER_UP&&event.getPointerId(event.getActionIndex())==pointer){
            discarded=true;target=0;if(action==MotionEvent.ACTION_CANCEL)pointer=-1;return true;
        }
        int index=event.findPointerIndex(pointer);
        if(index<0||!valid()){discarded=true;target=0;}
        if(!discarded&&(action==MotionEvent.ACTION_MOVE||action==MotionEvent.ACTION_UP)){
            float dx=(event.getX(index)-a.offX)/a.scale-anchorX,dy=anchorY-(event.getY(index)-a.offY)/a.scale;
            if(Math.abs(dx)>Math.max(37,Math.max(0,dy)*.95f)){discarded=true;target=0;}
            else{
                float f=Math.max(0,Math.min(1,(dy-3)/(DISTANCE-3)));target=f*MAX_PEEK;
                if(action==MotionEvent.ACTION_UP&&f>=1){
                    pending=true;progress=target=MAX_PEEK;sent=android.os.SystemClock.uptimeMillis();
                    a.host.command("revealHole","session",session,"hand",hand);a.performClick();
                }
            }
        }
        if(action==MotionEvent.ACTION_UP){pointer=-1;if(!pending)target=0;}
        a.invalidate();return true;
    }
    void card(int value,float x,float y,float w,float h,float tilt,int index,int count){
        // The sealed snapshot carries no rank or suit. The fold has only unprinted stock until the engine replies.
        if(progress<=0){a.card(0,x,y,w,h,tilt,true);return;}
        float f=Math.min(1,progress*(1+.027f*(index-count*.5f)));
        a.c.save();a.c.rotate(tilt,x+w/2,y+h/2);a.c.translate(x,y);
        if(!hidden&&value>=0)a.card(value,0,0,w,h,0,false);
        else{a.box(1,3,w+1,h+3,5,0x8a10110c);a.box(0,0,w,h,5,0xfff0e3cb);}
        float left=h*(1-f),right=h*(1-Math.min(1,f*1.17f)),curve=w*.073f*(float)Math.sin(f*Math.PI);
        float depth=Math.min(h*.115f,Math.min(f,1-f)*h*.85f),end=(left+right)*.5f;
        cover.rewind();cover.moveTo(-1,-1);cover.lineTo(w+1,-1);cover.lineTo(w+1,right);
        cover.quadTo(w*.53f,end+curve,-1,left);cover.close();
        a.c.save();a.c.clipPath(cover);a.card(0,0,0,w,h,0,true);a.c.restore();
        if(depth>.3f){
            lip.rewind();lip.moveTo(0,left);lip.quadTo(w*.53f,end+curve,w,right);
            lip.lineTo(w-.9f,right+depth*.73f);lip.quadTo(w*.53f,end+curve+depth,1,left+depth*.34f);lip.close();
            shadeMatrix.setScale(1,depth);shadeMatrix.postTranslate(0,Math.min(left,right));paper.setLocalMatrix(shadeMatrix);
            stock.setShader(paper);a.c.drawPath(lip,stock);
        }
        a.c.restore();
    }
}
