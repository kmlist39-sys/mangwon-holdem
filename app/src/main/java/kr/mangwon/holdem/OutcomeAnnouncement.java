package kr.mangwon.holdem;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Korean sentences use the actual credited winners of each pot and board. */
final class OutcomeAnnouncement {
    private static final String[] NUMBERS={"","","투","쓰리","포","파이브","식스","세븐","에이트","나인","텐"};
    private static final String[] ORDINALS={"메인 팟","첫 번째 사이드 팟","서드 팟","세 번째 사이드 팟","네 번째 사이드 팟","다섯 번째 사이드 팟","여섯 번째 사이드 팟","일곱 번째 사이드 팟","여덟 번째 사이드 팟"};
    private OutcomeAnnouncement() {}
    static String action(String kind){
        switch(kind){
            case "check":return "체크.";case "call":return "콜.";case "raise":return "레이즈.";
            case "quarter":return "쿼터.";case "half":return "하프.";case "pot":case "full":return "풀.";
            case "allin":return "올인.";case "fold":return "폴드.";default:return "";
        }
    }
    static String rank(String raw){
        String[] parts=raw.trim().split(" ",2);
        if(parts.length<2)return raw;
        String[] ranks=parts[0].split("·");String high=card(ranks[0]);
        if(ranks.length==2){
            String low=card(ranks[1]);
            if(parts[1].equals("풀하우스"))return high+" 트리플에 "+low+" 페어, 풀하우스";
            return high+particle(high,"와","과")+" "+low+" 투 페어";
        }
        return high+" "+parts[1].replace("원페어","원 페어").replace("하이카드","하이 카드");
    }
    private static String card(String value){
        try{int n=Integer.parseInt(value);if(n>=2&&n<=10)return NUMBERS[n];}catch(NumberFormatException ignored){}
        return value;
    }
    private static String particle(String value,String vowel,String consonant){
        int end=value.charAt(value.length()-1)-0xac00;
        return end<0||end>11171||end%28==0?vowel:consonant;
    }
    private static String instrumental(String value){
        int end=value.charAt(value.length()-1)-0xac00;
        return end>=0&&end<=11171&&end%28==8?"로":particle(value,"로","으로");
    }
    static String person(String name){return ("나".equals(name)?"플레이어":name)+" 님";}
    static DealerLine hand(String line){
        String[] p=line.split(",\\s*",2);
        if(p.length!=2)return null;
        String name=p[0].equals("나")?"플레이어":p[0];
        return new DealerLine(person(name)+"은 "+rank(p[1])+"입니다.","name:"+name,"rank:"+p[1]);
    }
    static List<DealerLine> phrases(JSONObject result,boolean dual){
        List<DealerLine> lines=new ArrayList<>();
        JSONArray pots=result.optJSONArray("pots");if(pots==null)return lines;
        boolean contested=result.optBoolean("contestedShowdown"),anyOddChip=false;
        for(int i=0;i<pots.length();i++){
            JSONObject pot=pots.optJSONObject(i);if(pot==null||pot.optLong("net")<=0)continue;
            JSONArray boards=pot.optJSONArray("boards");if(boards==null||boards.length()==0)continue;
            int index=pot.optInt("index",i);
            String prefix=pots.length()>1||dual&&contested?ORDINALS[Math.min(ORDINALS.length-1,index)]+"입니다. ":"";
            String potKey=prefix.isEmpty()?"":index==0?"result:main":index==2?"pot:third":"result:side"+index;
            if(!dual||!contested||pot.optBoolean("uncontested")||boards.length()!=2){
                JSONArray who=boards.optJSONObject(0).optJSONArray("winners");
                if(who!=null&&who.length()>0){
                    DealerLine line=new DealerLine(prefix+joined(who)+(who.length()==1?"이 팟을 가져갑니다.":"이 팟을 나눠 갖습니다."));
                    if(!potKey.isEmpty())line.clips.add(potKey);
                    winnerKeys(line,who,"won");lines.add(line);
                }
                continue;
            }
            JSONObject upper=boards.optJSONObject(0),lower=boards.optJSONObject(1);
            JSONArray a=upper.optJSONArray("winners"),b=lower.optJSONArray("winners");
            boolean hasUpper=upper.optLong("amount")>0&&a!=null&&a.length()>0;
            boolean hasLower=lower.optLong("amount")>0&&b!=null&&b.length()>0;
            if(!hasUpper&&!hasLower)continue;
            if(index==0||!hasUpper||!hasLower){
                if(hasUpper)lines.add(board(upper,true,prefix,potKey,index==0));
                if(hasLower)lines.add(board(lower,false,hasUpper?"":prefix,hasUpper?"":potKey,index==0));
                if(!hasUpper||!hasLower)continue;
            }
            Set<Integer> aa=ids(a),bb=ids(b);
            DealerLine summary;
            if(aa.size()==1&&bb.size()==1&&aa.equals(bb)){
                String winner=keyName(a.optJSONObject(0));
                summary=new DealerLine(person(winner)+"이 두 보드를 모두 이겼습니다. 스윕입니다.","name:"+winner,"result:scoop");
            }else if(aa.size()==1&&bb.size()==1){
                String first=keyName(a.optJSONObject(0)),second=keyName(b.optJSONObject(0));
                summary=index==0?new DealerLine("한 보드씩 이겨서 오십 대 오십, 찹입니다.","result:chop"):
                    new DealerLine("윗보드 승자는 "+person(first)+", 아랫보드 승자는 "+person(second)+"입니다. 오십 대 오십, 찹입니다.",
                        "board:upperWin","name:"+first,"board:lowerWin","name:"+second,"result:chop");
            }
            else if(aa.size()==1&&bb.size()==2&&bb.containsAll(aa))summary=threeQuarters(a,b);
            else if(bb.size()==1&&aa.size()==2&&aa.containsAll(bb))summary=threeQuarters(b,a);
            else if(aa.equals(bb)&&aa.size()==2){
                summary=new DealerLine((index==0?"":joined(a)+", ")+"두 보드 모두 공동 승리라, 반씩 나눕니다.");
                if(index!=0)namesOnly(summary,a);
                summary.clips.add("result:bothTie");
            }
            else if(index>0){
                lines.add(board(upper,true,prefix,potKey,false));
                lines.add(board(lower,false,"","",false));
                summary=null;
            }
            else summary=new DealerLine("각 보드의 승자들이 팟을 나눠 갖습니다.","result:shared");
            if(summary!=null){
                if(index>0){
                    DealerLine titled=new DealerLine(prefix+summary.text,potKey);
                    titled.clips.addAll(summary.clips);summary=titled;
                }
                lines.add(summary);
            }
            anyOddChip|=upper.optLong("amount")!=lower.optLong("amount")||unequal(a)||unequal(b);
        }
        if(anyOddChip)lines.add(new DealerLine("남는 칩까지 정산표에 반영했습니다.","result:rounding"));
        return lines;
    }
    private static DealerLine board(JSONObject board,boolean upper,String prefix,String potKey,boolean rankDetail){
        if(board==null||board.optLong("amount")<=0)return null;
        JSONArray who=board.optJSONArray("winners");if(who==null||who.length()==0)return null;
        String raw=rankDetail?who.optJSONObject(0).optString("rank",""):"";
        String winning=raw.isEmpty()?"":rank(raw);
        String with=winning.isEmpty()?"":winning+instrumental(winning)+" ";
        DealerLine line=new DealerLine(prefix+(upper?"윗보드는 ":"아랫보드는 ")+joined(who)+"이 "+with+(who.length()==1?"승리하셨습니다.":"공동으로 이겼습니다."));
        if(!potKey.isEmpty())line.clips.add(potKey);
        line.clips.add(upper?"board:upperWin":"board:lowerWin");
        namesOnly(line,who);
        if(who.length()>1)line.clips.add("board:joint");
        if(!raw.isEmpty())line.clips.add((who.length()==1?"winning:":"rank:")+raw);
        else if(who.length()==1&&rankDetail)line.clips.add("board:winEnd");
        return line;
    }
    private static void namesOnly(DealerLine line,JSONArray who){
        for(int i=0;i<who.length();i++){
            if(i>0&&i==who.length()-1)line.clips.add("board:and");
            line.clips.add("name:"+keyName(who.optJSONObject(i)));
        }
    }
    private static String keyName(JSONObject w){return w.optInt("id",-2)==-1?"플레이어":w.optString("name");}
    private static void winnerKeys(DealerLine line,JSONArray who,String solo){
        for(int i=0;i<who.length();i++)line.clips.add("result:"+(who.length()==1?solo:i==who.length()-1?"joint":"with")+":"+keyName(who.optJSONObject(i)));
    }
    private static DealerLine threeQuarters(JSONArray solo,JSONArray tied){
        JSONObject large=solo.optJSONObject(0),first=tied.optJSONObject(0);
        JSONObject small=first.optInt("id")==large.optInt("id")?tied.optJSONObject(1):first;
        String major=keyName(large),minor=keyName(small);
        return new DealerLine(person(major)+"은 칠십오 퍼센트, "+person(minor)+"은 이십오 퍼센트입니다. 찹앤찹입니다.",
            "name:"+major,"share:75","name:"+minor,"share:25");
    }
    private static String joined(JSONArray who){
        StringBuilder names=new StringBuilder();
        for(int i=0;i<who.length();i++){
            JSONObject w=who.optJSONObject(i);
            if(i>0)names.append(i==who.length()-1?"과 ":", ");
            names.append(person(w.optInt("id",-2)==-1?"플레이어":w.optString("name")));
        }
        return names.toString();
    }
    private static Set<Integer> ids(JSONArray who){
        Set<Integer> ids=new HashSet<>();for(int i=0;i<who.length();i++)ids.add(who.optJSONObject(i).optInt("id"));return ids;
    }
    private static boolean unequal(JSONArray who){
        long first=who.optJSONObject(0).optLong("amount");
        for(int i=1;i<who.length();i++)if(who.optJSONObject(i).optLong("amount")!=first)return true;
        return false;
    }
}
