package kr.mangwon.holdem;

import android.content.*;
import android.graphics.*;
import android.os.SystemClock;
import android.view.*;
import org.json.*;
import java.io.*;
import java.util.*;

public final class Arena extends View {
 static final String LOBBY_SLOGAN="동네에서 즐기는 방식 그대로";
 final PubDisplay display; final Cashier cashier; final PotReceipt receipt=new PotReceipt();
 final WashMotion washMotion=new WashMotion(this);
 final SettlementMotion payouts=new SettlementMotion(this);
 final FoldMotion folds=new FoldMotion(this);
 final LobbyIntro intro=new LobbyIntro(this);
 final TournamentPodium podium=new TournamentPodium(this);
 final AttendanceDesk attendance=new AttendanceDesk(this);
 final BlindTicker blindTicker=new BlindTicker(this);
 private long pocketTurnSampleAt;
 String studyReturn="";
 final WagerDisplay wages=new WagerDisplay(this);final NameplateClock nameClock=new NameplateClock(this);
 final CastArt castArt=new CastArt(this); final StageArt stage; final PubMenu pubMenu; final CharacterProfile character; String profileReturn=""; final long[] foldStarted=new long[CastArt.COUNT+1]; final CommunityDeal community=new CommunityDeal(this); final GameActivity host; Canvas c; final Paint p=new Paint(3); final RectF rect=new RectF();
 Bitmap pub,table,faces,calm,busts,chipArt; long feeChangedAt; final DealAnimation deal=new DealAnimation(this);  int fee=50000,cashSeats=6,cashLevel=0; final int[] tablesChoice={2,12}; JSONObject snapshot=new JSONObject(),g,opt=new JSONObject(); JSONArray people=new JSONArray();
 final int ink=0xff101b19,paper=0xffffedcb,gold=0xffefbd74,muted=0xffb9bbaa,green=0xff244c3d,red=0xffa14638;
 final Typeface normal=clubFace("club-sans",500),bold=clubFace("club-sans",695),
     title=clubFace("club-serif",740),nums=bold;
 private Typeface clubFace(String family,int weight){
     return new Typeface.Builder(getContext().getAssets(),"type/"+family+".ttf")
         .setFontVariationSettings("'wght' "+weight).build();
 }
 final String[] pubs={"망원동 홀덤펍","마포구 홀덤대회","당산 홀덤펍"},latin={"MANGWON","MAPO","DANGSAN"},streets={"프리플랍","플랍","턴","리버"};
 private static final String[] RANKS={"2","3","4","5","6","7","8","9","10","J","Q","K","A"}, SUITS={"♠","♥","♣","♦"};
 final CardBack cardBack=new CardBack();
 private final Paint lettering=new Paint(Paint.ANTI_ALIAS_FLAG),fills=new Paint(Paint.ANTI_ALIAS_FLAG),
     outlines=new Paint(Paint.ANTI_ALIAS_FLAG),lines=new Paint(Paint.ANTI_ALIAS_FLAG);
 private final LinearGradient hudTop=new LinearGradient(0,0,0,72,0xfc111c17,0x07111c17,Shader.TileMode.CLAMP);
 private final LinearGradient hudBottom=new LinearGradient(0,409,0,520,0x00101814,0xfa101813,Shader.TileMode.CLAMP);
 final SeatActing acting;
 final TournamentBanner tournamentBanner;
 private final ArrayList<Hit> hitPool=new ArrayList<>();
 private final android.util.LongSparseArray<String> numbers=new android.util.LongSparseArray<>();
 private final java.text.NumberFormat commaFormat=java.text.NumberFormat.getIntegerInstance(Locale.US);
 final int[] artMap={0,1,2,3,5,6,8,7,12,9,10,11,17,14,15,16,22,13,27,21,20,23,25,4,29,28,24,31,26,33,30,35,32,37,34,39,36,41,38,43,42,48,40,46,45,44,19,18,47,49};

 final ArrayList<Hit> hits=new ArrayList<>(); String screen="lobby",panel="",toast="",prevPhase="",lastFeed="";int venue=0,buy=200000,profile=0,page=0,lastHand=-1,lastStreet=-1,raise=0,chipSeat=-2,focused=-2;boolean dbl=false,shopFromEntry=false;
 long now,born=SystemClock.uptimeMillis(),dealAt,boardAt,phaseAt,chipAt,focusAt,toastEnd;float scale=1,offX,offY,pressX,pressY,peel;String held="",pressSession="";int pressHand;boolean drag;
 static class Hit{RectF r;String key;Hit(float l,float t,float r,float b,String k){this.r=new RectF(l,t,r,b);key=k;}}
 Arena(GameActivity a){super(a);host=a;pub=image("title.jpg");pubMenu=new PubMenu(this);table=image("nine-room.jpg");busts=image("busts.png");deal.dealer=image("dealer.png");chipArt=image("chips.png");faces=image("faces.jpg");calm=image("calm.jpg");stage=new StageArt(this);acting=new SeatActing(this);tournamentBanner=new TournamentBanner(this);character=new CharacterProfile(this);display=new PubDisplay(this);cashier=new Cashier(this);setFocusable(true);}
 Bitmap image(String n){try(InputStream in=host.getAssets().open(n)){return BitmapFactory.decodeStream(in);}catch(IOException e){return null;}}
 String rebuySoundSession="";
 boolean rebuyWarningVisible(){return g!=null&&screen.equals("table")&&panel.isEmpty()&&g.optBoolean("rebuyNoticePending");}
 boolean tableOpen(){return screen.equals("table")&&(panel.isEmpty()||panel.equals("tables")||panel.equals("wagers")||panel.equals("receipt")||panel.equals("profile"))&&g!=null&&!rebuyWarningVisible();}
 boolean live(){return tableOpen()&&!deal.running();}
 boolean back(){if(pubMenu.arrival.back())return true;if(attendance.dismiss())return true;if(rebuyWarningVisible()){host.command("ackRebuyNotice","session",g.optString("sessionId"));return true;}if(intro.blocking())return intro.back();if(tournamentBanner.showing())return true;if(panel.equals("shop"))host.rewardAds.leave();if(!panel.isEmpty()){if(panel.equals("surrender"))panel="pause";else if(panel.equals("gtoSample")||panel.equals("gtoReview"))panel="gtoStore";else if(panel.equals("gtoStore"))panel=studyReturn;else if(panel.equals("profile"))panel=profileReturn;else if(panel.equals("confirm"))panel="shop";else if(panel.equals("shop")&&shopFromEntry){shopFromEntry=false;panel="entry";}else panel="";invalidate();return true;}if(screen.equals("table")){panel="pause";invalidate();return true;}return false;}
 String fmt(long n){
     String value=numbers.get(n);
     if(value==null){if(numbers.size()>=256)numbers.clear();value=commaFormat.format(n);numbers.put(n,value);}
     return value;
 }
 String shortP(int n){return n%10000==0?n/10000+"만":fmt(n);}
 void note(String s){toast=s;toastEnd=SystemClock.uptimeMillis()+2300;}
 JSONObject savedVenue(int pub){
     JSONArray sessions=snapshot.optJSONArray("sessions");
     return sessions==null?null:sessions.optJSONObject(pub);
 }
 boolean enterExistingVenue(int pub){
     JSONObject seat=savedVenue(pub);if(seat==null)return false;
     if(g!=null&&seat.optString("sessionId").equals(g.optString("sessionId"))){
         screen="table";panel="";host.lastTick=SystemClock.uptimeMillis();
     }else host.command("resumeVenue","pub",pub,"session",seat.optString("sessionId"),"fromLobby",true);
     invalidate();return true;
 }
 void accept(JSONObject v){try{JSONObject old=g;snapshot=v;g=v.optJSONObject("g");opt=v.getJSONObject("opt");String msg=v.optString("notice");if(!msg.isEmpty())note(msg);
 String changed=v.optString("venueChange");boolean resumed=changed.equals("resumed");
 if(changed.equals("parked")){screen="lobby";panel="";hits.clear();held="";}
 else if(changed.equals("denied")){screen="lobby";panel="entry";}
 else if(changed.equals("parkFailed")){screen="table";panel="pause";}
 else if(g!=null&&(resumed||changed.equals("entered"))){screen="table";panel="";venue=g.optInt("pub");host.lastTick=SystemClock.uptimeMillis();}
 else if(old!=null&&g==null){screen="lobby";panel="result";}
 if(old!=null&&g!=null&&!g.optString("sessionId").equals(old.optString("sessionId"))){acting.clear();old=null;}
 pocketTurnSampleAt=SystemClock.uptimeMillis();
 tournamentBanner.observe(g);podium.observe(v);attendance.observe(v);
 blindTicker.observe(g,pocketTurnSampleAt);
 if(old!=null&&g==null)acting.clear();
 washMotion.observe(old,g,SystemClock.uptimeMillis(),host.active&&screen.equals("table")&&panel.isEmpty());
 stage.rebuys.observe(old,g,v.optJSONArray("tournamentEvents"),SystemClock.uptimeMillis());
 if(g!=null){stage.onSnapshot(old,g);int h=g.optInt("hand"),st=g.optInt("street");String ph=g.optString("phase"),feed=g.optString("feed");long t=SystemClock.uptimeMillis();payouts.observe(old,g,t);
 boolean newHand=old==null||h!=lastHand||!g.optString("sessionId").equals(old.optString("sessionId"));
 if(newHand){dealAt=t;boardAt=resumed?t-4000:t;deal.begin(g,!resumed&&screen.equals("table"));chipSeat=-2;lastHand=h;Arrays.fill(foldStarted,0);peel=0;raise=0;if(!resumed&&opt.optBoolean("sound"))host.fx("deal",.65f);}
 folds.observe(old,g,t,newHand);
 acting.observe(g,t,newHand);
 if(st!=lastStreet){boardAt=resumed?t-4000:t;lastStreet=st;if(!resumed&&opt.optBoolean("sound"))host.fx("deal",.8f);}
 boolean enteringTitle=g.optBoolean("showdownTitle")&&(old==null||h!=old.optInt("hand")||!old.optBoolean("showdownTitle"));
 if(!ph.equals(prevPhase)||enteringTitle){phaseAt=enteringTitle?t-g.optLong("showdownTitleMs",0):t;prevPhase=ph;}
 if(enteringTitle&&old!=null&&g.optLong("showdownTitleMs",0)<400&&opt.optBoolean("sound"))
     host.fx("show",opt.optBoolean("voice",true)&&host.dealerVoice.ready()?.15f:.8f);
 if(old!=null&&old.optInt("actor")!=g.optInt("actor"))raise=0;
 if(!newHand&&!feed.equals(lastFeed)){JSONArray os=old.optJSONArray("p"),ps=g.optJSONArray("p");for(int i=0;i<Math.min(os.length(),ps.length());i++){JSONObject aa=os.optJSONObject(i),bb=ps.optJSONObject(i);if(aa.optInt("id")==bb.optInt("id")&&bb.optInt("bet")>aa.optInt("bet")){chipSeat=i;chipAt=t;if(opt.optBoolean("sound"))host.fx("chip",.65f);break;}}}lastFeed=feed;
 JSONObject cs=g.optJSONObject("cashStats"),priorCs=old==null?null:old.optJSONObject("cashStats");
 if(cs!=null&&priorCs!=null&&cs.optInt("washedAt")>priorCs.optInt("washedAt")){
     JSONObject wash=cs.optJSONObject("lastWash");int credited=wash==null?0:wash.optInt("heroAmount");
     if(credited==0)note("30핸드 워싱 완료");
 }
 String said=v.optString("spoken");if(!said.isEmpty()&&opt.optBoolean("voice"))host.line(said);
 }invalidate();}catch(Exception e){android.util.Log.e("Mangwon","Snapshot",e);}}
 void reset(int col){p.reset();p.setAntiAlias(true);p.setFilterBitmap(true);p.setColor(col);}
 void box(float l,float t,float r,float b,float rd,int col){fills.setColor(col);c.drawRoundRect(l,t,r,b,rd,rd,fills);}
 void stroke(float l,float t,float r,float b,float rd,int col,float width){outlines.setColor(col);outlines.setStyle(Paint.Style.STROKE);outlines.setStrokeWidth(width);c.drawRoundRect(l,t,r,b,rd,rd,outlines);}
 void line(float x,float y,float xx,float yy,int col,float w){lines.setColor(col);lines.setStrokeWidth(w);c.drawLine(x,y,xx,yy,lines);}
 void text(String s,float x,float y,float size,int co,Typeface font,Paint.Align align){lettering.setColor(co);lettering.setTypeface(font);lettering.setTextSize(size);lettering.setTextAlign(align);c.drawText(s,x,y,lettering);}
 void txt(String s,float x,float y,float z,int col){text(s,x,y,z,col,normal,Paint.Align.LEFT);}
 void mid(String s,float x,float y,float z,int col){text(s,x,y,z,col,bold,Paint.Align.CENTER);}
 void wrapped(String s,float x,float y,float width,float size,int col,int limit){reset(col);p.setTypeface(normal);p.setTextSize(size);String rem=s;for(int k=0;k<limit&&!rem.isEmpty();k++){int n=p.breakText(rem,true,width,null);if(n<=0)break;String sub=rem.substring(0,n);if(n<rem.length()&&sub.lastIndexOf(' ')>n*.55)n=sub.lastIndexOf(' ');txt(rem.substring(0,n),x,y+k*(size+7),size,col);rem=rem.substring(n).trim();}}
 void grad(float l,float t,float r,float b,int a,int z,boolean vert){reset(-1);p.setShader(new LinearGradient(l,t,vert?l:r,vert?b:t,a,z,Shader.TileMode.CLAMP));c.drawRect(l,t,r,b,p);p.setShader(null);}
 void photo(Bitmap bm,float l,float t,float r,float b){if(bm==null)return;reset(-1);rect.set(l,t,r,b);c.drawBitmap(bm,null,rect,p);}
 void cover(Bitmap bm,float l,float t,float r,float b){if(bm==null)return;float k=Math.max((r-l)/bm.getWidth(),(b-t)/bm.getHeight()),w=bm.getWidth()*k,h=bm.getHeight()*k;c.save();c.clipRect(l,t,r,b);photo(bm,(l+r-w)/2,(t+b-h)/2,(l+r+w)/2,(t+b+h)/2);c.restore();}
 // The painted portrait sheets have evenly spaced columns and individually sized rows.
 static final int[] PORTRAIT_ROW={0,183,364,540,714,887};
 void person(int id,float l,float t,float size,boolean happy){if(id<0||id>=CastArt.COUNT)return;if(id>=CastArt.ORIGINAL){photo(castArt.portrait(id),l,t,l+size,t+size);return;}int idx=artMap[id];Bitmap b=happy?faces:calm;if(b==null)return;int x=idx%10,y=idx/10;Rect src=new Rect(x*b.getWidth()/10+2,PORTRAIT_ROW[y]*b.getHeight()/887+2,(x+1)*b.getWidth()/10-2,PORTRAIT_ROW[y+1]*b.getHeight()/887-2);reset(-1);c.drawBitmap(b,src,new RectF(l,t,l+size,t+size),p);}
 void hit(float l,float t,float r,float b,String key){
     int slot=hits.size();
     if(slot==hitPool.size())hitPool.add(new Hit(l,t,r,b,key));
     Hit target=hitPool.get(slot);target.r.set(l,t,r,b);target.key=key;hits.add(target);
 }
 void key(String label,float x,float y,float w,float h,String id,int type){
     float shift=id.equals(held)?2:0;stage.plate(x,y+shift,x+w,y+h+shift,type);mid(label,x+w/2,y+h/2+6+shift,h>45?18:15,type==1?ink:paper);hit(x,y,x+w,y+h,id);
 }
 void brass(String label,float x,float y,float w,String id){stage.plate(x,y,x+w,y+31,0);mid(label,x+w/2,y+21,14,paper);hit(x,y,x+w,y+31,id);}
 void musicButton(float x,float y){
     boolean on=host.musicEnabled;int color=on?0xffbcd0b9:0xffa5aaa4;
     box(x,y,x+84,y+31,7,0xea18251f);stroke(x,y,x+84,y+31,7,0xff657163,1);
     reset(color);c.drawOval(x+10,y+17,x+19,y+23,p);line(x+18,y+20,x+18,y+7,color,1.8f);
     line(x+18,y+7,x+24,y+9,color,2f);if(!on)line(x+8,y+7,x+26,y+24,color,1.8f);
     mid(on?"음악 켬":"음악 끔",x+53,y+21,12,color);hit(x-1,y-6,x+85,y+37,"music");
 }
 @Override protected void onDraw(Canvas canvas){
     super.onDraw(canvas);
     c=canvas;
     now=SystemClock.uptimeMillis();
     if(intro.draw(canvas,now))return;
     pubMenu.arrival.update(now);
     attendance.refresh();
     boolean tableScreen=screen.equals("table")&&g!=null;
     boolean room=tableScreen||panel.equals("entry"),awardScreen=podium.showing();
     deal.update(now,host.active&&tableScreen&&panel.isEmpty());
     int w=getWidth(),h=getHeight();
     int playHeight=tableScreen?Math.max(1,h-stage.bet.dockPixels()):h;
     scale=Math.min(w/1080f,playHeight/520f);
     offX=(w-1080*scale)/2;
     offY=Math.min((h-520*scale)/2,playHeight-520*scale);
     c.drawColor(ink);
     if(awardScreen)podium.backdrop(c,w,h);else if(!room)pubMenu.backdrop(w,h);
     c.save();c.translate(offX,offY);c.scale(scale,scale);
     if(tableScreen||(!panel.isEmpty()&&!panel.equals("attendance")))c.clipRect(0,0,1080,520);
     hits.clear();
     if(room){photo(stage.place.backdrop(),0,0,1080,520);stage.ambience();}
     if(panel.equals("entry")&&venue<2)dailyBuyIn();
     else{
         if(tableScreen)game();
         else if(!room&&!awardScreen)lobby();
         if(panel.equals("attendance"))attendance.draw();
         else if(awardScreen)podium.draw();
         else if(panel.equals("tables"))display.draw();
         else if(panel.equals("receipt"))receipt.draw(this);
         else if(panel.equals("shop")||panel.equals("confirm"))cashier.draw(panel.equals("confirm"));
         else if(panel.equals("profile"))character.draw();
         else if(panel.equals("wagers"))wages.detail();
         else if(panel.equals("gtoStore"))StudyShop.draw(this);
         else if(panel.equals("gtoSample")||panel.equals("gtoReview"))GtoStudy.draw(this,host.studyBilling.unlocked(),panel.equals("gtoSample"));
         else if(!panel.isEmpty())overlay();
     }
     if(rebuyWarningVisible())drawRebuyWarning();
     if(now<toastEnd&&!awardScreen&&!rebuyWarningVisible()){
         float tw=Math.min(860,Math.max(230,toast.length()*14));
         box(540-tw/2,10,540+tw/2,43,15,0xf4eadbbd);
         mid(toast,540,32,15,ink);
     }
     c.restore();
     if(tableScreen&&!rebuyWarningVisible()){c.save();c.translate(offX,offY);c.scale(scale,scale);stage.bet.rail();c.restore();}
     if(tournamentBanner.showing()){hits.clear();held="";tournamentBanner.draw(canvas,now);}
     if(pubMenu.arrival.active()){pubMenu.arrival.draw(canvas,w,h);hits.clear();if(host.active)postInvalidateOnAnimation();}
     host.frame(now);
     if(host.active){
         if(awardScreen){if(podium.animating(now))postInvalidateOnAnimation();}
         else if(screen.equals("lobby")&&panel.isEmpty())pubMenu.schedule();
         else postInvalidateOnAnimation();
     }
 }
 void drawRebuyWarning(){
     String id=g.optString("sessionId");
     if(host.active&&!id.equals(rebuySoundSession)){
         rebuySoundSession=id;
         if(opt.optBoolean("sound"))host.fx("rebuyBell",.65f);
         if(opt.optBoolean("voice"))host.dealerVoice.lastRebuyLevel();
     }
     hits.clear();
     box(0,0,1080,520,0,0xb30c1110);
     stage.plate(248,142,832,365,0);
     text("마지막 리바인 블라인드",540,210,32,paper,title,Paint.Align.CENTER);
     text("5,000  /  10,000",540,252,31,gold,nums,Paint.Align.CENTER);
     mid("이 레벨의 핸드까지 리바인할 수 있습니다.",540,285,16,paper);
     key("확인",474,307,132,39,"ackRebuyNotice",1);
 }
 void openVenue(int selected){
     venue=selected;screen="lobby";if(enterExistingVenue(venue))return;
     if(venue<2)fee=PubMenu.FEES[venue][0];feeChangedAt=now;panel="entry";invalidate();
 }
 void lobby(){pubMenu.draw();}
 // Anchors on the painted armrest of the 1080 x 520 room, ordered around the table.
 // The flank seats are on its outer ends, the rear seats follow its far rim.
 static final float[][] SEAT_X={{},{},{540},{286,794},{113,540,967},{113,354,726,967},
     {113,283,540,797,967},{102,217,392,688,863,978},{102,217,354,540,726,863,978},
     {102,217,341,466,614,739,863,978}};
 static final float[] RAIL_X={129,135,146,165,185,211,245,283,330,400,485,540,632,700,751,802,845,876,902,927,940};
 static final float[] RAIL_Y={293,268,249,231,217,204,192,182,174,168,164,163,165,169,176,188,203,217,237,258,293};
 static float rearRail(float x){
     for(int i=1;i<RAIL_X.length;i++)if(x<RAIL_X[i]){
         float u=(x-RAIL_X[i-1])/(RAIL_X[i]-RAIL_X[i-1]);
         return RAIL_Y[i-1]+(RAIL_Y[i]-RAIL_Y[i-1])*u;
     }
     return RAIL_Y[RAIL_Y.length-1];
 }
 static boolean frontWing(float x){return x<129||x>941;}
 static boolean endSeat(float x){return x<225||x>855;}
 static final float[][][] LAYOUT=new float[SEAT_X.length][][];
 static final Path[][] SEAT_MASKS=new Path[SEAT_X.length][];
 static{
     for(int n=2;n<SEAT_X.length;n++){
         LAYOUT[n]=new float[n][];
         SEAT_MASKS[n]=new Path[n];
         LAYOUT[n][0]=new float[]{540,455};
         for(int i=1;i<n;i++){
             float x=SEAT_X[n][i-1];
             LAYOUT[n][i]=new float[]{x,frontWing(x)?322:rearRail(x)};
             float y=LAYOUT[n][i][1],size=seatSize(y),left=x-size/2,top=y-size;
             Path mask=new Path();mask.moveTo(left-8,top-5);mask.lineTo(left+size+8,top-5);
             for(int k=12;k>=0;k--){float px=left-8+(size+16)*k/12f;float edge=(px<=129||px>=940)?y+5:rearRail(px);mask.lineTo(px,Math.min(y+5,edge));}
             mask.close();SEAT_MASKS[n][i]=mask;
         }
     }
 }
 float[] position(int pos,int n){return LAYOUT[n][pos];}
 static float seatSize(float y){return Math.min(114,74+(y-163)*.25f);}
 static float plateY(float x,float y){return y-(frontWing(x)?9:endSeat(x)?6:24);}
 static final float POCKET_WIDTH=26,POCKET_HEIGHT=36,POCKET_STEP=29;
 static final float POT_X=346,POT_Y=277,BOARD_TOP=241;
 static float pocketX(float x,int count){
     float flankOffset=61+(POCKET_WIDTH+(count-1)*POCKET_STEP)/2;
     return x+(frontWing(x)?(x<540?flankOffset:-flankOffset):0);
 }
 static float pocketY(float x,float y,boolean action){
     float nameplate=plateY(x,y);
     return nameplate+(frontWing(x)?15:action?74:52);
 }
 static float pocketCardX(float x,int card,int count){return pocketX(x,count)+(card-(count-1)/2f)*POCKET_STEP;}
 static final ColorMatrixColorFilter[] FOLD_TONES=new ColorMatrixColorFilter[17];
 static{
     for(int i=0;i<FOLD_TONES.length;i++){
         float u=i/16f;
         ColorMatrix color=new ColorMatrix();color.setSaturation(1-.76f*u);
         ColorMatrix light=new ColorMatrix();float level=1-.57f*u;
         light.setScale(level,level,level,1);color.postConcat(light);
         FOLD_TONES[i]=new ColorMatrixColorFilter(color);
     }
 }
 float foldFade(int id){
     long start=foldStarted[id+1];if(start==0)return 1;
     return SeatActing.foldShade(now-start);
 }
 void chip(float x,float y,int count,int col){int n=Math.max(1,Math.min(count,7));for(int i=0;i<n;i++){float yy=y-i*2.4f;reset(col);c.drawOval(x-9,yy-3.4f,x+9,yy+3.4f,p);reset(0xffead3a7);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(.9f);c.drawOval(x-8,yy-3.1f,x+8,yy+2.7f,p);line(x-5,yy+1,x-4,yy+3,paper,1.7f);}}
 void card(int v,float x,float y,float w,float h,float angle,boolean back){
     if(back){cardBack.draw(c,x,y,w,h,angle,scale);return;}
     c.save();c.rotate(angle,x+w/2,y+h/2);
     box(x+1,y+3,x+w+1,y+h+3,5,0x86090d0b);box(x,y,x+w,y+h,5,0xfff6eddb);
     int col=v/13%2==1?0xffa73732:0xff202f2b;
     text(RANKS[v%13],x+w*.12f,y+h*.38f,h*.38f,col,title,Paint.Align.LEFT);
     mid(SUITS[v/13],x+w*.59f,y+h*.79f,h*.43f,col);c.restore();
 }
 void game(){
 
 reset(-1);p.setShader(hudTop);c.drawRect(0,0,1080,72,p);
 p.setShader(hudBottom);c.drawRect(0,409,1080,520,p);p.setShader(null);
 int venueNow=g.optInt("pub");text(pubs[venueNow],22,28,22,paper,bold,Paint.Align.LEFT);
 JSONObject tournament=g.optJSONObject("tournament");
 txt(g.optBoolean("cash")?(g.optBoolean("double")?"더블보드":"원보드"):"데일리  ·  바이인 "+shortP(g.optInt("buyin",50000))+"P  ·  "+g.optInt("left")+"명 생존",23,48,12,muted);
 float blindX=g.optBoolean("cash")?550:611;
 String blinds=g.optBoolean("cash")?cashBlindLabel():"LV. "+(g.optInt("level")+1)+"   "+fmt(g.optLong("sb"))+" / "+fmt(g.optLong("bb"));
 mid(blinds,blindX,23,Math.min(17,186f/Math.max(1,blinds.length())*1.6f),gold);
 int sec=g.optInt("levelSecondsRemaining");mid(g.optBoolean("cash")?"HAND "+g.optInt("hand"):"HAND "+g.optInt("hand")+"　·　"+String.format(Locale.US,"%d:%02d",sec/60,sec%60),blindX,42,11,muted);
 if(tournament!=null&&tournament.optBoolean("finalRebuyLevelActive"))mid("리바인 마지막 레벨",blindX,59,12,gold);
 washMotion.drawChips();musicButton(692,10);brass("테이블 현황",780,10,105,"tables");brass(fmt(snapshot.optInt("wallet"))+" P",887,10,126,"shop");brass("Ⅱ",1016,10,52,"pause");
 JSONArray ps=g.optJSONArray("p"),boards=g.optJSONArray("board");int hero=0;for(int i=0;i<ps.length();i++)if(ps.optJSONObject(i).optInt("id")==-1)hero=i;
 boolean db=g.optBoolean("double");int pot=g.optInt("pot");
 folds.draw(ps,hero,db);
 stage.opponents.draw(ps,hero);
 stage.chips.pot();payouts.chips();
 community.draw(boards,db,BOARD_TOP);
 for(int i=0;i<ps.length();i++){JSONObject q=ps.optJSONObject(i);int pos=(i-hero+ps.length())%ps.length();if(pos==0)continue;float[] xy=position(pos,ps.length());seat(q,i,xy[0],xy[1],SEAT_MASKS[ps.length()][pos]);}
 JSONObject me=ps.optJSONObject(hero);JSONArray hole=me.optJSONArray("hole");deal.draw(ps,hero,hole);
 String rank="";JSONArray rr=g.optJSONArray("ranks");for(int j=0;j<rr.length();j++)rank+=(j==0?"":"  /  ")+rr.optString(j);if(db&&rr.length()==2)rank="위 "+rr.optString(0)+"  ·  아래 "+rr.optString(1);if(!deal.running()&&g.optDouble("boardHold")==0&&!g.optString("phase").equals("showdown"))mid(rank,820,388,13,gold);
 nameClock.paint(333,391,451,417,g.optBoolean("own")&&!deal.running(),0,me.optBoolean("fold")?FOLD_TONES[Math.round(6*foldFade(-1))]:null);mid("나   "+fmt(me.optInt("stack")),392,409,15,paper);chip(323,412,5,0xffb15346);chip(309,416,3,0xff637c83);
 String myPosition=seatTag(hero);
 if(!myPosition.isEmpty()){box(336,376,369,390,7,0xffe4cda4);mid(myPosition,352.5f,387,10,ink);}
 ShowdownOdds.hero(this,me);
 String feed=g.optString("feed");stage.tableTicker();stage.chips.call();stage.heroGesture();stage.bet.badge(me,392,419,108);payouts.labels();
 String phase=g.optString("phase");if(deal.running()){}else if(g.optBoolean("rebuy")){key("대회 종료",698,446,134,52,"decline",0);int cost=g.optInt("rebuyFee",60000);boolean funded=snapshot.optInt("wallet")>=cost;key(funded?shortP(cost)+"P 리바인":"포인트 충전",842,446,215,52,funded?"re":"shop",1);}
 else if(g.optBoolean("break")){key("휴식 종료",842,446,215,52,"skip",1);mid("휴식  "+(int)g.optDouble("breakLeft")+"초",541,484,23,paper);}
 else if(phase.equals("done")){stage.bet.result();
 if(!g.isNull("settlement")){stage.plate(297,318,395,338,0);mid("팟 정산 내역 ›",Arena.POT_X,332,12,gold);hit(289,254,407,340,"receipt");}
 if(g.optBoolean("ready"))stage.nextHandButton();}
 else if(g.optBoolean("own")){float remaining=(float)g.optDouble("timer");if(!wages.visible())stage.turnNotice(opt.optBoolean("timing")?(remaining>0?"내 차례  ·  "+(int)Math.ceil(remaining)+"초":"타임뱅크  ·  "+(int)g.optDouble("bank")+"초"):"내 차례",862,419,true);
 }
 else if(phase.equals("showdown")&&g.optInt("show")>0){stage.showdownRank(ps,feed);}else if(phase.equals("suspense")||phase.equals("board")||phase.equals("street")||phase.equals("showdown")){/* Leave the felt clear while the hand settles. */}
 else{JSONObject who=ps.optJSONObject(g.optInt("actor"));if(who!=null)stage.turnNotice(who.optString("name")+"의 차례",944,475,false);}
 wages.draw();stage.heroWagerHand();ChipLeaders.draw(this,false);tournamentBanner.bubble(tournament,now);blindTicker.draw(now);washMotion.drawCredit();
 if(g.optBoolean("showdownTitle"))stage.showdownTitle();
 }
 void seat(JSONObject q,int idx,float x,float y,Path seatMask){
     int id=q.optInt("id");
     boolean folded=q.optBoolean("fold"),win=q.optInt("won")>0;
     boolean active=!deal.running()&&g.optString("phase").equals("bet")&&g.optInt("actor")==idx;
     // The far side is smaller in perspective. Leave clear space for the HUD above every head.
     float size=seatSize(y),top=y-size,plateTop=plateY(x,y);
     acting.draw(q,x,y,size,seatMask,active);
     nameClock.paint(x-49,plateTop-1,x+49,plateTop+30,active,win?1:0,folded?FOLD_TONES[Math.round(6*foldFade(id))]:null);
     int textColor=win?ink:folded?muted:paper;
     int tier=q.optInt("tier");
     mid(q.optString("name"),x-13,plateTop+12,13,textColor);
     stage.plate(x+7,plateTop+1,x+42,plateTop+14,tier==2?1:0);
     mid(q.optString("skill"),x+24.5f,plateTop+11,9,tier==2?ink:folded?muted:paper);
     mid(fmt(q.optInt("stack")),x,plateTop+25,12,win?ink:folded?muted:gold);
     String tag=seatTag(idx);
     if(!tag.isEmpty()){
         float bx=x+(x>939?43:-43);
         float half=tag.length()>2?16:11;
         box(bx-half,plateTop-6,bx+half,plateTop+8,7,0xffe4cda4);mid(tag,bx,plateTop+5,10,ink);
     }
     if(win&&now-phaseAt<1100&&!payouts.handles(g)){
         float progress=Math.min(1,(now-phaseAt)/900f);
         for(int v=0;v<4;v++)chip(POT_X+(x-POT_X)*progress+v*5,POT_Y+(y-POT_Y)*progress,2+v%3,0xffb9664d);
     }
     stage.rebuys.seat(id,x,plateTop);
     stage.bet.badge(q,x,plateTop+32,98);
     JSONArray hole=q.optJSONArray("hole");
     float cy=pocketY(x,y,!q.optString("action").isEmpty());
     for(int j=0;!folded&&j<hole.length();j++){
         float cx=pocketCardX(x,j,hole.length());
         int sample=g.optInt("pocketTurnMs",-1);
         if(sample<0){card(hole.optInt(j),cx-POCKET_WIDTH/2,cy-POCKET_HEIGHT/2,POCKET_WIDTH,POCKET_HEIGHT,0,false);continue;}
         float elapsed=sample+Math.min(200,Math.max(0,now-pocketTurnSampleAt));
         float u=Math.max(0,Math.min(1,(elapsed-90-j*65)/515f));
         float angle=(float)Math.PI*u;
         float lift=3*(float)Math.sin(angle);
         c.save();c.translate(cx,cy-lift);c.scale(Math.max(.035f,Math.abs((float)Math.cos(angle))),1);
         card(hole.optInt(j),-POCKET_WIDTH/2,-POCKET_HEIGHT/2,POCKET_WIDTH,POCKET_HEIGHT,0,u<.5f);c.restore();
     }

     ShowdownOdds.seat(this,q,x,plateTop);
     hit(x-Math.max(50,size/2),top,x+Math.max(50,size/2),plateTop+53,"person"+id);
 }
 void actionChip(float x,float y,String label,String amount,String id,int color){stage.button(x,y,label,amount,id);}
 int selectedTournamentTables(){return venue<2?tablesChoice[venue]:0;}

 void dailyBuyIn(){
     JSONArray entries=snapshot.optJSONArray("tournamentEntries");
     JSONObject room=entries==null?null:entries.optJSONObject(venue);
     if(room==null)return;
     JSONArray formats=room.optJSONArray("formats");
     JSONObject event=room;
     for(int i=0;formats!=null&&i<formats.length();i++){
         JSONObject f=formats.optJSONObject(i);
         if(f!=null&&f.optInt("tables")==selectedTournamentTables()){event=f;break;}
     }
     JSONArray offers=event==null?null:event.optJSONArray("offers");
     JSONObject selected=null;
     for(int i=0;offers!=null&&i<offers.length();i++){
         JSONObject offer=offers.optJSONObject(i);
         if(offer!=null&&offer.optInt("buyin")==fee){selected=offer;break;}
     }
     int[] fees=PubMenu.FEES[venue];
     hits.clear();
     grad(0,0,1080,189,0xed101812,0x00101812,true);
     grad(0,365,1080,520,0x00101812,0xed101812,true);
     text(pubs[venue],39,45,25,paper,title,Paint.Align.LEFT);
     txt(venue==0?"데일리 토너먼트":"지역 챔피언십",40,72,17,gold);
     musicButton(828,19);brass("돌아가기",933,19,110,"close");
     if(formats!=null)for(int i=0;i<formats.length();i++){
         int tables=formats.optJSONObject(i).optInt("tables");
         float x=540+(i-(formats.length()-1)/2f)*111-53;
         key(tables+"테이블",x,55,106,44,"field"+tables,tables==event.optInt("tables")?1:0);
     }
     mid("참가 "+event.optInt("initial")+"명  ·  시작 "+fmt(room.optInt("startStack",50000))+"칩  ·  블라인드 "+room.optInt("levelSeconds")/60+"분",540,123,16,paper);
     txt("바이인",40,173,17,gold);
     int[] colors={0xffa94d40,0xff567e91,0xff527b57,0xff775b88};
     for(int i=0;i<fees.length;i++){
         float x=540+(i-(fees.length-1)/2f)*182,y=245;
         boolean on=fee==fees[i];
         if(on){reset(0x9ce8b465);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(2);c.drawOval(x-63,y-16,x+63,y+17,p);}
         float bounce=on?Math.max(0,1-(now-feeChangedAt)/320f):0;
         float lift=on?7+10*(float)Math.sin(bounce*Math.PI):0;
         if(chipArt!=null){int cw=chipArt.getWidth()/2,ch=chipArt.getHeight()/2;Rect src=new Rect(i%2*cw,i/2*ch,(i%2+1)*cw,(i/2+1)*ch);reset(-1);c.drawBitmap(chipArt,src,new RectF(x-70,y-115-lift,x+70,y+20-lift),p);}
         else{c.save();c.translate(x,y-lift);c.scale(3.8f,3.3f);chip(0,0,i+2,colors[i]);c.restore();}
         mid(shortP(fees[i])+"P",x,279,on?25:22,on?gold:paper);
         hit(x-77,133,x+77,286,"fee"+i);
     }
     if(selected!=null)display.entryPrizes(selected);
     txt("내 지갑   "+fmt(snapshot.optInt("wallet"))+" P",39,465,18,paper);
     String share=selected!=null&&selected.has("prizeContributionRate")
            ?"  ·  바이인·리바인 "+Math.round(selected.optDouble("prizeContributionRate")*100)+"% 상금 적립":"";
     txt("리바인 "+shortP(room.optInt("rebuyFee",60000))+"P / "+shortP(room.optInt("rebuyStack",60000))+"칩  ·  5,000 / 10,000까지"+share,40,490,14,muted);
     boolean enough=snapshot.optInt("wallet")>=fee;
     key(enough?shortP(fee)+"P 바이인 · 착석":"포인트 충전",806,432,236,58,enough?"enter":"shop",1);
 }
 JSONObject cashStakes(){
     JSONArray levels=snapshot.optJSONArray("cashLevels");
     return levels==null?null:levels.optJSONObject(cashLevel);
 }
 int fundedMax(JSONObject level){
     return Math.max(level.optInt("min"),Math.min(level.optInt("max"),snapshot.optInt("wallet")));
 }
 String seatTag(int idx){
     boolean dealer=idx==g.optInt("btn",-1),small=idx==g.optInt("sbs",-1);
     if(small)return dealer?"D/SB":"SB";
     return dealer?"D":idx==g.optInt("bbs",-1)?"BB":"";
 }
 String cashBlindLabel(){
     if(g.optBoolean("singleBlind"))return "앤티 "+shortP(g.optInt("ante"))+" · SB "+shortP(g.optInt("sb"));
     String prefix=g.optBoolean("double")?"앤티 "+shortP(g.optInt("ante"))+" · ":"블라인드  ";
     return prefix+fmt(g.optInt("sb"))+" / "+fmt(g.optInt("bb"));
 }
 void cashBuyIn(){
     JSONObject stakes=cashStakes();if(stakes==null)return;
     int min=stakes.optInt("min"),max=stakes.optInt("max"),ceiling=fundedMax(stakes);
     buy=Math.max(min,Math.min(ceiling,buy));
     key("원보드",259,107,132,42,"cash",dbl?0:1);key("더블보드",398,107,132,42,"double",dbl?1:0);
     key("6인",621,107,86,42,"six",cashSeats==6?1:0);key("9인",714,107,86,42,"nine",cashSeats==9?1:0);
     txt(dbl?"앤티 · SB":"블라인드",267,182,16,muted);
     JSONArray choices=snapshot.optJSONArray("cashLevels");
     for(int i=0;i<choices.length();i++){
         JSONObject level=choices.optJSONObject(i);
         String limits=dbl?"앤티 "+shortP(level.optInt("ante"))+" · SB "+shortP(level.optInt("doubleSb")):fmt(level.optInt("sb"))+" / "+fmt(level.optInt("bb"));
         key(limits,367+i*217,151,208,43,"stakes"+i,cashLevel==i?1:0);
     }
     if(dbl){
         txt("전원 앤티 "+shortP(stakes.optInt("ante"))+" · SB만 "+shortP(stakes.optInt("doubleSb"))+" 추가 납부",267,216,16,paper);
         txt("홀카드 3장 중 최대 2장 · 두 플랍을 공개한 뒤 SB 다음부터 베팅",267,237,14,muted);
     }else txt("홀카드 2장 · 공유 카드 5장 · 한 보드에서 팟 정산",267,219,16,paper);
     txt("상대 바이인 "+shortP(min)+"~"+shortP(max)+" P  ·  30핸드마다 "+shortP(max)+" P 초과분 워싱",267,dbl?258:246,15,gold);
     txt("내 바이인",267,287,16,muted);mid(fmt(buy)+" P",539,294,30,paper);
     bar(297,331,780,"buy",min,ceiling,buy);
     txt(shortP(min)+" P",273,361,14,muted);text(shortP(ceiling)+" P",799,361,14,muted,normal,Paint.Align.RIGHT);
     txt(dbl?"레이크 10% · 최대 4만 P · 찹 1만 P":"레이크 10% · 1,000 P 미만 절사",267,388,15,gold);
     txt("내 지갑  "+fmt(snapshot.optInt("wallet"))+" P",267,432,17,paper);
     txt("6핸드 전 퇴장 시 잔여 칩 10% 차감",267,456,14,muted);
     boolean funded=snapshot.optInt("wallet")>=buy;
     key(funded?shortP(buy)+"P 착석  ›":"포인트 충전",590,403,219,55,funded?"enter":"shop",1);
 }
 void overlay(){hits.clear();box(0,0,1080,520,0,0xb208100e);float l=230,r=850;box(l,38,r,484,12,0xff1c2b25);stroke(l,38,r,484,12,0xff947951,1);line(l+22,104,r-22,104,0xff716345,1);String heading="";
 switch(panel){case "entry":heading=pubs[venue];break;case "settings":heading="게임 설정";break;case "shop":heading="포인트 충전";break;case "confirm":heading="모의 충전";break;case "pause":heading="일시정지";break;case "surrender":heading="포기하고 나가기";break;case "raise":heading="레이즈 금액";break;case "result":heading="테이블 정산";break;}
 text(heading,l+27,84,28,paper,title,Paint.Align.LEFT);brass("닫기",r-84,55,61,"close");
 if(panel.equals("entry"))cashBuyIn();
 else if(panel.equals("settings")){
     String[] ids={"sound","voice","timing","speed"},labels={"카드와 칩 효과음","한국어 딜러 음성","내 차례 제한 시간","진행 속도"};int[] rows={120,171,310,365};
     for(int i=0;i<ids.length;i++){
         int y=rows[i];txt(labels[i],265,y+25,18,paper);
         String val=i==3?(opt.optInt("speed")==1?"기본":"빠르게"):opt.optBoolean(ids[i])?"켜짐":"꺼짐";
         key(val,675,y,127,39,"opt:"+ids[i],0);
     }
     txt(host.dealerVoice.status(),265,235,15,muted);
     key("딜러 미리 듣기",261,248,192,42,"voiceSample",0);
     key("개인정보처리방침",500,248,302,42,"privacyPolicy",0);
     line(264,299,804,299,0xff52624f,1);
     brass("GTO 홀덤 공부",501,424,301,"gtoStore");
 }
 else if(panel.equals("pause"))ExitPanel.pause(this);
 else if(panel.equals("surrender"))ExitPanel.confirm(this);
 else if(panel.equals("raise")&&g!=null){mid(fmt(raise),540,198,47,gold);mid("이번 라운드 총 베팅액",540,225,15,muted);bar(295,266,785,"amount",g.optInt("min"),g.optInt("max"),raise);String[] ns={"최소","½ 팟","팟","올인"};for(int i=0;i<4;i++)key(ns[i],277+i*132,310,118,39,"size"+i,0);key("레이즈 확정",400,389,280,54,"commit",1);}
 else if(panel.equals("result")){wrapped(snapshot.optString("last"),272,192,540,23,paper,3);txt("현재 지갑  "+fmt(snapshot.optInt("wallet"))+" P",272,299,21,gold);key("로비로",400,379,280,54,"close",1);stage.rebuys.ticket();}
 }
 void bar(float l,float y,float r,String key,int min,int max,int v){float f=max==min?0f:(v-min)/(float)(max-min);box(l,y-3,r,y+3,3,0xff586154);box(l,y-3,l+(r-l)*f,y+3,3,gold);reset(paper);c.drawCircle(l+(r-l)*f,y,10,p);hit(l-14,y-25,r+14,y+25,key);}
 void slider(String k,float x){if(k.equals("buy")){JSONObject level=cashStakes();if(level==null)return;int min=level.optInt("min"),max=fundedMax(level);float position=Math.max(0,Math.min(1,(x-297)/483));buy=Math.max(min,Math.min(max,(int)Math.round((min+(max-min)*position)/1000.0)*1000));if(x>=779)buy=max;}if(k.equals("amount")&&g!=null){int min=g.optInt("min"),max=g.optInt("max");raise=Math.max(min,Math.min(max,(int)Math.round((min+(max-min)*Math.max(0,Math.min(1,(x-295)/490)))/100.0)*100));if(x>=784)raise=max;}}
 @Override public boolean onTouchEvent(MotionEvent e){if(pubMenu.arrival.touch(e)||intro.touch(e)||tournamentBanner.touch(e))return true;float x=(e.getX()-offX)/scale,y=(e.getY()-offY)/scale;switch(e.getActionMasked()){case MotionEvent.ACTION_DOWN:held="";pressX=x;pressY=y;drag=false;pressHand=g==null?-1:g.optInt("hand");pressSession=g==null?"":g.optString("sessionId");for(int i=hits.size()-1;i>=0;i--)if(hits.get(i).r.contains(x,y)){held=hits.get(i).key;break;}if(held.equals("buy")||held.equals("amount"))slider(held,x);invalidate();return true;
 case MotionEvent.ACTION_MOVE:if(held.equals("peek")){peel=Math.min(175,(float)Math.hypot(x-pressX,y-pressY)*2.4f);if(peel>110&&!drag){drag=true;host.command("peek");}}else slider(held,x);invalidate();return true;
 case MotionEvent.ACTION_UP:String a=held;held="";if(a.equals("buy")||a.equals("amount"))slider(a,x);else if((a.equals("peek")||Math.hypot(x-pressX,y-pressY)<36)&&(!BetArt.railKey(a)||stage.bet.tapStillValid(a,x,y)))action(a);peel=0;performClick();invalidate();return true;
 case MotionEvent.ACTION_CANCEL:held="";peel=0;return true;}return true;}
 @Override public boolean onKeyUp(int key,KeyEvent event){
     if((key==KeyEvent.KEYCODE_ENTER||key==KeyEvent.KEYCODE_SPACE||key==KeyEvent.KEYCODE_DPAD_CENTER)&&intro.keyboardStart())return true;
     return super.onKeyUp(key,event);
 }
 @Override public boolean performClick(){super.performClick();return true;}
 void action(String a){if(a.isEmpty()||!host.loaded)return;if(attendance.action(a)||display.action(a))return;if(opt.optBoolean("sound")&&!a.equals("peek"))host.fx("tap",.26f);
 if(rebuyWarningVisible()){if(a.equals("ackRebuyNotice"))host.command(a,"session",g.optString("sessionId"));return;}
 if(a.equals("voiceSample")){host.dealerVoice.preview();return;}
 if(a.equals("privacyPolicy")){PrivacyPolicy.show(host);return;}
 if(a.equals("gtoStore")){
     if(!panel.startsWith("gto"))studyReturn=panel;
     panel="gtoStore";host.studyBilling.refresh();invalidate();return;
 }
 if(a.equals("gtoPurchase")&&panel.equals("gtoStore")){host.studyBilling.purchase();return;}
 if(a.equals("gtoRefresh")&&panel.equals("gtoStore")){host.studyBilling.refresh();return;}
 if(a.equals("gtoManage")&&panel.equals("gtoStore")){host.studyBilling.manage();return;}
 if(a.equals("gtoSample")||a.equals("gtoReview")){
     if(!panel.startsWith("gto"))studyReturn=panel;
     panel=a.equals("gtoReview")&&!host.studyBilling.unlocked()?"gtoStore":a;
     if(panel.equals("gtoStore"))host.studyBilling.refresh();
     invalidate();return;
 }
 if(a.equals("parkVenue")){if(g!=null&&panel.equals("pause"))host.command(a,"session",g.optString("sessionId"));return;}
 if(a.equals("replayIntro")&&screen.equals("lobby")&&panel.isEmpty()){intro.replay();return;}
 if(a.startsWith("exit@")){if(panel.equals("surrender"))host.command("surrender","token",a.substring(5));return;}
 if(a.startsWith("field")&&panel.equals("entry")&&venue<2){tablesChoice[venue]=Integer.parseInt(a.substring(5));return;}
 if(a.startsWith("stakes")&&panel.equals("entry")&&venue==2){cashLevel=Integer.parseInt(a.substring(6));return;}
 if(a.startsWith("fee")){fee=PubMenu.FEES[venue][Integer.parseInt(a.substring(3))];feeChangedAt=now;return;}if(a.startsWith("venue")){pubMenu.arrival.start(Integer.parseInt(a.substring(5)));return;}if(a.startsWith("person")){int id=Integer.parseInt(a.substring(6));if(id>=0&&id<people.length()){profile=id;profileReturn=panel.equals("tables")?"tables":"";panel="profile";host.refreshPeople();}return;}if(a.startsWith("opt:")){host.command("option","name",a.substring(4));return;}if(a.startsWith("pack")){cashier.select(Integer.parseInt(a.substring(4)));return;}if(a.startsWith("size")){int i=Integer.parseInt(a.substring(4)),min=g.optInt("min"),max=g.optInt("max"),base=g.optInt("call")+g.optJSONArray("p").optJSONObject(0).optInt("bet");raise=i==0?min:i==3?max:Math.max(min,Math.min(max,base+g.optInt("pot")/(i==1?2:1)));return;}
 switch(a){case "entry":openVenue(venue);break;
 case "enter":if(enterExistingVenue(venue))break;host.command("enter","pub",venue,"double",dbl,"seats",cashSeats,"buy",venue<2?fee:buy,"tables",venue<2?tablesChoice[venue]:0,"cashLevel",cashLevel,"fromLobby",true);break;case "shop":shopFromEntry=panel.equals("entry");panel=a;break;case "wagers":wages.open();panel=a;break;case "olderWagers":wages.turn(1);break;case "newerWagers":wages.turn(-1);break;case "tables":display.open();panel=a;break;case "monitorClock":display.seats=false;break;case "monitorSeats":display.seats=true;break;case "music":host.toggleMusic();break;case "receipt":receipt.open(g.optJSONObject("settlement"));panel=a;break;case "potNext":receipt.turn(1);break;case "potPrev":receipt.turn(-1);break;case "tableNext":stage.changePage(1);break;case "tablePrev":stage.changePage(-1);break;case "pause":case "settings":panel=a;break;case "close":back();break;
 case "rewardAd":host.rewardAds.watch();break;case "adPrivacy":host.rewardAds.privacy();break;
 case "six":cashSeats=6;break;case "nine":cashSeats=9;break;case "cash":dbl=false;break;case "double":dbl=true;break;case "credit":cashier.purchase();break;
 case "surrender":panel="surrender";break;case "cancelExit":panel="pause";break;case "lobby":panel="";screen="lobby";break;case "leave":panel="";host.command("leave");break;case "raise":raise=g.optInt("min");panel="raise";break;case "commit":panel="";host.command("act","kind","raise","amount",raise,"hand",g.optInt("hand"),"session",g.optString("sessionId"));break;
 case "prefoldOn":case "prefoldOff":if(g!=null)host.command("prefold","checked",a.equals("prefoldOn"),"hand",g.optInt("hand"),"session",g.optString("sessionId"));break;case "quarter":case "half":case "pot":case "allin":case "fold":case "call":if(g!=null)host.command("act","kind",a,"hand",g.optInt("hand"),"session",g.optString("sessionId"));break;case "re":case "decline":host.command("re","yes",a.equals("re"));break;case "next":if(g!=null)host.command(a,"hand",g.optInt("hand"));break;case "skip":case "peek":host.command(a);break;}}
}

