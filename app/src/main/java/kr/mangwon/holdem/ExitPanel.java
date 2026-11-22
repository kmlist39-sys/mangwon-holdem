package kr.mangwon.holdem;

import org.json.JSONObject;

/** Pause menu and the point settlement the player must accept before leaving. */
final class ExitPanel {
    private ExitPanel() {}

    static void pause(Arena a) {
        a.mid("핸드와 블라인드 시계가 멈췄습니다.",540,140,19,a.paper);
        a.key("계속 플레이",345,159,390,50,"close",1);
        a.key("자리 보관하고 로비로",345,220,390,45,"parkVenue",0);
        a.key("게임 설정",345,276,390,43,"settings",0);
        if(a.g==null)return;
        JSONObject q=a.g.optJSONObject("exitQuote");
        if(q==null)return;
        if(a.g.optBoolean("cash")) {
            if(a.g.optString("phase").equals("done")&&q.optInt("completed")>=6)
                a.key("남은 칩 정산하고 퇴장",345,331,390,45,"leave",1);
            else a.mid("완료 "+q.optInt("completed")+"핸드  ·  정상 정산은 6핸드부터",540,357,14,a.muted);
        }
        a.key("포기하고 나가기",345,390,390,46,"surrender",0);
    }

    static void confirm(Arena a) {
        if(a.g==null)return;
        JSONObject q=a.g.optJSONObject("exitQuote");
        if(q==null)return;
        String kind=q.optString("kind"), title,subtitle,detail;
        if(kind.equals("daily")) {
            title="데일리 참가를 종료합니다";
            subtitle="납입한 바이인·리바인 합계   "+a.fmt(q.optInt("fees"))+" P";
            detail="보유한 대회 칩과 상금 자격을 포기합니다.";
        } else if(kind.equals("ranked")) {
            title="대회 "+q.optInt("place")+"위로 정산합니다";
            subtitle="확정된 순위 상금을 지갑에 지급합니다.";
            detail="퇴장하면 이번 대회가 종료됩니다.";
        } else {
            title="당산 "+(a.g.optBoolean("double")?"더블보드":"원보드")+"  ·  완료 "+q.optInt("completed")+" / 6핸드";
            subtitle="남은 칩   "+a.fmt(q.optInt("stack"))+" P      진행 중인 팟에 낸 칩   "+a.fmt(q.optInt("committed"))+" P";
            if(kind.equals("early"))detail="팟에 낸 칩 포기  ·  중도 퇴장 패널티 10%   −"+a.fmt(q.optInt("penalty"))+" P";
            else if(kind.equals("cashHand"))detail="현재 패와 팟에 낸 칩 포기  ·  중도 퇴장 패널티 0 P";
            else detail="핸드 정산 완료  ·  중도 퇴장 패널티 0 P";
        }
        a.mid(title,540,153,24,a.paper);
        a.mid(subtitle,540,204,16,a.muted);
        a.mid(detail,540,245,16,a.paper);
        a.mid("퇴장 실수령액",540,297,15,a.muted);
        a.mid(a.fmt(q.optInt("refund"))+" P",540,343,37,a.gold);
        a.key("돌아가기",278,387,245,52,"cancelExit",0);
        a.key("확인하고 퇴장",550,387,250,52,"exit@"+q.optString("token"),1);
    }
}
