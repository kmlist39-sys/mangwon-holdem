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
    private static final float DISTANCE=69,MAX_PEEK=.94f;
    private static final String HIT="squeezeHole";
    private final Arena a;
    private final String quickLabel;
    private float quickLabelWidth;
    private final Paint stock=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path covered=new Path(),lip=new Path(),cardShape=new Path();
    private final Matrix shadeMatrix=new Matrix();
    private final LinearGradient paper=new LinearGradient(0,0,0,1,
        new int[]{0xff705646,0xffb99f80,0xffead8bb,0xfffbefda},new float[]{0,.24f,.62f,1},Shader.TileMode.CLAMP);
    private final int[] exposed={-1,-1,-1};
    private final float[] cardX=new float[3],cardY=new float[3],cardW=new float[3],cardH=new float[3],cardTilt=new float[3];
    private int hand=-1,pointer=-1,selected=-1,privateFace=-1;
    private String session="";
    private boolean hidden,allowed,discarded,pending;
    private int requestedOption=-1;
    private long optionTime;
    private long previous,opening=-1,sent;
    private float anchorX,anchorY,target,progress;
    private int hintId,hintCount,hintOpen;
    private String hint="";

    HoleSqueeze(Arena arena){a=arena;quickLabel=a.getResources().getString(R.string.squeeze_quick);}
    private void clear(){
        pointer=-1;selected=-1;privateFace=-1;progress=target=0;pending=false;discarded=false;opening=-1;
    }
    void observe(JSONObject game){
        int nextHand=game==null?-1:game.optInt("hand");
        String nextSession=game==null?"":game.optString("sessionId");
        if(nextHand!=hand||!nextSession.equals(session)){
            clear();hidden=false;hand=nextHand;session=nextSession;
            java.util.Arrays.fill(cardW,0);
        }
        boolean nextHidden=game!=null&&game.optBoolean("heroHoleHidden");
        JSONArray visible=game==null?null:game.optJSONArray("heroOpenCards");
        for(int i=0;i<exposed.length;i++)exposed[i]=visible==null?-1:visible.optInt(i,-1);
        boolean folded=false;
        JSONArray seats=game==null?null:game.optJSONArray("p");
        for(int i=0;seats!=null&&i<seats.length();i++){
            JSONObject seat=seats.optJSONObject(i);
            if(seat!=null&&seat.optInt("id")==-1){folded=seat.optBoolean("fold");break;}
        }
        allowed=nextHidden&&!folded&&game.optBoolean("canSqueeze");
        if(pending&&selected>=0&&exposed[selected]>=0&&!folded){
            opening=android.os.SystemClock.uptimeMillis();pending=false;privateFace=-1;
        }
        if(folded||game==null)clear();
        if(!allowed&&pointer!=-1){discarded=true;target=0;}
        hidden=nextHidden;
    }
    void acceptPreview(JSONObject preview){
        if(preview==null||pointer<0||discarded||!valid()||selected<0||
                preview.optInt("index",-1)!=selected||!session.equals(preview.optString("session"))||
                preview.optInt("hand",-1)!=hand)return;
        int card=preview.optInt("card",-1);
        if(card>=0&&card<52){privateFace=card;a.invalidate();}
    }
    boolean covers(){return hidden||opening>=0;}
    void tableSwitch(){
        JSONObject game=a.g;
        if(game==null||!a.panel.isEmpty()||game.optBoolean("showdownTitle"))return;
        boolean checked=a.opt.optBoolean("squeeze");
        String phase=game.optString("phase");
        float left=657,width=165;
        if(a.payouts.presenting(a.now)){left=893;width=165;}
        else if(game.optBoolean("rebuy"))left=517;
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
            if(t==1)clear();return;
        }
        if(pending&&now-sent>1600){pending=false;target=0;}
        float weight=1-(float)Math.exp(-elapsed/(target>progress?23.0:53.0));
        progress+=(target-progress)*weight;if(Math.abs(progress-target)<.002f)progress=target;
        if(pointer<0&&!pending&&progress==0){selected=-1;privateFace=-1;}
    }
    void controls(boolean landed,boolean folded,int count){
        if(landed&&!folded&&selected>=0&&progress>.005f&&(hidden||opening>=0)){
            float reach=Math.min(1,progress/.24f);
            float tipX=cardX[selected]+cardW[selected]*.33f+10+(1-reach)*17;
            float rightEdge=.5f-progress-.09f*(float)Math.sin(progress*Math.PI);
            float tipY=cardY[selected]+cardH[selected]*rightEdge+3+(1-reach)*48;
            int alpha=opening>=0?Math.max(0,Math.min(245,Math.round((1-progress)*1100))):Math.round(245*reach);
            a.c.save();a.c.rotate(-20+progress*8,tipX,tipY);
            a.stage.rightHand(11,0,tipX-21,tipY-10,74,77,alpha);a.c.restore();
        }
        if(!landed||folded||!allowed||!a.panel.isEmpty())return;
        boolean wait=a.g.optBoolean("awaitingSqueeze");
        int label=wait?R.string.squeeze_wait:R.string.squeeze_hint;
        int open=0;for(int i=0;i<count;i++)if(exposed[i]>=0)open++;
        if(label!=hintId||count!=hintCount||open!=hintOpen){
            hintId=label;hintCount=count;hintOpen=open;
            hint=a.getResources().getString(label)+"  "+open+"/"+count;
        }
        float cx=549,half=wait?119:99;
        a.stage.plate(cx-half,432,cx+half,454,0);
        a.stage.bet.fitted(hint,cx,448,half*2-26,12.5f,a.paper,Paint.Align.CENTER);
        a.hit(count==3?464:484,338,count==3?640:623,437,HIT);
    }
    private boolean inTarget(float x,float y){
        for(int i=a.hits.size()-1;i>=0;i--){
            Arena.Hit hit=a.hits.get(i);if(hit.r.contains(x,y))return HIT.equals(hit.key);
        }
        return false;
    }
    private int cardAt(float x,float y,int count){
        for(int i=count-1;i>=0;i--){
            if(cardW[i]<=0)continue;
            double theta=Math.toRadians(cardTilt[i]),cos=Math.cos(theta),sin=Math.sin(theta);
            float dx=x-cardX[i],dy=y-cardY[i];
            double lx=dx*cos+dy*sin,ly=-dx*sin+dy*cos;
            if(Math.abs(lx)<=cardW[i]*.5f&&Math.abs(ly)<=cardH[i]*.5f)return i;
        }
        return -1;
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
            int index=cardAt(x,y,a.g.optBoolean("double")?3:2);
            if(index<0||exposed[index]>=0){a.held="";return true;}
            clear();selected=index;
            pointer=event.getPointerId(0);anchorX=x;anchorY=y;target=0;discarded=false;a.held="";
            a.host.command("squeezePreview","session",session,"hand",hand,"index",selected);
            return true;
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
                    a.host.command("revealHole","session",session,"hand",hand,"index",selected);a.performClick();
                }
            }
        }
        if(action==MotionEvent.ACTION_UP){pointer=-1;if(!pending)target=0;}
        a.invalidate();return true;
    }
    void card(int value,float x,float y,float w,float h,float tilt,int index,int count){
        cardX[index]=x+w/2;cardY[index]=y+h/2;cardW[index]=w;cardH[index]=h;cardTilt[index]=tilt;
        int open=exposed[index]>=0?exposed[index]:value;
        int face=index==selected&&open<0?privateFace:open;
        if(index==selected&&face>=0&&progress>.001f){
            a.heroFace(face,x,y,w,h,tilt);
            drawCover(x,y,w,h,tilt);
        }else if(open>=0)a.heroFace(open,x,y,w,h,tilt);
        else a.card(0,x,y,w,h,tilt,true);
    }
    private void drawCover(float x,float y,float w,float h,float tilt){
        if(progress>=.999f)return;
        // The printed face is already in its final position. Only the back and its curled edge move.
        float bow=(float)Math.sin(progress*Math.PI);
        float left=h*(1-progress),right=left-h*.09f*bow;
        float middle=(left+right)*.5f+h*.045f*bow,depth=h*.062f*bow;
        a.c.save();a.c.rotate(tilt,x+w/2,y+h/2);a.c.translate(x,y);
        cardShape.rewind();cardShape.addRoundRect(0,0,w,h,5,5,Path.Direction.CW);a.c.clipPath(cardShape);
        covered.rewind();covered.moveTo(0,0);covered.lineTo(0,left);
        covered.quadTo(w*.51f,middle,w,right);covered.lineTo(w,0);covered.close();
        a.c.save();a.c.clipPath(covered);a.card(0,0,0,w,h,0,true);a.c.restore();
        if(depth>.3f){
            lip.rewind();lip.moveTo(0,left);lip.quadTo(w*.51f,middle,w,right);
            lip.lineTo(w,right-depth*.5f);lip.quadTo(w*.51f,middle-depth,0,left-depth*.35f);lip.close();
            shadeMatrix.setScale(1,depth+left-right);shadeMatrix.postTranslate(0,right-depth);paper.setLocalMatrix(shadeMatrix);
            stock.setShader(paper);a.c.drawPath(lip,stock);
        }
        a.c.restore();
    }
}
