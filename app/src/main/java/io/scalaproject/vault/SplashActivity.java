package io.scalaproject.vault;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.TextView;

@SuppressLint("CustomSplashScreen")
public class SplashActivity extends Activity {
    private TextView txtView;
    private final Handler handler = new Handler();
    private String baseText = "Loading";
    private final String[] dots = {"", ".", "..", "..."};
    private int currentDot = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        String configversion = Config.read(Config.CONFIG_KEY_CONFIG_VERSION);
        if(!configversion.equals(Config.version)) {
            Config.clear();
            Config.write(Config.CONFIG_KEY_CONFIG_VERSION, Config.version);
        }

        if ((getIntent().getFlags() & Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT) != 0) {
            // Activity was brought to front and not created,
            // Thus finishing this will get us to the last viewed activity
            finish();
            return;
        }

        setContentView(R.layout.splashscreen);

        txtView = findViewById(R.id.textView);
        txtView.setVisibility(View.VISIBLE);

        // Set base text from TextView (without dots)
        baseText = txtView.getText().toString().replace("...", "");

        // Start the infinite animation
        startAnimation();

        int millisecondsDelay = 2000;

        // Show splash screen for 2 seconds
        new Handler().postDelayed(() -> {
            // Execute NetworkActivity to check network connection
            startActivity(new Intent(SplashActivity.this, NetworkActivity.class));

            // Close splash screen
            finish();
        }, millisecondsDelay);
    }

    private void startAnimation() {
        handler.post(runnable);
    }

    private final Runnable runnable = new Runnable() {
        @SuppressLint("SetTextI18n")
        @Override
        public void run() {
            txtView.setText(baseText + dots[currentDot]);
            currentDot = (currentDot + 1) % dots.length;
            handler.postDelayed(this, 500); // Change dots every 500ms
        }
    };
}
