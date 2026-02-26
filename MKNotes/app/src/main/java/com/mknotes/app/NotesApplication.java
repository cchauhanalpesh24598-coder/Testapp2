package com.mknotes.app;

import android.app.Activity;
import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.os.StrictMode;
import androidx.multidex.MultiDex;

import com.google.firebase.FirebaseApp;
import com.mknotes.app.db.NotesRepository;
import com.mknotes.app.util.SessionManager;

public class NotesApplication extends Application {

    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        // minSdk 26 pe native multidex hai, but AIDE ke build
        // system me explicit call safe rehta hai
        MultiDex.install(this);
    }

    public static final String CHANNEL_ID_REMINDER = "notes_reminder_channel";
    public static final String CHANNEL_ID_GENERAL = "notes_general_channel";

    public void onCreate() {
        super.onCreate();

        // Allow file:// URIs to be shared with external apps.
        // Required because this app has no support library / AndroidX
        // and cannot use FileProvider.
        StrictMode.VmPolicy.Builder builder = new StrictMode.VmPolicy.Builder();
        StrictMode.setVmPolicy(builder.build());

        createNotificationChannels();

        // Initialize Firebase
        try {
            FirebaseApp.initializeApp(this);
        } catch (Exception e) {
            // Firebase init failed - app works offline
        }

        // ISSUE 3: Auto-delete trash notes older than 30 days on app startup
        try {
            NotesRepository.getInstance(this).cleanupOldTrash();
        } catch (Exception e) {
            // Fail silently - don't block app startup
        }

        // Register ActivityLifecycleCallbacks for session timeout tracking.
        // Tracks when app goes to background/foreground to enforce session expiry.
        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            private int activityCount = 0;

            public void onActivityStarted(Activity activity) {
                if (activityCount == 0) {
                    // App came to foreground
                    SessionManager.getInstance(activity).onAppForegrounded();
                }
                activityCount++;
            }

            public void onActivityStopped(Activity activity) {
                activityCount--;
                if (activityCount == 0) {
                    // App went to background
                    SessionManager.getInstance(activity).onAppBackgrounded();
                }
            }

            // Required empty implementations (no lambdas)
            public void onActivityCreated(Activity a, Bundle b) {
            }

            public void onActivityResumed(Activity a) {
            }

            public void onActivityPaused(Activity a) {
            }

            public void onActivitySaveInstanceState(Activity a, Bundle b) {
            }

            public void onActivityDestroyed(Activity a) {
            }
        });
    }

    private void createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel reminderChannel = new NotificationChannel(
                    CHANNEL_ID_REMINDER,
                    "Note Reminders",
                    NotificationManager.IMPORTANCE_HIGH
            );
            reminderChannel.setDescription("Notifications for note reminders");
            reminderChannel.enableVibration(true);

            NotificationChannel generalChannel = new NotificationChannel(
                    CHANNEL_ID_GENERAL,
                    "General",
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            generalChannel.setDescription("General notifications");

            NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (manager != null) {
                manager.createNotificationChannel(reminderChannel);
                manager.createNotificationChannel(generalChannel);
            }
        }
    }
}
