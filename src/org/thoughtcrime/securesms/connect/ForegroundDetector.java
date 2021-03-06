/*
 * Copyright (C) 2018 Delta Chat contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.thoughtcrime.securesms.connect;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Application;
import android.os.Bundle;

import org.thoughtcrime.securesms.ApplicationContext;


@SuppressLint("NewApi")
public class ForegroundDetector implements Application.ActivityLifecycleCallbacks {

    private int refs = 0;
    private static ForegroundDetector Instance = null;
    ApplicationContext application;

    public static ForegroundDetector getInstance() {
        return Instance;
    }

    public ForegroundDetector(ApplicationContext application) {
        Instance = this;
        this.application = application;
        application.registerActivityLifecycleCallbacks(this);
    }

    public boolean isForeground() {
        return refs > 0;
    }

    public boolean isBackground() {
        return refs == 0;
    }

    @Override
    public void onActivityStarted(Activity activity) {
        refs++;

        //applicationContext.stopService(new Intent(applicationContext, KeepAliveService.class));
        application.dcContext.startThreads(); // we call this without checking getPermanentPush() to have a simple guarantee that push is always active when the app is in foregroud (startIdleThread makes sure the thread is not started twice)
    }


    @Override
    public void onActivityStopped(Activity activity) {
        if( refs <= 0 ) {
            return;
        }

        refs--;
        if (refs == 0) {
            application.dcContext.afterForgroundWakeLock.acquire(60*1000);
        }
    }

    @Override
    public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
    }

    @Override
    public void onActivityResumed(Activity activity) {
    }

    @Override
    public void onActivityPaused(Activity activity) {
        // pause/resume will also be called when the app is partially covered by a dialog
    }

    @Override
    public void onActivitySaveInstanceState(Activity activity, Bundle outState) {
    }

    @Override
    public void onActivityDestroyed(Activity activity) {
    }
}
