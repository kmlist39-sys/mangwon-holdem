package kr.mangwon.holdem;

import android.graphics.Paint;
import org.json.JSONArray;
import org.json.JSONObject;

/** The public hand's share of each board, including its share of a tie. */
final class ShowdownOdds {
    private static final int IVORY = 0xfff8ead0, JADE = 0xffa6d9cc, GOLD = 0xffe7c48c;
    private static final String[] EXACT = new String[1001], ESTIMATES = new String[1001];

    private ShowdownOdds() {}

    static void hero(Arena a, JSONObject player) { draw(a,player,392,391,true); }

    static void seat(Arena a, JSONObject player, float x, float plateTop) {
        draw(a,player,x,plateTop,false);
    }

    static String percent(double value, boolean estimate) {
        if (!Double.isFinite(value) || value<0 || value>100) return "";
        if (value>0 && value<.1) return "<0.1%";
        if (value>99.9 && value<100) return ">99.9%";
        int tenth=(int)Math.round(value*10);
        String[] cache=estimate?ESTIMATES:EXACT;
        if (cache[tenth]==null) {
            String number=Integer.toString(tenth/10);
            if (tenth%10!=0) number+="."+tenth%10;
            cache[tenth]=(estimate?"≈":"")+number+"%";
        }
        return cache[tenth];
    }

    private static void draw(Arena a, JSONObject player, float x, float plateTop, boolean hero) {
        JSONObject equity=a.g.optJSONObject("equity");
        if (equity==null || player.optBoolean("fold") || a.deal.running()
            || a.g.optBoolean("showdownTitle") || a.g.optInt("pocketTurnMs",-1)>=0
            || a.g.optDouble("boardHold",0)>0 || "suspense".equals(a.g.optString("phase"))) return;
        boolean both=a.g.optBoolean("double");
        JSONArray hole=player.optJSONArray("hole");
        if (hole==null || hole.length()!=(both?3:2)) return;
        JSONObject seats=equity.optJSONObject("seats");
        JSONArray shares=seats==null?null:seats.optJSONArray(player.optString("id"));
        if (shares==null || shares.length()!=(both?2:1)) return;
        boolean estimate=equity.optBoolean("approximate");
        String first=percent(shares.optDouble(0),estimate);
        String second=both?percent(shares.optDouble(1),estimate):"";
        if (first.isEmpty() || (both && second.isEmpty())) return;

        if (hero) {
            // This strip sits between the pot receipt above and the payout/stack below.
            float right=432, left=both?277:estimate?315:333;
            a.stage.plate(left,343,right,366,0);
            if (both) {
                float middle=(left+right)*.5f;
                row(a,left+11,middle-5,359,"위",first,GOLD);
                row(a,middle+4,right-11,359,"아래",second,JADE);
            } else row(a,left+11,right-11,359,estimate?"추정 승률":"승률",first,GOLD);
            return;
        }

        // The outside shoulder is clear of the portrait's face, the action ribbon below
        // the stack, and the transient payout immediately above the nameplate.
        float center=x+(x<540?-46:46);
        float half=33;
        float bottom=plateTop-20, top=bottom-(both||estimate?37:23);
        a.stage.plate(center-half,top,center+half,bottom,0);
        if (both) {
            row(a,center-half+6,center+half-6,top+15,"위",first,GOLD);
            row(a,center-half+6,center+half-6,top+29,"아래",second,JADE);
        } else if (estimate) {
            a.text("추정 승률",center,top+14,10.5f,GOLD,a.bold,Paint.Align.CENTER);
            a.text(first,center,top+28,13,IVORY,a.nums,Paint.Align.CENTER);
        } else row(a,center-half+6,center+half-6,top+16,"승률",first,GOLD);
    }

    private static void row(Arena a, float left, float right, float baseline, String label, String value, int tint) {
        a.p.setTypeface(a.bold); a.p.setTextSize(10.5f);
        float space=right-left-a.p.measureText(label)-2;
        a.p.setTypeface(a.nums); a.p.setTextSize(12.8f);
        float font=12.8f*Math.min(1,space/a.p.measureText(value));
        a.text(label,left,baseline,10.5f,tint,a.bold,Paint.Align.LEFT);
        a.text(value,right,baseline,font,IVORY,a.nums,Paint.Align.RIGHT);
    }
}
