package io.scalaproject.vault;

import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.util.Log;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class NetworkActivity extends BaseActivity {

    private static final int NETWORK_CHECK_TIMEOUT = 5; // segundos

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        showProgressDialog(R.string.splashcreen_check_network, 0);

        // Check network connection with timeout
        int connectionState = isNetworkAvailable() ? 1 : 0;

        Intent intent;
        if (connectionState == 1) {
            String hideSetupWizard = Config.read(Config.CONFIG_KEY_HIDE_HOME_WIZARD);
            if (hideSetupWizard.isEmpty()) {
                intent = new Intent(NetworkActivity.this, WizardHomeActivity.class);
                Log.d("NetworkActivity", "Starting WizardHomeActivity");
            } else {
                intent = new Intent(NetworkActivity.this, LoginActivity.class);
                Log.d("NetworkActivity", "Starting LoginActivity");
            }
        } else {
            intent = new Intent(NetworkActivity.this, LoginActivity.class);
            Log.d("NetworkActivity", "Starting LoginActivity due to no connection");
        }

        // Pass the connection state
        intent.putExtra("connection_state", connectionState);
        startActivity(intent);

        dismissProgressDialog();
        finish();
    }

    private boolean isNetworkAvailable() {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Callable<Boolean> networkTask = () -> {
            ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
            return activeNetworkInfo != null && activeNetworkInfo.isConnected();
        };

        // TODO need to improve not work how was i expected
        Future<Boolean> future = executor.submit(networkTask);
        try {
            return future.get(NETWORK_CHECK_TIMEOUT, TimeUnit.SECONDS);
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            Log.d("NetworkActivity", "Network check timeout or error: " + e.getMessage());
            return false;
        } finally {
            executor.shutdown();
        }
    }
}
