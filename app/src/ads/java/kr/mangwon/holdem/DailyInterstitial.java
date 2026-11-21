package kr.mangwon.holdem;

import android.os.SystemClock;
import android.util.Log;
import com.google.android.gms.ads.AdError;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.FullScreenContentCallback;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.interstitial.InterstitialAd;
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback;

/** Google's non-rewarded demo placement. No live interstitial placement is configured. */
final class DailyInterstitial {
    private final GameActivity host;
    private InterstitialAd ready;
    private long expiresAt, retryAt;
    private boolean loading, showing;

    DailyInterstitial(GameActivity host) { this.host=host; }
    boolean enabled() { return BuildConfig.TEST_ADS; }
    boolean showing() { return showing; }
    boolean readyNow() { return ready!=null && SystemClock.elapsedRealtime()<expiresAt; }

    void warm() {
        long now=SystemClock.elapsedRealtime();
        if (!enabled() || loading || showing || now<retryAt || (ready!=null && now<expiresAt)) return;
        ready=null;
        loading=true;
        InterstitialAd.load(host,host.getString(R.string.daily_interstitial_unit),new AdRequest.Builder().build(),new InterstitialAdLoadCallback(){
            @Override public void onAdLoaded(InterstitialAd ad) {
                loading=false;
                if (host.isDestroyed() || host.isFinishing()) return;
                ready=ad;expiresAt=SystemClock.elapsedRealtime()+55*60_000L;
                Log.i("DailyAd","Test interstitial ready");
            }
            @Override public void onAdFailedToLoad(LoadAdError error) {
                loading=false;retryAt=SystemClock.elapsedRealtime()+60_000L;
                Log.i("DailyAd","Load failed "+error.getCode()+" ("+error.getDomain()+")");
            }
        });
    }

    void tryShow(Runnable resume) {
        InterstitialAd ad=ready;
        ready=null;
        if (!enabled() || ad==null || showing || SystemClock.elapsedRealtime()>=expiresAt) {
            Log.i("DailyAd","No ready test interstitial; continuing");
            resume.run();return;
        }
        showing=true;
        host.music.setActive(false);
        FullScreenContentCallback callback=new FullScreenContentCallback(){
            private boolean consumed;
            private void complete() {
                if (consumed) return;
                consumed=true;showing=false;
                if (host.isDestroyed() || host.isFinishing()) return;
                host.music.setActive(host.active);
                resume.run();
            }
            @Override public void onAdShowedFullScreenContent() { Log.i("DailyAd","Test interstitial shown"); }
            @Override public void onAdDismissedFullScreenContent() { Log.i("DailyAd","Test interstitial closed");complete(); }
            @Override public void onAdFailedToShowFullScreenContent(AdError error) {
                Log.i("DailyAd","Show failed "+error.getCode());complete();
            }
        };
        ad.setFullScreenContentCallback(callback);
        try {
            ad.show(host);
        } catch (RuntimeException error) {
            // No overlay opened. Keep the held deal on the same resume path.
            callback.onAdFailedToShowFullScreenContent(new AdError(0,"Unable to show test interstitial","app"));
        }
    }
}
