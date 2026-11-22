package kr.mangwon.holdem;

import android.content.res.AssetFileDescriptor;
import android.os.ParcelFileDescriptor;
import org.json.JSONObject;
import org.json.JSONArray;
import java.io.InputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** A bank must identify a Gemini speech model and authenticate each bundled recording. */
final class GeminiBank {
    private final GameActivity activity;
    private final Set<String> inspected=new HashSet<>(),bad=new HashSet<>();
    private JSONObject clips,hashes,recipes;
    private boolean complete;
    private final boolean english;
    private String packedAudio,packedDigest;
    private JSONObject packedRanges;
    private boolean packedVerified,packedFailed;
    private String voice="";
    GeminiBank(GameActivity activity){
        this.activity=activity;
        english=GameLanguage.english(activity);
        try{
            if(english){
                JSONObject pack=new JSONObject(new String(activity.bytes("dealer-voice-en.json"),StandardCharsets.UTF_8));
                if(!usesDealerVoice(pack,true))return;
                configurePack("dealer-voice-en.ogg",pack);voice=pack.getString("voice");
                recipes=pack.getJSONObject("recipes");clips=packedRanges;
                complete=clips.has("check")&&clips.has("showdown")&&clips.has("hand:royal")&&clips.has("share:25");
                return;
            }
            recipes=new JSONObject(new String(activity.bytes("dealer-phrases.json"),StandardCharsets.UTF_8));
            JSONObject wins=new JSONObject(new String(activity.bytes("dealer-win-phrases.json"),StandardCharsets.UTF_8));
            boolean fullVocabulary=recipes.length()>=710&&wins.length()>=326;
            for(java.util.Iterator<String> i=wins.keys();i.hasNext();){String key=i.next();recipes.put(key,wins.getJSONArray(key));}
            JSONObject origin=new JSONObject(new String(activity.bytes("dealer-voice/source.json"),StandardCharsets.UTF_8));
            JSONObject index=new JSONObject(new String(activity.bytes("dealer-voice/index.json"),StandardCharsets.UTF_8));
            JSONObject sums=origin.getJSONObject("sha256");
            String model=origin.optString("model","").toLowerCase(java.util.Locale.ROOT);
            String provider=origin.optString("provider","");
            if(!"google-gemini".equals(provider)||!model.startsWith("gemini-")||!model.contains("tts"))return;
            if(index.length()==0||index.length()!=origin.optInt("count")||sums.length()<index.length())return;
            for(java.util.Iterator<String> it=index.keys();it.hasNext();){
                String k=it.next();JSONArray row=index.getJSONArray(k);
                if(row.length()!=2||row.getLong(1)<=0||!row.getString(0).matches("[a-zA-Z0-9_-]{1,96}")||!sums.optString(row.getString(0)).matches("[0-9a-fA-F]{64}"))return;
            }
            clips=index;hashes=sums;voice=origin.optString("voice","");
            complete=origin.optBoolean("complete")&&fullVocabulary&&index.has("share:75")&&index.has("share:25")&&index.has("pot:third");
            if(complete)for(java.util.Iterator<String> it=recipes.keys();it.hasNext();){
                String key=it.next();if(clips.has(key))continue;
                JSONArray parts=recipes.getJSONArray(key);
                for(int i=0;i<parts.length();i++)if(!clips.has(parts.getString(i)))complete=false;
            }
            JSONObject names=new JSONObject(new String(activity.bytes("dealer-names-ko.json"),StandardCharsets.UTF_8));
            if(!usesDealerVoice(names,false)||!"ko-KR".equals(names.optString("locale")))return;
            configurePack("dealer-names-ko.ogg",names);
            for(java.util.Iterator<String> it=packedRanges.keys();it.hasNext();){
                String key=it.next();
                if((!key.startsWith("name:")&&!"cash:added".equals(key))||clips.has(key))
                    throw new IOException("Invalid additional dealer recording "+key);
                clips.put(key,packedRanges.getJSONArray(key));
            }
        }catch(Exception ignored){}
    }
    private static boolean usesDealerVoice(JSONObject pack,boolean allowLiteModel){
        if(!"google-gemini".equals(pack.optString("provider"))||!"Veda".equals(pack.optString("voice"))
            ||!"gemini-3.8-flash-tts".equals(pack.optString("model")))return false;
        JSONArray sources=pack.optJSONArray("sources");
        if(sources==null||sources.length()==0)return false;
        for(int i=0;i<sources.length();i++){
            JSONObject source=sources.optJSONObject(i);
            if(source==null||!"Veda".equals(source.optString("voice")))return false;
            String model=source.optString("model");
            if(!"gemini-3.8-flash-tts".equals(model)
                &&!(allowLiteModel&&"gemini-3.8-flash-lite-tts".equals(model)))return false;
        }
        return true;
    }
    private void configurePack(String asset,JSONObject index)throws Exception{
        String digest=index.getString("sha256");
        if(!digest.matches("[a-f0-9]{64}"))throw new IOException("Invalid speech asset checksum");
        packedAudio=asset;packedDigest=digest;packedRanges=index.getJSONObject("clips");
    }
    boolean ready(){
        if(clips==null)return false;
        if(!inspected.isEmpty())return true;
        return sampleKey()!=null;
    }
    String sampleKey(){
        if(clips!=null)for(java.util.Iterator<String> i=clips.keys();i.hasNext();){
            String key=i.next();if(has(key))return key;
        }
        return null;
    }
    String status(){
        if(english)return ready()?"Google Gemini English · "+voice:"English dealer audio unavailable";
        if(!ready())return clips==null?"Google Gemini 한국어 음성 미탑재":"Google Gemini 음원 확인 필요";
        return complete&&bad.isEmpty()?"Google Gemini 한국어 · "+voice:"Google Gemini 시범 음성 · "+(clips.length()-bad.size())+"개";
    }
    boolean has(String key){
        if(clips==null||!clips.has(key)||bad.contains(key))return false;
        if(inspected.contains(key))return true;
        try(AssetFileDescriptor ignored=inspect(key)){return true;}catch(IOException error){return false;}
    }
    List<String> resolve(DealerLine line){
        List<String> out=new ArrayList<>();
        if(line==null)return out;
        String full="line:"+line.text;
        if(has(full)){out.add(full);return out;}
        List<String> parts=new ArrayList<>();
        for(String key:line.clips){
            if(key.startsWith("name:")){parts.add(key);continue;}
            if(has(key)){parts.add(key);continue;}
            JSONArray recipe=recipes==null?null:recipes.optJSONArray(key);
            String name="";
            if(recipe==null&&key.startsWith("result:")){
                int sep=key.indexOf(':',7);
                if(sep>0&&recipes!=null){
                    name=key.substring(sep+1);
                    recipe=recipes.optJSONArray(key.substring(0,sep+1)+"플레이어");
                }
            }
            if(recipe==null||recipe.length()==0)return out;
            for(int i=0;i<recipe.length();i++){
                String part=recipe.optString(i);
                parts.add(!name.isEmpty()&&part.equals("name:플레이어")?"name:"+name:part);
            }
        }
        for(int i=0;i<parts.size();i++){
            String key=parts.get(i);
            if(english&&key.startsWith("name:")&&!has(key)){
                String fallback=englishNameFallback(key.substring(5));
                if(!has(fallback)){out.clear();return out;}
                out.add(fallback);continue;
            }
            // Preserve the verified hand, result and board audio while an entrant's
            // proper name is unavailable. Never substitute another player's voice clip.
            if(key.startsWith("name:")&&!has(key))continue;
            if(!english&&key.equals("board:and")&&((i>0&&parts.get(i-1).startsWith("name:")&&!has(parts.get(i-1)))
                ||(i+1<parts.size()&&parts.get(i+1).startsWith("name:")&&!has(parts.get(i+1)))))continue;
            if(has(key)){out.add(key);continue;}
            out.clear();return out;
        }
        return out;
    }
    private String englishNameFallback(String name){
        JSONObject game=activity.arena==null?null:activity.arena.g;
        JSONArray seats=game==null?null:game.optJSONArray("p");
        if(seats!=null)for(int i=0;i<seats.length();i++){
            JSONObject seat=seats.optJSONObject(i);
            if(seat!=null&&name.equals(seat.optString("name")))return "seat:"+(i+1);
        }
        return "name:플레이어";
    }
    AssetFileDescriptor open(String key)throws IOException{
        if(clips==null||!clips.has(key)||bad.contains(key))throw new IOException("Missing verified Gemini phrase "+key);
        return inspect(key);
    }
    private AssetFileDescriptor inspect(String key)throws IOException{
        if(packedRanges!=null&&packedRanges.has(key)){
            if(packedFailed)throw new IOException("Recorded speech bank is unavailable");
            if(!packedVerified){
                try{
                    MessageDigest digest=MessageDigest.getInstance("SHA-256");
                    try(InputStream stream=activity.getAssets().open(packedAudio)){
                        byte[] bytes=new byte[8192];int length;
                        while((length=stream.read(bytes))!=-1)digest.update(bytes,0,length);
                    }
                    StringBuilder hex=new StringBuilder(64);
                    for(byte b:digest.digest()){hex.append(Character.forDigit((b&255)>>>4,16)).append(Character.forDigit(b&15,16));}
                    if(!hex.toString().equals(packedDigest))throw new IOException("Dealer bank checksum mismatch");
                    packedVerified=true;
                }catch(Exception e){packedFailed=true;bad.add(key);throw new IOException(e);}
            }
            JSONArray range=packedRanges.optJSONArray(key);
            if(range==null)throw new IOException("Missing recorded phrase bounds");
            try(AssetFileDescriptor file=activity.getAssets().openFd(packedAudio)){
                long start=range.optLong(0,-1),length=range.optLong(1,-1);
                if(start<0||length<=0||start>file.getLength()-length)throw new IOException("Invalid recorded phrase bounds");
                AssetFileDescriptor clip=new AssetFileDescriptor(ParcelFileDescriptor.dup(file.getFileDescriptor()),file.getStartOffset()+start,length);
                inspected.add(key);
                return clip;
            }
        }
        String id=clips.optJSONArray(key).optString(0),file="dealer-voice/"+id+".ogg";
        try{
            if(!inspected.contains(key)){
                MessageDigest md=MessageDigest.getInstance("SHA-256");
                try(InputStream in=activity.getAssets().open(file)){
                    byte[] buffer=new byte[8192];int n;while((n=in.read(buffer))!=-1)md.update(buffer,0,n);
                }
                StringBuilder digest=new StringBuilder();
                for(byte b:md.digest()){int v=b&255;digest.append(Character.forDigit(v>>>4,16)).append(Character.forDigit(v&15,16));}
                if(!digest.toString().equalsIgnoreCase(hashes.optString(id)))throw new IOException("Gemini audio hash does not match "+id);
                inspected.add(key);
            }
            return activity.getAssets().openFd(file);
        }catch(Exception error){inspected.remove(key);bad.add(key);throw new IOException(error);}
    }
}
