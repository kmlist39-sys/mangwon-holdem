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
    private final BitmapRegionDecoder[][] sheets = new BitmapRegionDecoder[2][PAGES+1];
    private final LruCache<Integer,Bitmap> seats = new LruCache<>(32);

    CastArt(Arena arena) { this.arena = arena; }

    Bitmap portrait(int id) {
        return portrait(id,false);
    }

    Bitmap portrait(int id,boolean folded) {
        if (id < 0 || id >= COUNT) return null;
        int key=id+(folded?COUNT:0);
        Bitmap cached = seats.get(key);
        if (cached != null) return cached;
        int index=id-ORIGINAL,page=index<0?0:1+index/PAGE_SIZE;
        int cell=index<0?arena.artMap[id]:index%PAGE_SIZE;
        int columns=page==0?10:COLUMNS,rows=page==0?5:COLUMNS;
        int pose=folded?1:0;
        try {
            BitmapRegionDecoder sheet = sheets[pose][page];
            if (sheet == null) {
                String file=page==0?(folded?"seat-fold.webp":"busts.png"):
                    (folded?"mapo-fold-":"mapo-cast-")+page+".webp";
                try (InputStream stream = arena.host.getAssets().open(file)) {
                    sheet = BitmapRegionDecoder.newInstance(stream, false);
                    sheets[pose][page] = sheet;
                }
            }
            int column=cell%columns,row=cell/columns;
            Rect frame=new Rect(column*sheet.getWidth()/columns,row*sheet.getHeight()/rows,
                (column+1)*sheet.getWidth()/columns,(row+1)*sheet.getHeight()/rows);
            Bitmap face = sheet.decodeRegion(frame, null);
            if (face != null) seats.put(key,face);
            return face;
        } catch (IOException e) {
            Log.w("Mangwon", "Portrait page " + (page+1), e);
            return null;
        }
    }
}
