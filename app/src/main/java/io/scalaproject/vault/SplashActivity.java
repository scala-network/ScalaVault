// Copyright (c) 2020, Scala
//
// Please see the included LICENSE file for more information.

package io.scalaproject.vault;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;

@SuppressLint("CustomSplashScreen")
public class SplashActivity extends Activity {
    @Override
    public void onCreate(Bundle savedInstanceState) {

        String configversion = Config.read(Config.CONFIG_KEY_CONFIG_VERSION);
        if(!configversion.equals(Config.version)) {
            Config.clear();
            Config.write(Config.CONFIG_KEY_CONFIG_VERSION, Config.version);
        }

        super.onCreate(savedInstanceState);
        if ((getIntent().getFlags() & Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT) != 0) {
            // Activity was brought to front and not created,
            // Thus finishing this will get us to the last viewed activity
            finish();
            return;
        }

        setContentView(R.layout.splashscreen);

        int millisecondsDelay = 2000;
        new Handler().postDelayed(() -> {
            String hide_setup_wizard = Config.read(Config.CONFIG_KEY_HIDE_HOME_WIZARD);

            if (hide_setup_wizard.isEmpty()) {
                startActivity(new Intent(SplashActivity.this, WizardHomeActivity.class));
            } else {
                startActivity(new Intent(SplashActivity.this, LoginActivity.class));
            }

            finish();
        }, millisecondsDelay);
    }
}