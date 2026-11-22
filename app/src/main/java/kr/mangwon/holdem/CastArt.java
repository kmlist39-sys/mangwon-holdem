package kr.mangwon.holdem;

import android.graphics.Bitmap;
import android.graphics.BitmapRegionDecoder;
import android.graphics.Rect;
import android.util.Log;
import android.util.LruCache;
import java.io.IOException;
import java.io.InputStream;

/** Original regulars and the six painted pages of tournament entrants share stable IDs. */
final class CastArt {
    static final int ORIGINAL = 50;
    private static final int COLUMNS = 6, PAGE_SIZE = 36, PAGES = 6;
    static final int COUNT = ORIGINAL + PAGES * PAGE_SIZE;
    private final Arena arena;
    private final BitmapRegionDecoder[] pages = new BitmapRegionDecoder[PAGES];
    private final LruCache<Integer,Bitmap> seats = new LruCache<>(16);

    CastArt(Arena arena) { this.arena = arena; }

    Bitmap portrait(int id) {
        if (id < ORIGINAL || id >= COUNT) return null;
        Bitmap cached = seats.get(id);
        if (cached != null) return cached;
        int index = id - ORIGINAL, page = index / PAGE_SIZE, cell = index % PAGE_SIZE;
        try {
            BitmapRegionDecoder sheet = pages[page];
            if (sheet == null) {
                try (InputStream stream = arena.host.getAssets().open("mapo-cast-" + (page+1) + ".webp")) {
                    sheet = BitmapRegionDecoder.newInstance(stream, false);
                    pages[page] = sheet;
                }
            }
            int edge = sheet.getWidth() / COLUMNS;
            int left = (cell % COLUMNS) * edge, top = (cell / COLUMNS) * edge;
            Bitmap face = sheet.decodeRegion(new Rect(left, top, left+edge, top+edge), null);
            if (face != null) seats.put(id,face);
            return face;
        } catch (IOException e) {
            Log.w("Mangwon", "Portrait page " + (page+1), e);
            return null;
        }
    }
}
