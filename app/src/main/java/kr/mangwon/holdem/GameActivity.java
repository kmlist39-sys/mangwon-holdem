package kr.mangwon.holdem;

import android.app.Activity;
import android.os.*;
import android.view.*;
import android.graphics.*;
import android.media.*;
import android.content.res.AssetFileDescriptor;
import org.json.*;
import java.util.*;
import java.io.*;

public final class GameActivity extends Activity {
    static {System.loadLibrary("club");}
    static native void boot(byte[] code);
    static native String call(String json);
    final Handler ui=new Handler(Looper.getMainLooper());
    Handler worker; HandlerThread thread; Arena arena;
    volatile boolean active; boolean pending,loaded; long lastTick,lastSave;
    SoundPool sounds; final Map<String,Integer> soundIds=new HashMap<>(),lengths=new HashMap<>();
    final Set<Integer> ready=new HashSet<>(); long talkingUntil; DealerVoice dealerVoice;
    PubMusic music; RewardAdDesk rewardAds;
    private static boolean openedInProcess;
    boolean musicEnabled; String rewardNoticeOnReturn;
    static JSONObject args(Object... pairs){JSONObject j=new JSONObject();try{for(int i=0;i<pairs.length;i+=2)j.put((String)pairs[i],pairs[i+1]);}catch(JSONException e){throw new IllegalArgumentException(e);}return j;}
    byte[] bytes(String file)throws IOException{try(InputStream in=getAssets().open(file);ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[16384];int n;while((n=in.read(b))>=0)out.write(b,0,n);return out.toByteArray();}}
    void immersive(){getWindow().getDecorView().setSystemUiVisibility(5894);getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);}
    @Override public void onCreate(Bundle b){super.onCreate(b);immersive();setVolumeControlStream(AudioManager.STREAM_MUSIC);
        musicEnabled=getPreferences(0).getBoolean("pubMusic",true);music=new PubMusic(this);music.setEnabled(musicEnabled);
        arena=new Arena(this);arena.intro.prepare(b==null&&!openedInProcess);openedInProcess=true;dealerVoice=new DealerVoice(this);rewardAds=new RewardAdDesk(this);setContentView(arena);
        if(Build.VERSION.SDK_INT>=33)getOnBackInvokedDispatcher().registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT,()->{if(!arena.back())finish();});
        thread=new HandlerThread("PokerRules");thread.start();worker=new Handler(thread.getLooper());
        sounds=new SoundPool.Builder().setMaxStreams(6).setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).build()).build();
        sounds.setOnLoadCompleteListener((pool,id,status)->{if(status==0)ready.add(id);});
        try{JSONObject durations=new JSONObject(new String(bytes("sounds.json"),"UTF-8"));Iterator<String> it=durations.keys();while(it.hasNext()){String key=it.next();try(AssetFileDescriptor fd=getAssets().openFd("sfx/"+key+".wav")){soundIds.put(key,sounds.load(fd,1));lengths.put(key,durations.optInt(key));}}}catch(Exception e){android.util.Log.e("Mangwon","Audio assets",e);}
        worker.post(()->{try{boot(bytes("club.js"));JSONObject state=new JSONObject(call(args("op","init","data",getPreferences(0).getString("session",""),"completedAds",getPreferences(0).getLong("completedAds",0)).toString()));if(state.optBoolean("rewardApplied"))persist();JSONArray people=new JSONArray(call("{\"op\":\"roster\"}"));ui.post(()->{if(isDestroyed())return;arena.people=people;present(state);loaded=true;arena.intro.ready(arena.g!=null);lastTick=SystemClock.uptimeMillis();rewardAds.start();});}catch(Exception e){android.util.Log.e("Mangwon","Startup",e);}});
    }
    void persist(){getPreferences(0).edit().putString("session",call("{\"op\":\"save\"}")).commit();}
    // Commit the SDK's earned event first. A process restart replays the cumulative
    // count into the saved wallet exactly once, even if it interrupted the rules thread.
    void recordAdCompletion(){
        android.content.SharedPreferences prefs=getPreferences(0);
        long completed=prefs.getLong("completedAds",0)+1;
        if(!prefs.edit().putLong("completedAds",completed).commit()){
            android.util.Log.e("RewardAds","Could not save earned event");
            arena.note("광고 보상 저장을 확인해 주세요");
            return;
        }
        if(!isDestroyed())command("rewardedAd","completedAds",completed);
    }
    void command(String op,Object... values){
        JSONObject j=args(values);try{j.put("op",op);}catch(JSONException e){return;}
        boolean tick=op.equals("step");if(tick)pending=true;
        worker.post(()->{
            try{
                JSONObject state=new JSONObject(call(j.toString()));
                long now=SystemClock.uptimeMillis();
                if(!tick||state.optBoolean("preFoldConsumed")||cashWaitsForInput(state.optJSONObject("g"),state.optJSONObject("opt"))||now-lastSave>=2500){persist();lastSave=now;}
                JSONArray roster=op.equals("next")||op.equals("enter")?new JSONArray(call("{\"op\":\"roster\"}")):null;
                ui.post(()->{
                    if(isDestroyed())return;
                    if(roster!=null)arena.people=roster;
                    present(state);if(tick)pending=false;
                });
            }catch(JSONException error){
                android.util.Log.e("Mangwon","Rule snapshot",error);
                if(tick)ui.post(()->pending=false);
            }
        });
    }
    void refreshPeople(){
        worker.post(()->{try{
            JSONArray roster=new JSONArray(call("{\"op\":\"roster\"}"));
            ui.post(()->{if(!isDestroyed()){arena.people=roster;arena.invalidate();}});
        }catch(JSONException e){android.util.Log.w("Mangwon","Participant profile",e);}});
    }
    boolean cashWaitsForInput(JSONObject game,JSONObject options){
        return game!=null&&game.optBoolean("cash")&&(game.optBoolean("ready")||
            game.optBoolean("own")&&!game.optBoolean("preFold")&&!options.optBoolean("timing")&&game.optString("phase").equals("bet")
                &&game.optDouble("beforeStreet")==0&&game.optDouble("boardHold")==0);
    }
    void frame(long now){
        if(!loaded||!active||!arena.tableOpen()){lastTick=now;return;}
        JSONObject game=arena.g;
        // Cash has a single table. These two states wait solely for a button press.
        if(cashWaitsForInput(game,arena.opt)){
            lastTick=now;return;
        }
        if(!pending&&now-lastTick>=180){
            double dt=Math.min(.7,(now-lastTick)/1000.0);lastTick=now;
            command("step","dt",dt,"inspect",arena.panel.equals("tables"),"holdTable",arena.deal.running()||arena.panel.equals("wagers")||arena.panel.equals("receipt")||arena.panel.equals("profile")||now<talkingUntil);
        }
    }
    void fx(String key,float volume){Integer id=soundIds.get(key);if(id!=null&&ready.contains(id)&&active)sounds.play(id,volume,volume,1,0,1f);}
    void present(JSONObject state){
        dealerVoice.observe(state);
        arena.accept(state);
        if(!active&&state.optBoolean("rewardApplied"))rewardNoticeOnReturn=state.optString("notice");
    }
    void line(String words){dealerVoice.hand(words);}
    void toggleMusic(){
        musicEnabled=!musicEnabled;music.setEnabled(musicEnabled);
        getPreferences(0).edit().putBoolean("pubMusic",musicEnabled).apply();arena.invalidate();
    }
    @Override protected void onResume(){super.onResume();active=true;immersive();music.setActive(true);lastTick=SystemClock.uptimeMillis();if(arena!=null){if(rewardNoticeOnReturn!=null){arena.note(rewardNoticeOnReturn);rewardNoticeOnReturn=null;}arena.invalidate();}}
    @Override protected void onPause(){if(arena.intro.blocking())arena.intro.finish();active=false;music.setActive(false);dealerVoice.stop();talkingUntil=0;if(worker!=null)worker.post(this::persist);super.onPause();}
    @Override protected void onUserLeaveHint(){rewardAds.leave();super.onUserLeaveHint();}
    @Override protected void onDestroy(){worker.post(()->thread.quitSafely());ui.removeCallbacksAndMessages(null);dealerVoice.release();music.release();sounds.release();super.onDestroy();}
    @Override public void onWindowFocusChanged(boolean b){super.onWindowFocusChanged(b);if(b)immersive();}
    @Override public void onBackPressed(){if(!arena.back())super.onBackPressed();}
}
