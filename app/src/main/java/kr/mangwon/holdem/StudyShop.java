package kr.mangwon.holdem;

import android.graphics.Paint;

final class StudyShop {
    private StudyShop() {}

    static void draw(Arena a) {
        StudyBilling billing = a.host.studyBilling;
        a.hits.clear();
        a.box(0,0,1080,520,0,0xba0d1511);
        a.box(153,33,927,487,14,0xff1b2c25);
        a.stroke(153,33,927,487,14,0xffa38c62,1.3f);
        a.text("홀덤 공부",190,100,41,a.paper,a.title,Paint.Align.LEFT);
        a.brass("닫기",831,54,62,"close");
        a.line(191,121,892,121,0xff786849,1);
        a.card(50,217,168,74,108,-13,false);
        a.card(51,274,161,74,108,10,false);
        a.text("실시간 GTO 가이드",396,182,28,a.paper,a.title,Paint.Align.LEFT);
        a.txt("내 차례가 오면 테이블 오른쪽에 액션별 권장 빈도가 뜹니다.",396,219,16,a.paper);
        a.txt("가이드를 눌러 현재 팟, 콜 비용과 선택 근거를 확인하세요.",396,246,16,a.paper);
        a.txt(BuildConfig.DEBUG ? "테스트 이용 중 · 무료" : billing.displayPrice(),396,281,21,a.gold);
        a.line(191,307,892,307,0xff716447,1);
        a.txt(billing.status(),198,337,15,a.paper);
        a.txt(BuildConfig.DEBUG ? "정식 Google Play 버전의 구독료는 한국 기준 월 2,900원입니다."
            : "매월 자동 갱신됩니다. 취소는 Google Play 구독 관리에서 할 수 있습니다.",198,365,13,a.muted);
        a.key("무료 예시",192,390,173,51,"gtoSample",0);
        if (!BuildConfig.DEBUG) a.key("구독 확인",374,390,148,51,"gtoRefresh",0);
        if (billing.unlocked()) a.key("확인",539,390,348,51,"close",1);
        else if (billing.canBuy()) a.key("GTO 구매하기",539,390,348,51,"gtoPurchase",1);
        else {
            a.box(540,395,884,437,7,0xff2e3e35);
            a.mid("GTO 구매하기",711,422,17,a.muted);
        }
        if (!BuildConfig.DEBUG) a.brass("Google Play 구독 관리",658,448,227,"gtoManage");
    }
}
