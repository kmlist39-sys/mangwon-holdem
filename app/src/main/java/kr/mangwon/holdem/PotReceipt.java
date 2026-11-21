package kr.mangwon.holdem;

import android.graphics.Paint;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;

final class PotReceipt {
    private static class Row {
        final String pot,board,player,rank;
        final int amount;
        Row(String p,String b,String who,String r,int n){pot=p;board=b;player=who;rank=r;amount=n;}
    }
    private final ArrayList<Row> rows=new ArrayList<>();
    private JSONObject settlement;
    private int page;
    void open(JSONObject data){
        settlement=data;rows.clear();page=0;
        if(data==null)return;
        JSONArray pots=data.optJSONArray("pots");
        for(int i=0;pots!=null&&i<pots.length();i++){
            JSONObject pot=pots.optJSONObject(i);JSONArray boards=pot.optJSONArray("boards");
            String name=i==0?"메인 팟":i==1?"사이드 팟":i==2?"서드 팟":"사이드 팟 "+i;
            for(int j=0;boards!=null&&j<boards.length();j++){
                JSONArray winners=boards.optJSONObject(j).optJSONArray("winners");
                for(int k=0;winners!=null&&k<winners.length();k++){
                    JSONObject win=winners.optJSONObject(k);
                    rows.add(new Row(name,boards.length()>1?(j==0?"위":"아래"):"",win.optString("name"),win.optString("rank"),win.optInt("amount")));
                }
            }
        }
        JSONArray refunds=data.optJSONArray("refunds");
        for(int i=0;refunds!=null&&i<refunds.length();i++){
            JSONObject r=refunds.optJSONObject(i);rows.add(new Row("미콜 반환","",r.optString("name"),"",r.optInt("amount")));
        }
    }
    void turn(int d){page=Math.max(0,Math.min((rows.size()-1)/7,page+d));}
    void draw(Arena a){
        a.hits.clear();a.box(0,0,1080,520,0,0xaa0c120f);a.box(137,35,943,483,10,0xff19251f);
        a.stroke(137,35,943,483,10,0xff7e7059,1);
        a.text("팟 정산 내역",171,85,28,a.paper,a.title,Paint.Align.LEFT);a.brass("닫기",822,55,87,"close");
        if(settlement==null)return;
        a.txt("총 베팅 "+a.fmt(settlement.optInt("committed"))+"   ·   레이크 "+a.fmt(settlement.optInt("rake"))+"   ·   지급 팟 "+a.fmt(settlement.optInt("net")),173,126,17,a.gold);
        a.line(172,143,906,143,0xff586451,1);
        a.txt("팟",173,171,14,a.muted);a.txt("보드",308,171,14,a.muted);a.txt("플레이어",385,171,14,a.muted);
        a.txt("족보",548,171,14,a.muted);a.text("지급 칩",903,171,14,a.muted,a.normal,Paint.Align.RIGHT);
        for(int i=page*7;i<Math.min(rows.size(),page*7+7);i++){
            Row r=rows.get(i);float y=204+(i%7)*32;
            a.txt(r.pot,173,y,16,a.paper);a.txt(r.board,308,y,16,a.muted);a.txt(r.player,385,y,16,a.paper);
            a.txt(r.rank,548,y,16,a.paper);a.text(a.fmt(r.amount),903,y,18,a.gold,a.nums,Paint.Align.RIGHT);
        }
        if(rows.size()>7){if(page>0)a.key("‹ 이전",172,425,106,36,"potPrev",0);if((page+1)*7<rows.size())a.key("다음 ›",798,425,106,36,"potNext",0);a.mid((page+1)+" / "+((rows.size()+6)/7),540,451,15,a.muted);}
    }
}
