package kr.mangwon.holdem;

/** The unconfigured offline build never requests an interstitial. */
final class DailyInterstitial {
    DailyInterstitial(GameActivity host) {}
    boolean enabled() { return false; }
    boolean showing() { return false; }
    boolean readyNow() { return false; }
    void warm() {}
    void tryShow(Runnable resume) { resume.run(); }
}
