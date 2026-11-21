package kr.mangwon.holdem;

import android.content.res.AssetFileDescriptor;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;

/** Lobby and table share one continuous, gapless recording and one playhead. */
final class PubMusic {
    private static final float ROOM=.45f, UNDER_VOICE=.15f;
    private final GameActivity activity;
    private final Handler ui=new Handler(Looper.getMainLooper());
    private MediaPlayer player;
    private boolean enabled,active,ducked,ready,started,released;
    private float volume,from,target;
    private long fadeStart,fadeLength;

    PubMusic(GameActivity activity){this.activity=activity;}

    void setEnabled(boolean value){if(enabled!=value){enabled=value;update();}}
    void setActive(boolean value){if(active!=value){active=value;update();}}
    void setDucked(boolean value){if(ducked!=value){ducked=value;update();}}

    private void update(){
        if(released)return;
        if(!active){
            ui.removeCallbacks(fade);
            if(ready&&started){player.pause();started=false;}
            volume=0;target=0;
            return;
        }
        if(player==null){
            if(enabled)open();
            return;
        }
        if(!ready)return;
        if(enabled&&!started){player.setVolume(0,0);volume=0;player.start();started=true;}
        float next=enabled?(ducked?UNDER_VOICE:ROOM):0;
        if(!started||target==next&&volume==next)return;
        ui.removeCallbacks(fade);
        from=volume;target=next;fadeStart=SystemClock.uptimeMillis();
        fadeLength=next<volume?115:460;
        fade.run();
    }
    private final Runnable fade=new Runnable(){
        @Override public void run(){
            if(released||!ready||!started)return;
            float f=Math.min(1,(SystemClock.uptimeMillis()-fadeStart)/(float)fadeLength);
            volume=from+(target-from)*f*f*(3-2*f);
            player.setVolume(volume,volume);
            if(f<1)ui.postDelayed(this,24);
            else if(!enabled){player.pause();started=false;}
        }
    };
    private void open(){
        MediaPlayer next=new MediaPlayer();player=next;
        try(AssetFileDescriptor file=activity.getAssets().openFd("pub-loop.ogg")){
            next.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build());
            next.setDataSource(file.getFileDescriptor(),file.getStartOffset(),file.getLength());
            next.setLooping(true);next.setVolume(0,0);
            next.setOnPreparedListener(p->{if(p==player&&!released){ready=true;update();}});
            next.setOnErrorListener((p,what,extra)->{if(p==player)closePlayer();return true;});
            next.prepareAsync();
        }catch(Exception e){Log.w("Mangwon","Unable to open room music",e);closePlayer();}
    }
    private void closePlayer(){
        ui.removeCallbacks(fade);
        if(player!=null){MediaPlayer old=player;player=null;old.release();}
        ready=false;started=false;volume=0;target=0;
    }
    void release(){released=true;closePlayer();}
}
