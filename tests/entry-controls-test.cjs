const fs = require('node:fs');
const vm = require('node:vm');
const path = require('node:path');
const os = require('node:os');
const cp = require('node:child_process');
const assert = require('node:assert/strict');
const project = fs.existsSync(path.join(__dirname, 'club/app')) ? path.join(__dirname, 'club') : path.join(__dirname, '..');
const dir = path.join(project, 'app/src/main');
const arena = fs.readFileSync(path.join(dir, 'java/kr/mangwon/holdem/Arena.java'), 'utf8');
const section = (start, end) => {
  const left = arena.indexOf(start), right = arena.indexOf(end, left);
  assert(left >= 0 && right > left);
  return arena.slice(left, right);
};
const menu = JSON.parse(vm.runInNewContext(fs.readFileSync(path.join(dir, 'assets/club.js'), 'utf8')+'\nmobile(\'{"op":"init"}\')'));
function literal(value) {
  if (Array.isArray(value)) return 'new JSONArray('+value.map(literal).join(',')+')';
  if (value && typeof value === 'object') return 'new JSONObject()'+Object.entries(value).map(([k,v])=>'.put('+JSON.stringify(k)+','+literal(v)+')').join('');
  return JSON.stringify(value);
}
const java = `import java.util.*;
class JSONObject extends HashMap<String,Object>{
 public JSONObject put(String key,Object value){super.put(key,value);return this;}
 int optInt(String key){return optInt(key,0);}int optInt(String key,int backup){return get(key) instanceof Number n?n.intValue():backup;}
 boolean optBoolean(String key){return Boolean.TRUE.equals(get(key));}
 JSONArray optJSONArray(String key){return (JSONArray)get(key);}double optDouble(String key){return ((Number)get(key)).doubleValue();}
 boolean has(String key){return containsKey(key);}
}
class JSONArray extends ArrayList<Object>{JSONArray(Object... vals){addAll(Arrays.asList(vals));}int length(){return size();}JSONObject optJSONObject(int i){return (JSONObject)get(i);}}
class Rect{Rect(int... v){}}class RectF{RectF(float... v){}}
class Bitmap{int getWidth(){return 100;}int getHeight(){return 100;}}
class Canvas{void drawBitmap(Object... v){}void drawOval(Object... v){}void save(){}void translate(Object... v){}void scale(Object... v){}void restore(){}}
class Paint{enum Align{LEFT,RIGHT}enum Style{STROKE}void setStyle(Style s){}void setStrokeWidth(float w){}}
class Display{JSONObject offer;void entryPrizes(JSONObject selected){offer=selected;}}
class Honors{JSONObject offer;void entryFooter(JSONObject selected){offer=selected;}}
public class EntryControls {
  final FundingSlider cashDesk=new FundingSlider();class FundingSlider{void slider(float x){}}
  String[] pubs={"망원","마포","당산"};int[] tablesChoice={2,12};
  int ink,muted,gold,paper,bold,normal,title,fee,cashSeats=6,buy=200000,venue,cashLevel,raise;long now,feeChangedAt;
  boolean dbl,en;Bitmap chipArt;JSONObject g,snapshot;Paint p=new Paint();Canvas c=new Canvas();Display display=new Display();Honors honors=new Honors();
  final Map<String,String> keys=new HashMap<>();final List<String> labels=new ArrayList<>(),hits=new ArrayList<>();
  void reset(int v){}void grad(Object... v){}void text(Object... v){}void musicButton(float x,float y){}void brass(Object... v){}
  void chip(Object... v){}void bar(Object... v){}void mid(String label,Object... v){labels.add(label);}
  void txt(String label,Object... v){labels.add(label);}void hit(float l,float t,float r,float b,String k){hits.add(k);}
  String shortP(int n){return n%10000==0?n/10000+"만":fmt(n);}String fmt(long n){return String.format(Locale.US,"%,d",n);}
  void key(String label,float x,float y,float w,float h,String k,int on){keys.put(k,label+"/"+on);}
  ${section(' int selectedTournamentTables(){',' void overlay(){')}
  ${section(' void slider(String k,float x){',' @Override public boolean onTouchEvent')}
  void check(boolean value){if(!value)throw new AssertionError(keys+" "+labels);}
  void run(){
    snapshot=${literal({wallet:3000000,tournamentEntries:menu.tournamentEntries,cashLevels:menu.cashLevels})};
    for(venue=0;venue<2;venue++){
      for(int count:(venue==0?new int[]{2,3,4}:new int[]{12,24}))for(int amount:(venue==0?new int[]{50000,100000}:count==24?new int[]{100000,200000,500000}:new int[]{100000,150000,200000})){
        keys.clear();labels.clear();fee=amount;tablesChoice[venue]=count;dailyBuyIn();
        check(keys.get("field"+count).endsWith("/1"));check(display.offer.optInt("buyin")==amount);
        check(display.offer.optInt("pool")==count*9*amount*8/10);
        check(labels.stream().anyMatch(k->k.contains("참가 "+count*9+"명")&&k.contains(venue==0?"4분":"6분")));
        check(labels.stream().anyMatch(k->k.contains("리바인 "+shortP(amount)+"P / 6만칩")));
        check(honors.offer==display.offer);
        check((honors.offer.get("winnerTicket")!=null)==(venue==0));
      }
    }
    venue=2;for(cashLevel=0;cashLevel<2;cashLevel++)for(boolean two:new boolean[]{false,true}){
      int min=cashLevel==0?200000:500000,max=cashLevel==0?700000:2000000;
      dbl=two;keys.clear();snapshot.put("wallet",3000000);cashBuyIn();
      check(keys.get("stakes"+cashLevel).endsWith("/1"));check(keys.get(two?"double":"cash").endsWith("/1"));
      if(two){check(keys.get("stakes0").contains("SB 3만"));check(keys.get("stakes1").contains("SB 5만"));}
      slider("buy",297);check(buy==min);slider("buy",780);check(buy==max);slider("buy",538);check(buy>min&&buy<max&&buy%1000==0);
      snapshot.put("wallet",min+12345);slider("buy",780);check(buy==min+12345);
      snapshot.put("wallet",min-1);keys.clear();cashBuyIn();check(buy==min&&keys.containsKey("shop")&&!keys.containsKey("enter"));
    }
  }
  public static void main(String[] args){new EntryControls().run();}
}`;
const temp = fs.mkdtempSync(path.join(os.tmpdir(),'mangwon-entry-ui-'));
const file = path.join(temp,'EntryControls.java');
fs.writeFileSync(file,java);
const localJdk = process.env.USERPROFILE && path.join(process.env.USERPROFILE,'.jdks','jbr-21.0.11');
const jdk = process.env.JAVA_HOME || (localJdk && fs.existsSync(localJdk) ? localJdk : null);
const javaTool = name => jdk ? path.join(jdk,'bin',name+(process.platform==='win32'?'.exe':'')) : name;
cp.execFileSync(javaTool('javac'),['-encoding','UTF-8','-d',temp,file],{stdio:'inherit'});
cp.execFileSync(javaTool('java'),['-cp',temp,'EntryControls'],{stdio:'inherit'});
assert(arena.includes('"tables",venue<2?tablesChoice[venue]:0,"cashLevel",cashLevel'));
console.log('Native controls select all five tournament formats and both blind levels/boards. Preview pools, buy-ins, wallet caps, prices and 4/6 minute clocks verified.');
