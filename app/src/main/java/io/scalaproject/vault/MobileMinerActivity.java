// Copyright (c) 2021 Scala
//
// Please see the included LICENSE file for more information.

package io.scalaproject.vault;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;

public class MobileMinerActivity extends BaseActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if ((getIntent().getFlags() & Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT) != 0) {
            // Activity was brought to front and not created,
            // Thus finishing this will get us to the last viewed activity
            finish();
            return;
        }

        setContentView(R.layout.fragment_mobileminer);
    }

    public void onCloseVault(View view) {
        super.onBackPressed();
    }

    public void onDownloadMM(View view) {
        Uri uri = Uri.parse(getResources().getString(R.string.mm_url));
        Intent intent = new Intent(Intent.ACTION_VIEW, uri);
        startActivity(intent);
    }
}