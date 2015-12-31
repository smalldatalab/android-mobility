package org.ohmage.mobility;

import com.crashlytics.android.Crashlytics;

import io.fabric.sdk.android.Fabric;

/**
 * Created by changun on 12/31/15.
 */
public class Application extends com.orm.SugarApp {

    @Override
    public void onCreate() {
        super.onCreate();
        Fabric.with(this, new Crashlytics());

    }

}
