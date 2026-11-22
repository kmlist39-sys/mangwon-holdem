package kr.mangwon.holdem;

import android.graphics.Paint;
import org.json.JSONObject;

/** A transfer from the player's chip stacks to the wallet at the end of a cash hand. */
final class WashMotion {
    private static final float WALLET_X = 950, WALLET_Y = 26;
    private static final int LIFE_MS = 1240;
    private final Arena arena;
    private String session = "", caption = "";
    private int seenCycle, pendingHand;
    private long pendingCredit, startedAt = -1;

    WashMotion(Arena arena) {
        this.arena = arena;
    }

    void observe(JSONObject old, JSONObject game, long now, boolean visible) {
        JSONObject stats = game == null ? null : game.optJSONObject("cashStats");
        if (stats == null) {
            session = "";
            pendingCredit = 0;
            startedAt = -1;
            return;
        }
        int cycle = stats.optInt("washedAt");
        String nextSession = game.optString("sessionId");
        if (old == null || old.optJSONObject("cashStats") == null || !session.equals(nextSession)) {
            session = nextSession;
            seenCycle = cycle;
            pendingCredit = 0;
            startedAt = -1;
            return;
        }
        if (cycle > seenCycle) {
            seenCycle = cycle;
            JSONObject wash = stats.optJSONObject("lastWash");
            pendingHand = cycle;
            pendingCredit = wash != null && wash.optInt("hand") == cycle ? wash.optLong("heroAmount") : 0;
        }
        if (!visible || game.optInt("hand") != pendingHand) {
            pendingCredit = 0;
            startedAt = -1;
            return;
        }
        // Ownership can be calculated before the last showdown card is presented.
        if (pendingCredit > 0 && "done".equals(game.optString("phase"))) {
            caption = "+" + arena.fmt(pendingCredit) + " P";
            pendingCredit = 0;
            startedAt = now;
        }
    }

    void drawChips() {
        long age = arena.now - startedAt;
        if (startedAt < 0 || age < 0 || age >= 790 || !arena.panel.isEmpty()) return;
        for (int i = 0; i < 3; i++) {
            float elapsed = age - i * 44;
            if (elapsed < 0 || elapsed > 680) continue;
            float phase = TitleSequence.range(elapsed, 0, 680);
            float u = TitleSequence.smooth(phase), v = 1 - u;
            // Follow the near rail below the community cards, then the far right aisle.
            float x = v*v*v*(316+i*3) + 3*v*v*u*445 + 3*v*u*u*1076 + u*u*u*WALLET_X;
            float y = v*v*v*(405-i*3) + 3*v*v*u*(343-i*3) + 3*v*u*u*(408+i*4) + u*u*u*WALLET_Y;
            int alpha = Math.round(245 * TitleSequence.range(elapsed, 0, 60));
            arena.c.save();
            arena.c.rotate((i-1)*12*(float)Math.sin(Math.PI*u), x, y);
            arena.stage.chips.disc(x, y, 17-6*u, i == 1 ? 2 : 0, alpha);
            arena.c.restore();
        }
    }

    void drawCredit() {
        long age = arena.now - startedAt;
        if (startedAt < 0 || age < 550 || age >= LIFE_MS || !arena.panel.isEmpty()) return;
        float appear = TitleSequence.smooth(TitleSequence.range(age, 550, 160));
        float leave = TitleSequence.smooth(TitleSequence.range(age, 1010, LIFE_MS-1010));
        float opacity = appear * (1-leave);
        float baseline = 58 + (1-appear)*4 - leave*3;
        arena.reset(-1);
        arena.p.setTypeface(arena.nums);
        arena.p.setTextSize(15);
        arena.p.setTextAlign(Paint.Align.CENTER);
        arena.p.setColor((Math.round(255*opacity)<<24) | 0x00f7d69b);
        arena.p.setShadowLayer(2, 0, 1, (Math.round(225*opacity)<<24) | 0x00101815);
        arena.c.drawText(arena.tr(caption), WALLET_X, baseline, arena.p);
        arena.p.clearShadowLayer();
    }
}
