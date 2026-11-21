package kr.mangwon.holdem;

import android.graphics.Paint;
import org.json.JSONArray;
import org.json.JSONObject;

/** The top three displayed stacks across the daily tournament. */
final class ChipLeaders {
    private ChipLeaders() {}

    static void draw(Arena a, boolean detail) {
        JSONArray leaders = a.g.optJSONArray("leaders");
        if (leaders == null || leaders.length() == 0) return;
        float left = detail ? 280 : 200;
        float right = detail ? 889 : 511;
        float top = detail ? 28 : 3;
        float bottom = detail ? 76 : 54;
        float inset = detail ? 28 : 16;
        float cell = (right - left - 2 * inset) / 3;
        a.stage.plate(left, top, right, bottom, 0);
        for (int i = 0; i < leaders.length(); i++) {
            JSONObject entry = leaders.optJSONObject(i);
            int rank = entry.optInt("rank");
            boolean tied = entry.optInt("tied") > 1;
            String label = i == 0 ? "칩리더" : rank + "위";
            if (tied) label = "공동 " + label;
            float x = left + inset + (i + .5f) * cell;
            if (i > 0) a.line(x - cell / 2, top + 9, x - cell / 2, bottom - 9, 0x796f806b, 1);
            String name = entry.optString("name");
            String chips = entry.optString("display");
            if (detail) {
                a.mid(label + "   " + name, x, top + 20, 15, a.gold);
                a.text(chips, x, bottom - 8, 17, a.paper, a.nums, Paint.Align.CENTER);
            } else {
                a.mid(label + "  " + name, x, top + 20, 11.5f, a.gold);
                a.text(chips, x, bottom - 12, 16, a.paper, a.nums, Paint.Align.CENTER);
            }
        }
    }
}
