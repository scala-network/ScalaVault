// Copyright (c) 2020, Scala
//
// Please see the included LICENSE file for more information.

package io.scalaproject.vault;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;

import io.scalaproject.vault.dialog.PrivacyFragment;
import timber.log.Timber;

public class WizardHomeActivity extends BaseActivity
{
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if ((getIntent().getFlags() & Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT) != 0) {
            // Activity was brought to front and not created,
            // Thus finishing this will get us to the last viewed activity
            finish();
            return;
        }

        setContentView(R.layout.fragment_wizard_home);

        View view = findViewById(android.R.id.content).getRootView();

        String sDisclaimerText = getResources().getString(R.string.privacy_policy_intro);
        String sDiclaimer = getResources().getString(R.string.privacy_policy_label);

        SpannableString ss = new SpannableString(sDisclaimerText);
        ClickableSpan clickableSpan = new ClickableSpan() {
            @Override
            public void onClick(@NonNull View textView) {
                showPrivacyPolicy();
            }

            @Override
            public void updateDrawState(@NonNull TextPaint ds) {
                super.updateDrawState(ds);
                ds.setUnderlineText(false);
            }
        };

        // This fixes default home page on wizard screen
        // Uses per default system language so configured app stays same language
        // need test for compatibility
        int iStart = sDisclaimerText.indexOf(sDiclaimer);
        if (iStart != -1) {// Check if sDiclaimer is found
            int iEnd = iStart + sDiclaimer.length();
            ss.setSpan(clickableSpan, iStart, iEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        } else {
            // Handle the case where sDiclaimer is not found
            // You might want to log a warning or display an error message
            Timber.w("Privacy policy label not found in disclaimer text");
        }

        TextView tvDisclaimer = view.findViewById(R.id.disclaimer);
        tvDisclaimer.setText(ss);
        tvDisclaimer.setMovementMethod(LinkMovementMethod.getInstance());
        tvDisclaimer.setLinkTextColor(getResources().getColor(R.color.c_blue));
        tvDisclaimer.setHighlightColor(Color.TRANSPARENT);
    }

    public void onCreateWallet(View view) {
        Intent intent = new Intent(WizardHomeActivity.this, LoginActivity.class);
        intent.putExtra("GenerateFragmentType", GenerateFragment.TYPE_NEW);
        startActivity(intent);
        finish();

        Config.write(Config.CONFIG_KEY_HIDE_HOME_WIZARD, "1");
    }

    public void onImportFromSeed(View view) {
        Intent intent = new Intent(WizardHomeActivity.this, LoginActivity.class);
        intent.putExtra("GenerateFragmentType", GenerateFragment.TYPE_SEED);
        startActivity(intent);
        finish();

        Config.write(Config.CONFIG_KEY_HIDE_HOME_WIZARD, "1");
    }

    public void onSkip(View view) {
        startActivity(new Intent(WizardHomeActivity.this, LoginActivity.class));
        finish();

        Config.write(Config.CONFIG_KEY_HIDE_HOME_WIZARD, "1");
    }

    private void showPrivacyPolicy() {
        PrivacyFragment.display(getSupportFragmentManager());
    }
}