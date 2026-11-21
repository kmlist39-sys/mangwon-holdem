package kr.mangwon.holdem;

import android.graphics.Paint;
import org.json.JSONObject;

/** The tournament desk's announcement for the next deal at this table. */
final class BlindTicker {
    private static final float LEFT = 692, RIGHT = 1068, TOP = 42, BOTTOM = 70;
    private static final float INSET = 23, TYPE_SIZE = 15;
    private static final long HOLD_MS = 2200;

    private final Arena arena;
    private final Paint type = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
    private String session = "", message = "";
    private int hand;
    private long small, big;
    private float textWidth;
    private long began;

    BlindTicker(Arena arena) { this.arena = arena; }

    void observe(JSONObject game, long now) {
        JSONObject upcoming = game == null ? null : game.optJSONObject("nextBlinds");
        if (upcoming == null) {
            message = "";
            return;
        }
        String id = game.optString("sessionId");
        int next = upcoming.optInt("hand");
        long sb = upcoming.optLong("sb"), bb = upcoming.optLong("bb");
        if (!message.isEmpty() && session.equals(id) && hand == next && small == sb && big == bb) return;
        session = id;
        hand = next;
        small = sb;
        big = bb;
        message = "다음 판부터 블라인드가 " + arena.fmt(sb) + " / " + arena.fmt(bb) + "으로 적용됩니다";
        type.setTypeface(arena.bold);
        type.setColor(arena.paper);
        type.setTextSize(TYPE_SIZE);
        textWidth = type.measureText(message);
        float usable = RIGHT - LEFT - 2 * INSET - 4;
        if (textWidth > usable) {
            type.setTextSize(TYPE_SIZE * usable / textWidth);
            textWidth = type.measureText(message);
        }
        began = now;
    }

    void draw(long now) {
        if (message.isEmpty()) return;
        final float innerLeft = LEFT + INSET, innerRight = RIGHT - INSET;
        final float centered = (innerRight + innerLeft - textWidth) * .5f;
        // Settle the first pass quickly so a short hand still gets a readable notice.
        final long age = Math.max(0, now - began);
        final float x;
        if (age < 350) {
            float u = 1 - age / 350f;
            x = centered + (innerRight - centered) * u * u * u;
        } else {
            final float speed = .092f;
            final float distance = innerRight - innerLeft + textWidth;
            final float pass = (age - 350) % (HOLD_MS + distance / speed);
            float pos = centered - Math.max(0, pass - HOLD_MS) * speed;
            if (pos < innerLeft - textWidth) pos += distance;
            x = pos;
        }
        arena.stage.plate(LEFT, TOP, RIGHT, BOTTOM, 0);
        int saved = arena.c.save();
        arena.c.clipRect(innerLeft, TOP + 3, innerRight, BOTTOM - 3);
        arena.c.drawText(message, x, (TOP + BOTTOM - type.ascent() - type.descent()) * .5f, type);
        arena.c.restoreToCount(saved);
    }
}
