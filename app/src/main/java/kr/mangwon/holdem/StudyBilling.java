package kr.mangwon.holdem;

import android.content.Context;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.SystemClock;
import android.util.Base64;
import com.android.billingclient.api.*;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.Collections;
import java.util.List;

/** Release access comes from Play receipts. The debug APK includes a clearly labelled study preview. */
final class StudyBilling {
    static final String PRODUCT = "gto_study_monthly";
    static final String BASE_PLAN = "monthly";
    private final GameActivity host;
    private final BillingClient client;
    private final PublicKey licenseKey;
    private ProductDetails product;
    private String offerToken = "", price = "", message = "";
    private boolean connecting, checking, checked, active, pending, buying, closed;
    private int request;
    private long lastCheck;

    StudyBilling(GameActivity host) {
        this.host = host;
        licenseKey = readKey(host.getString(R.string.play_licensing_key));
        if (BuildConfig.DEBUG) {
            client = null;
        } else if (!installedByPlay(host)) {
            client = null;
            message = "Google Play에서 설치한 버전에서 구독할 수 있습니다.";
        } else if (licenseKey == null) {
            client = null;
            message = "구독 판매를 준비하고 있습니다.";
        } else {
            client = BillingClient.newBuilder(host).setListener(this::purchaseUpdate)
                .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
                .enableAutoServiceReconnection().build();
        }
    }

    private static boolean installedByPlay(Context context) {
        try {
            String source = Build.VERSION.SDK_INT >= 30
                ? context.getPackageManager().getInstallSourceInfo(context.getPackageName()).getInstallingPackageName()
                : context.getPackageManager().getInstallerPackageName(context.getPackageName());
            return "com.android.vending".equals(source);
        } catch (Exception ignored) { return false; }
    }

    static PublicKey readKey(String value) {
        if (value.trim().isEmpty()) return null;
        try {
            return KeyFactory.getInstance("RSA").generatePublic(
                new X509EncodedKeySpec(Base64.decode(value.trim(), Base64.DEFAULT)));
        } catch (Exception invalid) { return null; }
    }

    static boolean validSignature(PublicKey key, String data, String signature) {
        if (key == null || data == null || signature == null) return false;
        try {
            Signature verifier = Signature.getInstance("SHA1withRSA");
            verifier.initVerify(key);
            verifier.update(data.getBytes(StandardCharsets.UTF_8));
            return verifier.verify(Base64.decode(signature, Base64.DEFAULT));
        } catch (Exception invalid) { return false; }
    }

    enum Receipt { INELIGIBLE, PENDING, ACKNOWLEDGE, ACTIVE }
    static Receipt receipt(Purchase purchase, String packageName, PublicKey publicKey) {
        if (!purchase.getProducts().contains(PRODUCT) || !packageName.equals(purchase.getPackageName())
                || purchase.isSuspended() || purchase.getPurchaseToken().isEmpty()
                || !validSignature(publicKey, purchase.getOriginalJson(), purchase.getSignature()))
            return Receipt.INELIGIBLE;
        if (purchase.getPurchaseState() == Purchase.PurchaseState.PENDING) return Receipt.PENDING;
        if (purchase.getPurchaseState() != Purchase.PurchaseState.PURCHASED) return Receipt.INELIGIBLE;
        return purchase.isAcknowledged() ? Receipt.ACTIVE : Receipt.ACKNOWLEDGE;
    }

    boolean unlocked() {
        return BuildConfig.DEBUG || (checked && active && SystemClock.elapsedRealtime() - lastCheck < 300000);
    }
    String storeLabel() { return unlocked() ? "GTO 가이드" : "GTO 구매하기"; }
    void renewDuringPlay(){
        if(client!=null&&active&&!checking&&SystemClock.elapsedRealtime()-lastCheck>=240000)refresh();
    }
    boolean canBuy() {
        return client != null && client.isReady() && checked && !checking && !active && !pending
            && !buying && product != null && !offerToken.isEmpty();
    }
    String displayPrice() { return price.isEmpty()?"월 2,900원 · 한국 기준":(GameLanguage.english(host)?price+" / month":"월 "+price); }
    String status() {
        if (BuildConfig.DEBUG) return "테스트 APK에서는 실시간 가이드가 무료로 켜져 있습니다.";
        if (pending) return "결제 대기 중 · Google Play 승인 후 이용할 수 있습니다.";
        if (unlocked()) return "구독 이용 중 · 테이블에서 실시간 가이드가 켜집니다.";
        if (!message.isEmpty()) return message;
        if (connecting || checking) return "Google Play에서 구독 정보를 확인하고 있습니다.";
        return product != null ? "Google Play에서 결제 금액과 갱신 조건을 확인하세요." : "구독 상품 정보를 확인하고 있습니다.";
    }
    void refresh() {
        if (client == null || closed || connecting) return;
        if (client.isReady()) { query(); return; }
        connecting = true;
        client.startConnection(new BillingClientStateListener() {
            public void onBillingSetupFinished(BillingResult result) {
                onUi(() -> {
                    connecting = false;
                    if (ok(result)) query();
                    else unavailable("Google Play에 연결할 수 없습니다.");
                });
            }
            public void onBillingServiceDisconnected() {
                onUi(() -> { connecting = false; unavailable("Google Play 연결이 끊겼습니다. 구독 확인을 눌러 주세요."); });
            }
        });
    }

    private void query() {
        if (checking || closed) return;
        if(!active)checked=false;
        checking = true; message = ""; product = null; offerToken = "";
        final int generation = ++request;
        QueryProductDetailsParams.Product item = QueryProductDetailsParams.Product.newBuilder()
            .setProductId(PRODUCT).setProductType(BillingClient.ProductType.SUBS).build();
        client.queryProductDetailsAsync(QueryProductDetailsParams.newBuilder()
            .setProductList(Collections.singletonList(item)).build(), (result, found) -> onUi(() -> {
                if (generation != request) return;
                product = null; offerToken = ""; price = "";
                if (ok(result)) for (ProductDetails candidate : found.getProductDetailsList()) select(candidate);
                if (product == null && !active && !pending) message = "현재 계정 또는 지역에서 구독 상품을 구매할 수 없습니다.";
                redraw();
            }));
        client.queryPurchasesAsync(QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.SUBS).includeSuspendedSubscriptions(false).build(),
            (result, purchases) -> onUi(() -> {
                if (generation != request) return;
                if (!ok(result)) { unavailable("구독 상태를 확인할 수 없습니다. 다시 확인해 주세요."); return; }
                apply(purchases, generation);
            }));
    }

    private void select(ProductDetails candidate) {
        if (!PRODUCT.equals(candidate.getProductId()) || candidate.getSubscriptionOfferDetails() == null) return;
        for (ProductDetails.SubscriptionOfferDetails offer : candidate.getSubscriptionOfferDetails()) {
            if (!BASE_PLAN.equals(offer.getBasePlanId()) || offer.getOfferId() != null) continue;
            List<ProductDetails.PricingPhase> phases = offer.getPricingPhases().getPricingPhaseList();
            if (phases.size() != 1) continue;
            ProductDetails.PricingPhase recurring = phases.get(0);
            if (!"P1M".equals(recurring.getBillingPeriod()) ||
                    recurring.getRecurrenceMode() != ProductDetails.RecurrenceMode.INFINITE_RECURRING ||
                    recurring.getPriceAmountMicros() <= 0) continue;
            product = candidate; offerToken = offer.getOfferToken(); price = recurring.getFormattedPrice();
            return;
        }
    }

    private void apply(List<Purchase> owned, int generation) {
        active = false; pending = false; lastCheck = SystemClock.elapsedRealtime();
        for (Purchase p : owned) {
            Receipt state = receipt(p, host.getPackageName(), licenseKey);
            if (state == Receipt.INELIGIBLE) continue;
            if (state == Receipt.PENDING) { pending = true; continue; }
            if (state == Receipt.ACTIVE) { active = true; pending = false; break; }
            client.acknowledgePurchase(AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(p.getPurchaseToken()).build(), result -> onUi(() -> {
                    if (generation != request) return;
                    checking = false;
                    if (ok(result)) {
                        pending = false; active = true; checked = true;
                        lastCheck = SystemClock.elapsedRealtime(); redraw();
                    }
                    else unavailable("Google Play의 구매 확인이 끝나면 이용할 수 있습니다.");
                }));
            return;
        }
        checking = false; checked = true;
        redraw();
    }

    void purchase() {
        if (!canBuy()) { if (!buying) refresh(); return; }
        BillingFlowParams.ProductDetailsParams item = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(product).setOfferToken(offerToken).build();
        buying = true;
        BillingResult result = client.launchBillingFlow(host, BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(Collections.singletonList(item)).build());
        if (!ok(result)) purchaseUpdate(result, null);
    }

    void manage() {
        Uri url = Uri.parse("https://play.google.com/store/account/subscriptions").buildUpon()
            .appendQueryParameter("sku", PRODUCT).appendQueryParameter("package", host.getPackageName()).build();
        try {
            host.startActivity(new Intent(Intent.ACTION_VIEW, url));
        } catch (ActivityNotFoundException unavailable) {
            message = "Google Play 앱의 결제 및 정기 결제 메뉴에서 관리할 수 있습니다."; redraw();
        }
    }

    private void purchaseUpdate(BillingResult result, List<Purchase> purchases) {
        onUi(() -> {
            buying = false;
            if (ok(result) || result.getResponseCode() == BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED) {
                ++request; checking = false; refresh();
            } else {
                message = result.getResponseCode() == BillingClient.BillingResponseCode.USER_CANCELED
                    ? "구독 결제를 취소했습니다." : "결제를 시작할 수 없습니다. Google Play에서 다시 확인해 주세요.";
                redraw();
            }
        });
    }

    private static boolean ok(BillingResult result) { return result.getResponseCode() == BillingClient.BillingResponseCode.OK; }
    private void unavailable(String reason) {
        ++request; active = false; pending = false; checking = false; checked = false; buying = false;
        product = null; offerToken = ""; price = ""; message = reason; redraw();
    }
    private void onUi(Runnable action) { host.runOnUiThread(() -> { if (!closed) action.run(); }); }
    private void redraw() { if (host.arena != null) host.arena.invalidate(); }
    void close() {
        closed = true;
        if (client != null) client.endConnection();
    }
}
