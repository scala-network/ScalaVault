/*
 * Copyright (c) 2017 m2049r et al.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * ////////////////
 *
 * Copyright (c) 2020 Scala
 *
 * Please see the included LICENSE file for more information.*/

package io.scalaproject.vault;


import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;

import androidx.annotation.NonNull;

import io.scalaproject.vault.model.NetworkType;
import io.scalaproject.vault.util.LocaleHelper;

import org.acra.ACRA;
import org.acra.annotation.AcraCore;
import org.acra.annotation.AcraMailSender;

import timber.log.Timber;

@AcraCore(buildConfigClass = BuildConfig.class)
@AcraMailSender(mailTo = "support@scala.network")
public class ScalaVaultApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        SharedPreferences preferences = getSharedPreferences(getPackageName() + "_preferences", MODE_PRIVATE);
        Config.initialize(preferences);

        /*if (BuildConfig.DEBUG) {
            Timber.plant(new Timber.DebugTree());
        }*/
    }

    @Override
    protected void attachBaseContext(Context context) {
        super.attachBaseContext(LocaleHelper.setLocale(context, LocaleHelper.getLocale(context)));

        ACRA.init(this);

        SharedPreferences preferences = getSharedPreferences(getPackageName() + "_preferences", MODE_PRIVATE);
        Config.initialize(preferences);

        // Enable Debug info by default
        ACRA.getErrorReporter().setEnabled(Config.read(Config.CONFIG_SEND_DEBUG_INFO, "1").equals("1"));
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration configuration) {
        super.onConfigurationChanged(configuration);
        LocaleHelper.updateSystemDefaultLocale(configuration.locale);
        LocaleHelper.setLocale(ScalaVaultApplication.this, LocaleHelper.getLocale(ScalaVaultApplication.this));
    }

    static public NetworkType getNetworkType() {
        return switch (BuildConfig.FLAVOR_net) {
            case "mainnet" -> NetworkType.NetworkType_Mainnet;
            case "stagenet" -> NetworkType.NetworkType_Stagenet;
            case "testnet" -> NetworkType.NetworkType_Testnet;
            default ->
                    throw new IllegalStateException("unknown net flavor " + BuildConfig.FLAVOR_net);
        };
    }
}
