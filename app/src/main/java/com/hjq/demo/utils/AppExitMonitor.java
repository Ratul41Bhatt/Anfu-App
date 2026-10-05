
package com.hjq.demo.utils;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;

public class AppExitMonitor {

    private static int activityCount = 0;
    private static AppExitListener listener;

    public static void init(Application application, AppExitListener exitListener) {
        listener = exitListener;
        application.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
            @Override
            public void onActivityCreated(Activity activity, Bundle savedInstanceState) { }

            @Override
            public void onActivityStarted(Activity activity) {
                if (activityCount == 0 && listener != null) {
                    listener.onAppForeground();
                }
                activityCount++;
            }

            @Override
            public void onActivityResumed(Activity activity) { }

            @Override
            public void onActivityPaused(Activity activity) { }

            @Override
            public void onActivityStopped(Activity activity) {
                activityCount--;
                if (activityCount == 0 && listener != null) {
                    listener.onAppBackground();
                }
            }

            @Override
            public void onActivitySaveInstanceState(Activity activity, Bundle outState) { }

            @Override
            public void onActivityDestroyed(Activity activity) { }
        });
    }

    public static void notifyAppExit() {
        if (listener != null) {
            listener.onAppExit();
        }
    }

    public interface AppExitListener {
        void onAppForeground();
        void onAppBackground();
        void onAppExit();
    }
}
