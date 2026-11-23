package kr.mangwon.holdem;

import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.content.res.AssetFileDescriptor;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayDeque;
import java.util.List;

/** One serialized queue for actions, whole Korean sentences, and net pot results. */
final class DealerVoice {
    private final GameActivity activity;
    private final GeminiBank bank;
    private final Handler ui=new Handler(Looper.getMainLooper());
    private final ArrayDeque<DealerLine> pending=new ArrayDeque<>();
    private final ArrayDeque<String> recorded=new ArrayDeque<>();
    private final AudioAttributes audio=new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build();
    private MediaPlayer playing;
    private MediaPlayer prefetched;
    private String prefetchedKey="";
    private boolean playingStarted,prefetchedReady,prefetchedLinked;
    private int seenHand=-1,seenAction=-1,titleHand=-1,generation;
    private String settlementId="";
    private boolean observed,held,dual,enabled,previewing;
    private boolean tournamentQueue,cashEntryQueue;
    private String cashSession="";
    private long lastCashSeq;

    DealerVoice(GameActivity activity){this.activity=activity;bank=new GeminiBank(activity);}
    boolean ready(){return bank.ready();}
    boolean busy(){return playing!=null||prefetched!=null||!pending.isEmpty()||!recorded.isEmpty();}
    String status(){return bank.status();}
    void lastRebuyLevel(){
        if(activity.active&&ready()&&add(new DealerLine("이번 블라인드가 마지막 리바인 기회입니다.","rebuy-level")))playNext();
    }
    void preview(){
        if(!ready()){activity.arena.note(status());return;}
        stop();previewing=true;
        DealerLine sample=new DealerLine("체크. 쇼다운! 윗보드는 오미경 님이 에이스 원 페어로 승리하셨습니다.","check","showdown","result:upper:오미경","winning:에이스 원페어");
        if(available(sample))add(sample);
        else{
            // A proof pack may currently provide only one genuine recording.
            String[] options={"check","showdown","call","raise","allin","fold","result:main"};
            for(String key:options)if(bank.has(key)){recorded.add(key);break;}
            if(recorded.isEmpty()){String first=bank.sampleKey();if(first!=null)recorded.add(first);}
        }
        playNext();
    }
    void observe(JSONObject snapshot){
        JSONObject game=snapshot.optJSONObject("g"),opts=snapshot.optJSONObject("opt");
        String switched=snapshot.optString("venueChange");
        if("parked".equals(switched)||"resumed".equals(switched)){
            stop();observed=false;seenHand=-1;seenAction=-1;titleHand=-1;settlementId="";cashSession="";
        }
        boolean voiceOn=activity.active&&opts!=null&&opts.optBoolean("voice",true);
        JSONArray events=snapshot.optJSONArray("tournamentEvents");
        if(game==null){
            cashSession="";
            if(observed&&voiceOn&&seenHand!=-1&&ready())addTournamentEvents(events);
            if(tournamentQueue&&voiceOn)playNext();
            else if(!previewing)stop();
            observed=true;seenHand=-1;seenAction=-1;titleHand=-1;settlementId="";return;
        }
        int hand=game.optInt("hand");
        if(seenHand!=hand&&held&&!tournamentQueue&&!cashEntryQueue)stop();
        dual=game.optBoolean("double");
        boolean title=game.optBoolean("showdownTitle");
        JSONObject action=game.optJSONObject("announcement"),result=game.optJSONObject("settlement");
        int seq=action==null?-1:action.optInt("seq",-1);
        String resultId=result==null?"":game.optString("sessionId")+"/"+hand+"/"+result.optInt("hand",hand);
        enabled=activity.arena.screen.equals("table")&&voiceOn;
        if(!enabled&&!previewing)stop();
        boolean play=observed&&enabled&&ready();
        if(play){
            if(snapshot.optBoolean("finalTableStarted"))add(new DealerLine("파이널 테이블입니다.","final-table"));
            if(snapshot.optBoolean("headsUpStarted"))add(new DealerLine("해즈업!!!","heads-up"));
            if(action!=null&&action.optInt("hand",hand)==hand&&(seenHand!=hand||seq!=seenAction)){
                add(actionLine(game,action));
            }
            if(title&&titleHand!=hand&&add(new DealerLine("쇼다운!","showdown")))hold();
            if(result!=null&&!resultId.equals(settlementId)){
                List<DealerLine> lines=OutcomeAnnouncement.phrases(result,dual);
                boolean any=false;for(DealerLine line:lines)if(add(line))any=true;if(any)hold();
            }
            addTournamentEvents(events);
        }
        observeCashEntries(game,play);
        if(play)playNext();
        if(title)titleHand=hand;
        if(result!=null)settlementId=resultId;
        observed=true;seenHand=hand;seenAction=seq;
    }
    private void observeCashEntries(JSONObject game,boolean play){
        String session=game.optString("sessionId");
        boolean same=!session.isEmpty()&&session.equals(cashSession);
        if(!same){cashSession=session;lastCashSeq=0;}
        JSONArray events=game.optJSONArray("cashEvents");
        if(events!=null&&events.length()>0){
            JSONObject last=events.optJSONObject(events.length()-1);
            if(last!=null&&cashSession.equals(last.optString("session"))&&last.optLong("seq")<=lastCashSeq)return;
            for(int i=0;i<events.length();i++)observeCashEntry(events.optJSONObject(i),play&&same);
        }else observeCashEntry(game.optJSONObject("cashEvent"),play&&same);
    }
    private void observeCashEntry(JSONObject event,boolean play){
        if(event==null||cashSession.isEmpty()||!cashSession.equals(event.optString("session")))return;
        long seq=event.optLong("seq",0);
        if(seq<=lastCashSeq)return;
        lastCashSeq=seq;
        if(!play||event.optInt("id",-1)<0||event.optLong("amount")<=0)return;
        String name=event.optString("name"),kind=event.optString("kind");
        if(name.isEmpty()||!("added".equals(kind)||"rebought".equals(kind)))return;
        boolean added="added".equals(kind);
        DealerLine line=new DealerLine(OutcomeAnnouncement.person(name)+", "+(added?"애드온하셨습니다.":"리바인하셨습니다."),
            "name:"+name,added?"cash:added":"tournament:rebought");
        if(add(line))cashEntryQueue=true;
    }
    static DealerLine actionLine(JSONObject game,JSONObject action){
        String kind=action.optString("kind"),text=OutcomeAnnouncement.action(kind);
        String cue="full".equals(kind)?"pot":kind;
        int actor=action.optInt("id",-1);
        JSONArray seats=game.optJSONArray("p");
        if(actor>=0&&seats!=null&&!text.isEmpty()&&!"check".equals(kind)&&!"call".equals(kind)
            &&!"fold".equals(kind)&&action.optBoolean("raisesBet",true)){
            for(int i=0;i<seats.length();i++){
                JSONObject seat=seats.optJSONObject(i);
                if(seat==null||seat.optInt("id",-1)!=actor)continue;
                if(!action.has("raisesBet")){
                    // Older snapshots only stored the bet increase on the actor's seat.
                    JSONObject move=seat.optJSONObject("move");
                    if(move==null||!"raise".equals(move.optString("kind"))||move.optInt("increase")<=0
                        ||move.optInt("total")<=move.optInt("increase"))break;
                }
                String name=seat.optString("name");
                if(!name.isEmpty())return new DealerLine(OutcomeAnnouncement.person(name)+", "+text,"name:"+name,cue);
                break;
            }
        }
        return new DealerLine(text,cue);
    }
    private void addTournamentEvents(JSONArray events){
        if(events==null)return;
        for(int i=0;i<events.length();i++){
            JSONObject event=events.optJSONObject(i);
            if(event==null||!event.optBoolean("voiceAllowed",true))continue;
            String name=event.optString("name"),kind=event.optString("kind");
            if(name.isEmpty()||!("eliminated".equals(kind)||"rebought".equals(kind)))continue;
            String words="eliminated".equals(kind)?"탈락하셨습니다.":"리바인하셨습니다.";
            if(add(new DealerLine(name+" 님, "+words,"name:"+name,"tournament:"+kind)))tournamentQueue=true;
        }
    }
    void hand(String sentence){
        if(dual||!enabled||!ready()||!activity.active)return;
        DealerLine line=OutcomeAnnouncement.hand(sentence);
        if(line==null)return;
        if(add(line)){hold();playNext();}
    }
    private boolean available(DealerLine line){return line!=null&&!line.text.isEmpty()&&!bank.resolve(line).isEmpty();}
    private boolean add(DealerLine line){if(!available(line))return false;pending.add(line);return true;}
    private void hold(){held=true;activity.talkingUntil=Long.MAX_VALUE;}
    private String nextKey(){
        while(recorded.isEmpty()&&!pending.isEmpty())recorded.addAll(bank.resolve(pending.removeFirst()));
        return recorded.pollFirst();
    }
    private void playNext(){
        if(playing!=null){prefetch();return;}
        if(!activity.active||!ready()){pending.clear();recorded.clear();finishQueue();return;}
        String key=nextKey();
        if(key==null){finishQueue();return;}
        prepare(key,false);
    }
    private void prefetch(){
        if(prefetched!=null||playing==null||!activity.active)return;
        String key=nextKey();if(key!=null)prepare(key,true);
    }
    private void prepare(String key,boolean ahead){
        int token=generation;
        MediaPlayer player=new MediaPlayer();
        if(ahead){prefetched=player;prefetchedKey=key;prefetchedReady=false;prefetchedLinked=false;}else playing=player;
        try{
            if(ahead)player.setAudioSessionId(playing.getAudioSessionId());
            player.setAudioAttributes(audio);
            try(AssetFileDescriptor fd=bank.open(key)){
                player.setDataSource(fd.getFileDescriptor(),fd.getStartOffset(),fd.getLength());
            }
            player.setVolume(.96f,.96f);
            player.setOnCompletionListener(p->finished(p,true));
            player.setOnErrorListener((p,what,extra)->{finished(p,false);return true;});
            player.setOnPreparedListener(p->{
                if(token!=generation||!activity.active)return;
                ui.removeCallbacksAndMessages(p);
                if(prefetched==p){prefetchedReady=true;linkNext();}
                else if(playing==p)start(p,key,false);
            });
            watch(player,16000);
            player.prepareAsync();
        }catch(Exception e){Log.e("Mangwon","Dealer playback",e);finished(player,false);}
    }
    private void watch(MediaPlayer player,long timeout){
        // Preparation and playback have separate deadlines. A queued clip has no running timer.
        ui.removeCallbacksAndMessages(player);
        ui.postAtTime(()->{
            if(playing==player||prefetched==player)finished(player,false);
        },player,SystemClock.uptimeMillis()+timeout);
    }
    private void linkNext(){
        if(!playingStarted||!prefetchedReady||prefetchedLinked)return;
        try{
            if(!playing.isPlaying())return;
            playing.setNextMediaPlayer(prefetched);
            prefetchedLinked=true;
        }catch(IllegalStateException|IllegalArgumentException ignored){/* Fall back to the completion callback. */}
    }
    private void start(MediaPlayer player,String key,boolean alreadyStarted){
        if(activity.music!=null)activity.music.setDucked(true);
        try{
            if(!alreadyStarted)player.start();
            watch(player,Math.max(0,player.getDuration())+8000L);
        }catch(IllegalStateException error){finished(player,false);return;}
        playingStarted=true;
        if((activity.getApplicationInfo().flags&android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE)!=0)
            Log.d("MangwonVoice",SystemClock.uptimeMillis()+" "+key);
        prefetch();linkNext();
    }
    private void finished(MediaPlayer player,boolean reachedEnd){
        ui.removeCallbacksAndMessages(player);
        if(prefetched==player){
            prefetched=null;prefetchedReady=false;prefetchedLinked=false;
            player.release();prefetch();return;
        }
        if(playing!=player)return;
        playing=null;playingStarted=false;player.release();
        if(prefetched!=null){
            playing=prefetched;String key=prefetchedKey;boolean ready=prefetchedReady;
            boolean linked=prefetchedLinked;
            prefetched=null;prefetchedReady=false;prefetchedLinked=false;
            if(ready)start(playing,key,linked&&reachedEnd);
        }else playNext();
    }
    private void finishQueue(){
        if(activity.music!=null)activity.music.setDucked(false);
        held=false;previewing=false;tournamentQueue=false;cashEntryQueue=false;activity.talkingUntil=0;
    }
    void stop(){
        generation++;pending.clear();recorded.clear();ui.removeCallbacksAndMessages(null);
        if(playing!=null){MediaPlayer p=playing;playing=null;p.release();}
        if(prefetched!=null){MediaPlayer p=prefetched;prefetched=null;p.release();}
        playingStarted=false;prefetchedReady=false;prefetchedLinked=false;
        finishQueue();
    }
    void release(){stop();}
}
