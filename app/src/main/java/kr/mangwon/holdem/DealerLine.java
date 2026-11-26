package kr.mangwon.holdem;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** A complete spoken sentence and its corresponding recorded Google phrases. */
final class DealerLine {
    final String text;
    final List<String> clips=new ArrayList<>();
    int payoutPot=-1,payoutBoard=-1;
    String settlement="";
    DealerLine payout(int pot,int board){payoutPot=pot;payoutBoard=board;return this;}
    DealerLine(String text,String... keys){this.text=text;clips.addAll(Arrays.asList(keys));}
}
