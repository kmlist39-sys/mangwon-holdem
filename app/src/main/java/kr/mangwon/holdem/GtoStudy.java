package kr.mangwon.holdem;

import android.graphics.Paint;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.Locale;

/** A frozen decision. The engine sends only the hero's hand and the board at that time. */
final class GtoStudy {
    private static final JSONObject SAMPLE=sample();

    private static JSONObject sample(){
        try{
            return new JSONObject()
                .put("hand",1).put("street",1).put("position","빅 블라인드").put("action","콜 1,000")
                .put("hole",new JSONArray("[11,10]")).put("boards",new JSONArray("[[9,5,39]]"))
                .put("opponents",1).put("pot",3000).put("call",1000).put("odds",25).put("stack",24000)
                .put("title","플러시 드로로 체크레이즈 섞기")
                .put("reason","플랍에서 체크한 뒤 1,000칩 베팅을 받았습니다. 콜하면 4,000칩 팟에 참여합니다. 상대가 자주 접는다면 두 장의 스페이드로 체크레이즈를 섞을 수 있습니다.");
        }catch(JSONException e){throw new IllegalStateException(e);}
    }

    static void draw(Arena a,boolean unlocked,boolean sample){
        a.hits.clear();
        a.box(0,0,1080,520,0,0xd408100e);
        a.character.frame();
        a.text("GTO 스터디",111,77,30,a.paper,a.title,Paint.Align.LEFT);
        a.txt(sample?"샘플 핸드":"내 결정 복기",329,73,15,a.gold);
        a.brass("닫기",861,46,99,"close");
        a.line(110,94,965,94,0xff8a7956,1);
        if(!sample&&!unlocked){
            a.mid("실전 핸드 복기",540,215,27,a.paper);
            a.mid("구독하면 실제 테이블에서 내가 선택했던 패를 여기서 복기할 수 있습니다.",540,258,18,a.muted);
            a.brass("무료 예시",355,334,168,"gtoSample");
            a.brass("구독 보기",558,334,168,"gtoStore");return;
        }
        if(!sample&&a.g!=null&&a.g.optBoolean("heroHoleHidden")){
            a.mid(a.getResources().getString(R.string.squeeze_study),540,236,20,a.paper);
            a.brass("샘플 핸드 보기",440,333,200,"gtoSample");return;
        }
        JSONObject spot=sample?SAMPLE:a.g==null?null:a.g.optJSONObject("study");
        if(spot==null)spot=a.snapshot.optJSONObject("study");
        if(spot==null){
            a.mid("첫 결정이 여기에 기록됩니다",540,222,25,a.paper);
            a.mid("테이블에서 한 번 행동한 뒤 내 패와 팟을 다시 볼 수 있습니다.",540,267,17,a.muted);
            a.brass("샘플 핸드 보기",440,333,200,"gtoSample");return;
        }
        int street=Math.max(0,Math.min(3,spot.optInt("street")));
        a.txt("핸드 "+spot.optInt("hand")+"   ·   "+a.streets[street]+"   ·   "+spot.optString("position"),112,123,17,a.gold);
        a.line(451,111,451,452,0xff79694d,1);
        a.txt("내 패",112,158,15,a.muted);
        cards(a,spot.optJSONArray("hole"),114,170,43,58);
        JSONArray boards=spot.optJSONArray("boards");
        for(int i=0;boards!=null&&i<boards.length();i++){
            int top=258+77*i;
            a.txt(boards.length()==1?"보드":i==0?"윗보드":"아랫보드",112,top,14,a.muted);
            JSONArray board=boards.optJSONArray(i);
            if(board==null||board.length()==0)a.txt("공개된 카드 없음",113,top+33,16,a.paper);
            else cards(a,board,114,top+7,36,47);
        }
        a.txt("상대 "+spot.optInt("opponents")+"명     내 스택 "+a.fmt(spot.optLong("stack")),112,435,15,a.muted);
        JSONObject forced=spot.optJSONObject("forced");
        if(forced!=null)a.txt("전원 앤티 "+a.fmt(forced.optLong("ante"))+" · SB "+a.fmt(forced.optLong("sb")),112,459,14,a.muted);
        a.txt("내 선택",475,133,15,a.muted);
        a.text(spot.optString("action"),475,170,29,a.paper,a.bold,Paint.Align.LEFT);
        a.txt("선택 전 팟",475,210,14,a.muted);
        a.txt(a.fmt(spot.optLong("pot")),475,236,21,a.paper);
        a.txt("콜 비용",632,210,14,a.muted);
        a.txt(a.fmt(spot.optLong("call")),632,236,21,a.paper);
        a.txt("팟 오즈",800,210,14,a.muted);
        double odds=spot.optDouble("odds",0);
        a.txt(spot.optLong("call")==0?"체크 가능":String.format(Locale.KOREA,"%.1f%%",odds),800,236,21,a.gold);
        a.line(473,254,958,254,0xff79694d,1);
        a.text(spot.optString("title"),474,291,21,a.gold,a.bold,Paint.Align.LEFT);
        a.wrapped(spot.optString("reason"),474,322,476,17,a.paper,4);
        if(!sample){
            String detail="상대 범위 추정 지분  "+spot.optInt("equity")+"%";
            if(spot.optBoolean("cash"))detail+="   ·   레이크 전";
            a.txt(detail,475,428,15,a.muted);
        }
        if(sample){
            if(unlocked)a.brass("내 결정 복기",778,436,178,"gtoReview");
            else a.brass("구독 보기",794,436,162,"gtoStore");
        }
    }

    private static void cards(Arena a,JSONArray values,int x,int y,int w,int h){
        if(values==null)return;
        for(int i=0;i<values.length();i++){
            int v=values.optInt(i,-1);
            if(v>=0&&v<52)a.card(v,x+i*(w+6),y,w,h,0,false);
        }
    }
}
