package kr.mangwon.holdem;

import android.app.Activity;
import android.app.AlertDialog;
import android.widget.ScrollView;
import android.widget.TextView;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

final class PrivacyPolicy {
    private PrivacyPolicy() {}

    static void show(Activity activity) {
        StringBuilder content = new StringBuilder();
        try (InputStream stream = activity.getAssets().open("privacy.txt");
             BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append('\n');
            }
        } catch (IOException e) {
            content.append("개인정보처리방침을 열 수 없습니다. 문의 kmlist39@gmail.com");
        }
        int margin = Math.round(20 * activity.getResources().getDisplayMetrics().density);
        TextView text = new TextView(activity);
        boolean english=GameLanguage.english(activity);
        String body=content.toString().trim();
        int split=body.indexOf("Mangwon-dong Hold’em Privacy Policy");
        if(split>=0)body=(english?body.substring(split):body.substring(0,split)).trim();
        else if(english&&body.startsWith("개인정보처리방침을 열 수 없습니다"))body="Privacy policy unavailable. Contact kmlist39@gmail.com.";
        text.setText(body);
        text.setTextSize(16);
        text.setLineSpacing(0, 1.14f);
        text.setTextIsSelectable(true);
        text.setPadding(margin, margin / 2, margin, margin);
        ScrollView scroll = new ScrollView(activity);
        scroll.addView(text);
        new AlertDialog.Builder(activity)
            .setTitle(english?"Privacy Policy":"개인정보처리방침")
            .setView(scroll)
            .setPositiveButton(english?"Close":"닫기", null)
            .show();
    }
}
