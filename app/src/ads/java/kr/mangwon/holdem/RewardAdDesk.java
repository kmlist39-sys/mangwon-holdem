package kr.mangwon.holdem;

import android.os.SystemClock;
import com.google.android.gms.ads.AdError;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.FullScreenContentCallback;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.rewarded.RewardedAd;
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback;
import com.google.android.ump.ConsentInformation;
import com.google.android.ump.ConsentRequestParameters;
import com.google.android.ump.UserMessagingPlatform;

/** Accepts a new opt-in at the cashier for each full-screen ad. */
final class RewardAdDesk {
    private final GameActivity host;
    private final ConsentInformation consent;
    private RewardedAd ready;
    private long loadedAt;
    private boolean requested, loading, showing, initializing, initialized, checkingConsent;
    private boolean consentChecked;
    private String failure = "";

    RewardAdDesk(GameActivity host) {
        this.host = host;
        consent = UserMessagingPlatform.getConsentInformation(host);
    }

    void start() {
        if (BuildConfig.TEST_ADS) {
            // Google's shared demo app has no publisher consent-message configuration.
            consentChecked = true;
        } else {
            checkConsent();
        }
    }

    private void checkConsent() {
        if (checkingConsent) return;
        checkingConsent = true;
        consent.requestConsentInfoUpdate(host, new ConsentRequestParameters.Builder().build(),
            () -> {
                if (host.isDestroyed()) return;
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(host, error -> finishConsent());
            }, error -> finishConsent());
    }

    private void finishConsent() {
        if (host.isDestroyed()) return;
        checkingConsent = false;
        consentChecked = consent.canRequestAds();
        if (requested) {
            if (consentChecked) load();
            else failed("광고 설정을 확인할 수 없습니다");
        }
        host.arena.invalidate();
    }

    boolean privacyRequired() {
        return !BuildConfig.TEST_ADS && consent.getPrivacyOptionsRequirementStatus() ==
            ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED;
    }

    void privacy() {
        if (!privacyRequired()) return;
        requested = false;
        ready = null;
        UserMessagingPlatform.showPrivacyOptionsForm(host, error -> {
            consentChecked = consent.canRequestAds();
            if (error != null) host.arena.note("광고 개인정보 설정을 열 수 없습니다");
            host.arena.invalidate();
        });
    }

    String button() {
        if (showing) return "광고 시청 중";
        if (requested) return "광고 불러오는 중";
        return failure.isEmpty() ? "광고 보기" : "다시 시도";
    }

    String caption() {
        if (!failure.isEmpty()) return failure;
        return BuildConfig.TEST_ADS ? "Google 테스트 광고 · 시청 완료 보상" : "광고 시청 완료 보상";
    }

    boolean enabled() { return !requested && !showing && host.arena.cashier.rewardEligible(); }
    void leave() { requested = false; }

    void watch() {
        if (!enabled() || !onCashier()) return;
        failure = "";
        requested = true;
        if (consentChecked) load();
        else checkConsent();
        host.arena.invalidate();
    }

    private boolean onCashier() {
        return host.active && !host.isFinishing() && !host.isDestroyed() && host.arena.panel.equals("shop");
    }

    boolean sdkReady() {
        if (initialized) return true;
        if (!consentChecked || initializing) return false;
        initializing=true;
        new Thread(() -> MobileAds.initialize(host.getApplicationContext(), status -> host.ui.post(() -> {
            if (host.isDestroyed()) return;
            initializing=false;initialized=true;
            if (requested) load();
            host.warmDailyAd();
        })), "MobileAdsInit").start();
        return false;
    }

    private void load() {
        if (!requested || !onCashier() || !host.arena.cashier.rewardEligible()) {
            requested = false;
            return;
        }
        if (!sdkReady()) return;
        if (ready != null && SystemClock.elapsedRealtime() - loadedAt < 55 * 60_000L) {
            show();
            return;
        }
        ready = null;
        if (loading) return;
        loading = true;
        RewardedAd.load(host, host.getString(R.string.rewarded_unit), new AdRequest.Builder().build(),
            new RewardedAdLoadCallback() {
                @Override public void onAdLoaded(RewardedAd ad) {
                    if (host.isDestroyed()) return;
                    loading = false;
                    ready = ad;
                    loadedAt = SystemClock.elapsedRealtime();
                    if (requested && onCashier()) show();
                    else requested = false;
                    host.arena.invalidate();
                }
                @Override public void onAdFailedToLoad(LoadAdError error) {
                    if (host.isDestroyed()) return;
                    android.util.Log.i("RewardAds", "Load " + error.getCode() + " (" + error.getDomain() + ")");
                    loading = false;
                    failed(error.getCode() == AdRequest.ERROR_CODE_NETWORK_ERROR ?
                        "네트워크 연결을 확인해 주세요" : "지금 볼 수 있는 광고가 없습니다");
                }
            });
    }

    private void failed(String message) {
        requested = false;
        failure = message;
        host.arena.invalidate();
    }

    private void show() {
        if (!requested || !onCashier() || !host.arena.cashier.rewardEligible()) {
            requested = false;
            return;
        }
        RewardedAd ad = ready;
        ready = null;
        requested = false;
        showing = true;
        final boolean[] credited = {false};
        ad.setFullScreenContentCallback(new FullScreenContentCallback() {
            @Override public void onAdDismissedFullScreenContent() {
                showing = false;
                if (!host.isDestroyed()) {
                    host.immersive();
                    host.arena.invalidate();
                }
            }
            @Override public void onAdFailedToShowFullScreenContent(AdError error) {
                showing = false;
                if (!host.isDestroyed()) failed("광고를 열 수 없습니다");
            }
        });
        ad.show(host, reward -> {
            if (credited[0]) return;
            credited[0] = true;
            host.recordAdCompletion();
        });
    }
}
