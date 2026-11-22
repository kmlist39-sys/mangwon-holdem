package kr.mangwon.holdem;

import android.content.Context;
import android.util.LruCache;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Iterator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Localizes the presentation boundary. Saves, dealer cues and poker actions keep stable IDs. */
final class GameLanguage {
    private final HashMap<String,String> phrases=new HashMap<>();
    private final Node root=new Node();
    private final LruCache<String,String> rendered=new LruCache<>(768);
    private static final Pattern TABLE=Pattern.compile("(\\d+)번 테이블");
    private static final Pattern PLACE=Pattern.compile("(\\d+)위");
    private static final Pattern HAND=Pattern.compile("(\\d+)번째 핸드");
    private static final Pattern MAN=Pattern.compile("([0-9]+)만");
    private static final class Node {
        HashMap<Character,Node> next;
        String word;
    }
    static boolean english(Context context){
        return !"ko".equals(context.getResources().getConfiguration().getLocales().get(0).getLanguage());
    }
    GameLanguage(Context context){
        try(BufferedReader reader=new BufferedReader(new InputStreamReader(
                context.getAssets().open("english.json"),StandardCharsets.UTF_8))){
            StringBuilder data=new StringBuilder();String line;
            while((line=reader.readLine())!=null)data.append(line);
            JSONObject dict=new JSONObject(data.toString());
            for(Iterator<String> it=dict.keys();it.hasNext();){
                String key=it.next(),value=dict.getString(key);
                phrases.put(key,value);
                Node at=root;
                for(int i=0;i<key.length();i++){
                    if(at.next==null)at.next=new HashMap<>();
                    at=at.next.computeIfAbsent(key.charAt(i),unused->new Node());
                }
                at.word=value;
            }
        }catch(IOException|org.json.JSONException e){throw new IllegalStateException("English catalogue",e);}
    }
    private static final String RANK="(10|[2-9]|잭|퀸|킹|에이스)";
    private static final Pattern HAND_NAME=Pattern.compile(RANK+"(?:·"+RANK+")? (스트레이트 플러시|스트레이트|플러시|하이카드|원페어|투페어|트리플|풀하우스|포카드)");
    private static final String[] CARD_VALUES={"2","3","4","5","6","7","8","9","10","잭","퀸","킹","에이스"};
    private static final String[] SINGULAR={"Two","Three","Four","Five","Six","Seven","Eight","Nine","Ten","Jack","Queen","King","Ace"};
    private static final String[] PLURAL={"twos","threes","fours","fives","sixes","sevens","eights","nines","tens","jacks","queens","kings","aces"};
    private static String card(String token,boolean plural){
        for(int i=0;i<CARD_VALUES.length;i++)if(CARD_VALUES[i].equals(token))return plural?PLURAL[i]:SINGULAR[i];
        return token;
    }
    private static String handNames(String input){
        Matcher ranks=HAND_NAME.matcher(input);
        if(!ranks.find())return input;
        StringBuffer result=new StringBuffer();
        do{
            String rank=ranks.group(1),other=ranks.group(2),kind=ranks.group(3);
            String many=card(rank,true),high=card(rank,false),text;
            switch(kind){
                case "원페어":text="Pair of "+many;break;
                case "투페어":text="Two pair, "+many+" and "+card(other,true);break;
                case "트리플":text="Three "+many;break;
                case "풀하우스":text=Character.toUpperCase(many.charAt(0))+many.substring(1)+" full of "+card(other,true);break;
                case "포카드":text="Four "+many;break;
                case "스트레이트 플러시":text=high+"-high straight flush";break;
                case "스트레이트":text=high+"-high straight";break;
                case "플러시":text=high+"-high flush";break;
                default:text=high+" high";break;
            }
            ranks.appendReplacement(result,Matcher.quoteReplacement(text));
        }while(ranks.find());
        return ranks.appendTail(result).toString();
    }
    static boolean hangul(char c){return c>='가'&&c<='힣';}
    String get(String original){
        if(original==null||original.isEmpty())return "";
        String exact=phrases.get(original);
        if(exact!=null)return exact;
        boolean korean=false;
        for(int i=0;i<original.length();i++)if(hangul(original.charAt(i))){korean=true;break;}
        if(!korean)return original;
        String cached=rendered.get(original);
        if(cached!=null)return cached;
        String s=HAND.matcher(handNames(original)).replaceAll("Hand $1");
        s=TABLE.matcher(s).replaceAll("Table $1");
        s=PLACE.matcher(s).replaceAll("#$1");
        Matcher amounts=MAN.matcher(s);StringBuffer numbers=new StringBuffer();
        while(amounts.find()){
            long tenThousands=Long.parseLong(amounts.group(1));
            amounts.appendReplacement(numbers,tenThousands>=100&&tenThousands%100==0?tenThousands/100+"M":tenThousands*10+"K");
        }
        amounts.appendTail(numbers);s=numbers.toString();
        StringBuilder out=new StringBuilder();
        for(int i=0;i<s.length();){
            Node n=root;String last=null;int end=i,j=i;
            while(j<s.length()&&n.next!=null){
                n=n.next.get(s.charAt(j++));
                if(n==null)break;
                if(n.word!=null){last=n.word;end=j;}
            }
            if(last==null){out.append(s.charAt(i++));continue;}
            if(!last.isEmpty()&&out.length()>0&&i>0&&
                    Character.isLetterOrDigit(out.charAt(out.length()-1))&&Character.isLetterOrDigit(last.charAt(0)))out.append(' ');
            out.append(last);i=end;
        }
        String result=out.toString().trim();
        rendered.put(original,result);return result;
    }
}
