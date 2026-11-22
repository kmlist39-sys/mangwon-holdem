package kr.mangwon.holdem;

import android.graphics.Bitmap;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.util.Log;
import org.json.JSONArray;
import org.json.JSONObject;

/** A player's pub portrait, table habits and actual recorded hands. */
final class CharacterProfile {
    private final Arena a;
    private final JSONObject[] stories;
    private final Path portraitClip=new Path();
    private final Rect src=new Rect();
    private final RectF dst=new RectF();
    private static final float[] SOURCE_X={.005f,.12f,.88f,.995f};
    private static final float[] SOURCE_Y={.12f,.27f,.31f,.70f,.74f,.88f};
    private static final float[] FRAME_X={66,113,967,1014};
    private static final float[] FRAME_Y={15,43,224,296,477,505};
    private static final String[] TIERS={"준프로","프로","탑티어"};

    CharacterProfile(Arena arena){
        a=arena;
        stories=new JSONObject[CastArt.COUNT];
        try{
            JSONArray data=new JSONArray(new String(a.host.bytes("characters.json"),"UTF-8"));
            for(int i=0;i<data.length();i++){
                JSONObject row=data.getJSONObject(i);
                int id=row.getInt("id");
                if(id>=0&&id<stories.length)stories[id]=row;
            }
        }catch(Exception e){Log.w("Mangwon","Character stories unavailable",e);}
    }

    void draw(){
        a.hits.clear();
        a.box(0,0,1080,520,0,0xd308100d);
        frame();
        int id=a.profile;
        JSONObject person=a.people.optJSONObject(id);
        if(person==null||id<0||id>=stories.length){
            a.mid("인물 정보를 불러오는 중",540,263,20,a.paper);
            a.brass("돌아가기",852,48,112,"close");
            return;
        }
        JSONObject story=stories[id];
        String name=person.optString("name");
        String job=person.optString("job");
        String alias=story==null?person.optString("alias"):story.optString("alias");
        String line=story==null?person.optString("line"):story.optString("line");
        int pub=Math.max(0,Math.min(a.pubs.length-1,person.optInt("pub")));
        int tier=Math.max(0,Math.min(2,person.optInt("tier")));
        String skill=person.optString("skill",TIERS[tier]);

        a.box(99,50,357,308,7,0xffb19563);
        portraitClip.reset();portraitClip.addRoundRect(102,53,354,305,5,5,Path.Direction.CW);
        a.c.save();a.c.clipPath(portraitClip);
        a.person(id,102,53,252,true);
        a.grad(102,205,354,305,0x00101713,0xf9101713,true);
        a.stage.bet.fitted(name,117,290,225,37,a.paper,Paint.Align.LEFT);
        a.c.restore();
        a.stage.plate(109,65,185,91,tier==2?1:0);
        a.mid(skill,147,83,15,tier==2?a.ink:a.paper);
        a.mid(alias.isEmpty()?job:alias,228,340,21,a.gold);
        a.stage.bet.fitted(job+"  ·  "+a.pubs[pub],228,366,273,15,a.paper,Paint.Align.CENTER);
        if(!line.isEmpty()){
            a.line(167,386,289,386,0xff83734d,1);
            centered("“"+line+"”",228,418,250,17,23);
        }
        a.line(383,63,383,458,0xff746648,1);
        a.text("동네 이야기",410,79,19,a.gold,a.bold,Paint.Align.LEFT);
        a.brass(a.profileReturn.equals("tables")?"참가자 목록":"테이블로",852,48,112,"close");
        a.line(409,94,963,94,0xff796947,1);
        String bio=person.optString("bio");
        String scene=story==null?bio:story.optString("scene",bio);
        a.wrapped(scene,410,127,543,20,a.paper,3);

        a.text("플레이 성향",410,222,16,a.gold,a.bold,Paint.Align.LEFT);
        a.wrapped(person.optString("style"),410,250,545,18,a.paper,2);
        String tell=story==null?person.optString("tell"):story.optString("tell");
        if(!tell.isEmpty()){
            a.text("테이블 버릇",410,306,16,a.gold,a.bold,Paint.Align.LEFT);
            a.wrapped(tell,410,334,545,17,a.paper,2);
        }
        a.line(409,383,963,383,0xff796947,1);
        JSONObject history=person.optJSONObject("history");
        int count=history==null?0:history.optInt("n");
        a.text("경기 기록",410,412,16,a.gold,a.bold,Paint.Align.LEFT);
        a.text("기록된 핸드  "+a.fmt(count),957,412,15,a.muted,a.normal,Paint.Align.RIGHT);
        String last=history==null?"":history.optString("note");
        a.wrapped(last.isEmpty()?"아직 기록된 쇼다운이 없습니다.":"최근 쇼다운  "+last,410,445,545,16,a.paper,2);
    }

    private void centered(String value,float x,float y,float width,float font,float leading){
        String rest=a.tr(value);
        for(int i=0;i<3&&!rest.isEmpty();i++){
            a.reset(a.paper);a.p.setTextSize(font);a.p.setTypeface(a.normal);
            int n=a.p.breakText(rest,true,width,null);
            if(n<=0)return;
            int word=rest.lastIndexOf(' ',n);
            if(n<rest.length()&&word>n/2)n=word;
            a.text(rest.substring(0,n).trim(),x,y+leading*i,font,a.paper,a.normal,Paint.Align.CENTER);
            rest=rest.substring(n).trim();
        }
    }

    void frame(){
        Bitmap plaque=a.stage.bet.plaque;
        if(plaque==null){a.box(69,21,1011,499,15,0xff193328);return;}
        a.reset(-1);
        for(int row=0;row<SOURCE_Y.length-1;row++)for(int col=0;col<SOURCE_X.length-1;col++){
            src.set(Math.round(SOURCE_X[col]*plaque.getWidth()),Math.round(SOURCE_Y[row]*plaque.getHeight()),
                Math.round(SOURCE_X[col+1]*plaque.getWidth()),Math.round(SOURCE_Y[row+1]*plaque.getHeight()));
            dst.set(FRAME_X[col],FRAME_Y[row],FRAME_X[col+1],FRAME_Y[row+1]);
            a.c.drawBitmap(plaque,src,dst,a.p);
        }
    }
}
