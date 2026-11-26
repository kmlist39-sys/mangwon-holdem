package kr.mangwon.holdem;

import java.util.ArrayList;
import org.json.JSONArray;
import org.json.JSONObject;

public class SettlementMotionTest {
    private static void check(boolean value,String reason){if(!value)throw new AssertionError(reason);}
    private static JSONObject person(int id,int amount){
        return new JSONObject().put("id",id).put("amount",amount);
    }
    private static JSONObject row(int board,JSONObject... awards){
        return new JSONObject().put("index",board).put("winners",new JSONArray(awards));
    }
    private static JSONObject pot(int index,int[] eligible,JSONObject... boards){
        return new JSONObject().put("index",index).put("eligible",new JSONArray(eligible)).put("boards",new JSONArray(boards));
    }
    private static JSONObject game(boolean dual){
        return new JSONObject().put("double",dual).put("hand",47).put("sessionId","test-cash").put("phase","showdown")
            .put("p",new JSONArray().put(person(13,0)).put(person(4,0)).put(person(-1,0)).put(person(31,0)));
    }
    private static JSONObject result(JSONObject game,JSONArray pots){
        return new JSONObject(game.toString()).put("phase","done")
            .put("settlement",new JSONObject().put("hand",47).put("contestedShowdown",true).put("pots",pots)
                .put("refunds",new JSONArray().put(person(31,9000))).put("rake",10000));
    }
    public static void main(String[] args) {
        JSONObject live=game(true);
        JSONArray pots=new JSONArray()
            .put(pot(0,new int[]{13,-1,31},row(1,person(-1,25000),person(13,25000)),row(0,person(-1,50001))))
            .put(pot(1,new int[]{13,31},row(0,person(31,12001),person(-1,9999)),row(1,person(13,12000))))
            .put(pot(2,new int[]{31},row(0,person(31,0)),row(1,person(31,0))));
        JSONObject paid=result(live,pots);
        ArrayList<SettlementMotion.Wave> waves=SettlementMotion.sequence(paid);
        check(waves.size()==4,"One wave per paid board; refunds and zero payouts excluded");
        check(waves.get(0).pot==0&&waves.get(0).board==0,"Upper board first even if JSON ordered differently");
        check(waves.get(1).pot==0&&waves.get(1).board==1&&waves.get(1).awards.size()==2,"Lower board is a genuine tie");
        check(waves.get(2).pot==1&&waves.get(2).awards.size()==1,"Only eligible side-pot winner");
        int hero=0,side=0;
        for(SettlementMotion.Wave wave:waves)for(SettlementMotion.Award award:wave.awards){
            if(award.id==-1){hero+=award.amount;check(award.position==0,"Hero remains at six o'clock");}
            if(award.id==13)check(award.position==2,"Rotate physical seats around the hero's actual index");
            if(wave.pot==1)side+=award.amount;
        }
        check(hero==75001&&side==24001,"Actual odd chip follows the ledger");
        SettlementMotion fresh=new SettlementMotion(null);
        fresh.observe(null,paid,100);check(!fresh.animating(110),"Saved settlement must stay still");
        SettlementMotion motion=new SettlementMotion(null);
        motion.observe(live,paid,100);check(motion.animating(110),"New award starts a sequence");
        motion.observe(paid,paid,9000);check(!motion.animating(9001),"Identical snapshots do not replay expired animation");
        motion.observe(paid,live,12000);check(!motion.animating(12000),"Clear as soon as the next hand enters");
        motion.observe(live,paid,12030);check(!motion.animating(12040),"Same settlement ID cannot double-pay visually");
        JSONObject other=result(game(false),new JSONArray().put(pot(0,new int[]{13},row(0,person(13,100)))));
        check(!SettlementMotion.handles(other),"Ordinary single-board pot remains in existing animation");
        other.optJSONObject("settlement").put("pots",pots);
        check(SettlementMotion.handles(other),"Single-board side pots are supported");
        JSONObject cash=game(true).put("cash",true);
        JSONArray seats=cash.optJSONArray("p");
        for(int i=0;i<seats.length();i++)seats.optJSONObject(i).put("stack",100000);
        JSONObject completed=result(cash,pots);
        JSONObject heroSeat=completed.optJSONArray("p").optJSONObject(2);
        heroSeat.put("stack",175001).put("won",75001);
        SettlementMotion visual=new SettlementMotion(null);
        visual.observe(cash,completed,1000);
        visual.heard("test-cash/47/47",0,0,3200);
        visual.heard("test-cash/47/47",0,1,6100);
        check(visual.presenting(1200)&&visual.balance(0,1200)==124002,"Net pot begins at eligible award total without rake/refunds");
        check(visual.stack(heroSeat,4000)==100000,"No seat credit ahead of first pot landing");
        check(visual.balance(0,4200)==74001&&visual.stack(heroSeat,4200)==150001,"First upper award follows the actual spoken cue");
        check(visual.balance(0,6700)==74001,"No second drain before lower board arrives");
        check(visual.balance(0,7100)==24001&&visual.stack(heroSeat,7100)==175001,"Lower tie adds its exact quarter independently");
        check(visual.balance(0,11000)==0&&!visual.presenting(11000),"All side pots exhaust and controls unlock");
        check(visual.stack(heroSeat,12000)==175001,"Repainting never credits again");
        SettlementMotion restore=new SettlementMotion(null);
        restore.observe(null,completed,1000);
        check(!restore.presenting(1000)&&restore.balance(99000,1000)==0&&restore.stack(heroSeat,1000)==175001,"Restore stays at final balances");
        paid.optJSONObject("settlement").put("contestedShowdown",false);
        check(SettlementMotion.sequence(paid).isEmpty(),"No board payments narrated after a fold");
        System.out.println("Settlement motion passed: dealer-timed upper/lower, 75:25, odd chips, rotated seats, side pots, rake/refunds, pot drain, seat stack, restore and input unlock");
    }
}
