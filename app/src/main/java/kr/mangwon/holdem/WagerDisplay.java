package kr.mangwon.holdem;

import android.graphics.Paint;
import org.json.JSONArray;
import org.json.JSONObject;

/** A live receipt on the felt and the paid actions in this betting round. */
final class WagerDisplay {
    private final Arena a;
    private int page;
    private static final int ROWS=6;
    WagerDisplay(Arena arena){a=arena;}

    private JSONArray entries(){return a.g==null?null:a.g.optJSONArray("wagers");}
    boolean visible(){
        JSONArray e=entries();
        return e!=null&&e.length()>0&&!a.deal.running()&&!a.g.optBoolean("rebuy")&&!a.g.optBoolean("break");
    }
    private String label(JSONObject e){
        if(e.optBoolean("allin"))return e.optString("kind").equals("call")?"올인 콜":"올인";
        if(e.optString("kind").equals("call"))return "콜";
        return e.optBoolean("opening")?"베팅":"레이즈";
    }
    private int amount(JSONObject e){return e.optString("kind").equals("raise")?e.optInt("total"):e.optInt("paid");}
    void draw(){
        if(!visible())return;
        JSONArray entries=entries();
        JSONObject e=entries.optJSONObject(entries.length()-1);
        if(e==null)return;
        a.stage.plate(655,395,1044,443,0);
        String move=e.optString("name")+"   "+label(e)+"   "+a.fmt(amount(e));
        a.text(move,683,425,19,a.paper,a.bold,Paint.Align.LEFT);
        boolean totalsDiffer=e.optInt("total")!=e.optInt("paid");
        String info=totalsDiffer?"이번 투입 "+a.fmt(e.optInt("paid")):a.streets[a.g.optInt("street")]+" 기록";
        a.text(info,1018,424,13,a.gold,a.normal,Paint.Align.RIGHT);
        a.hit(655,395,1044,443,"wagers");
    }
    void open(){page=0;}
    void turn(int delta){page+=delta;}
    void detail(){
        JSONArray e=entries();a.hits.clear();
        a.box(0,0,1080,520,0,0xbb101713);a.box(222,35,858,485,11,0xff1b3027);
        a.stroke(222,35,858,485,11,0xffa38a59,1);
        a.txt(a.streets[a.g.optInt("street")]+"  ·  상대 베팅",255,82,24,a.paper);
        a.brass("닫기",747,53,82,"close");
        a.txt("이름",264,133,14,a.muted);a.txt("행동",451,133,14,a.muted);
        a.text("이번에 낸 칩",658,133,14,a.muted,a.normal,Paint.Align.RIGHT);
        a.text("라운드 총액",808,133,14,a.muted,a.normal,Paint.Align.RIGHT);
        int count=e==null?0:e.length(),pages=Math.max(1,(count+ROWS-1)/ROWS);
        page=Math.max(0,Math.min(page,pages-1));
        for(int row=0;row<ROWS;row++){
            int index=count-1-(page*ROWS+row);
            if(index<0)break;
            JSONObject item=e.optJSONObject(index);float y=177+row*40;
            a.line(257,y-27,820,y-27,0xff485849,1);
            a.txt(item.optString("name"),264,y,17,a.paper);a.txt(label(item),451,y,16,a.gold);
            a.text(a.fmt(item.optInt("paid")),658,y,18,a.paper,a.nums,Paint.Align.RIGHT);
            a.text(a.fmt(item.optInt("total")),808,y,18,a.paper,a.nums,Paint.Align.RIGHT);
        }
        if(pages>1){a.mid((page+1)+" / "+pages,540,447,14,a.muted);
            if(page>0)a.brass("‹  최근",262,422,95,"newerWagers");
            if(page+1<pages)a.brass("이전  ›",721,422,95,"olderWagers");
        }
    }
}
