package kr.mangwon.holdem;

import android.content.res.AssetFileDescriptor;
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
    private String voice="";
    GeminiBank(GameActivity activity){
        this.activity=activity;
        try{
            recipes=new JSONObject(new String(activity.bytes("dealer-phrases.json"),StandardCharsets.UTF_8));
            JSONObject wins=new JSONObject(new String(activity.bytes("dealer-win-phrases.json"),StandardCharsets.UTF_8));
            boolean fullVocabulary=recipes.length()==710&&wins.length()==326;
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
        }catch(Exception ignored){}
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
        for(String key:line.clips){
            if(has(key)){out.add(key);continue;}
            JSONArray parts=recipes==null?null:recipes.optJSONArray(key);
            if(parts==null||parts.length()==0){out.clear();return out;}
            for(int i=0;i<parts.length();i++){
                String part=parts.optString(i);
                if(!has(part)){out.clear();return out;}
                out.add(part);
            }
        }
        return out;
    }
    AssetFileDescriptor open(String key)throws IOException{
        if(clips==null||!clips.has(key)||bad.contains(key))throw new IOException("Missing verified Gemini phrase "+key);
        return inspect(key);
    }
    private AssetFileDescriptor inspect(String key)throws IOException{
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
