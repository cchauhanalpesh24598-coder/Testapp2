package com.mknotes.app.cloud;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

/**
 * Singleton wrapper around Firebase Authentication.
 * Handles register, login, logout, UID storage.
 * No lambdas, pure Java, no AndroidX.
 */
public class FirebaseAuthManager {

    private static final String TAG = "FirebaseAuth";
    private static final String PREFS_NAME = "mknotes_firebase";
    private static final String KEY_UID = "firebase_uid";
    private static final String KEY_EMAIL = "firebase_email";

    private static FirebaseAuthManager sInstance;
    private FirebaseAuth firebaseAuth;
    private SharedPreferences prefs;

    public static synchronized FirebaseAuthManager getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new FirebaseAuthManager(context.getApplicationContext());
        }
        return sInstance;
    }

    private FirebaseAuthManager(Context context) {
        firebaseAuth = FirebaseAuth.getInstance();
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    /**
     * Register a new user with email and password.
     */
    public void register(final String email, final String password, final AuthCallback callback) {
        try {
            firebaseAuth.createUserWithEmailAndPassword(email, password)
                    .addOnCompleteListener(new OnCompleteListener<AuthResult>() {
                        public void onComplete(Task<AuthResult> task) {
                            if (task.isSuccessful()) {
                                FirebaseUser user = firebaseAuth.getCurrentUser();
                                if (user != null) {
                                    storeUid(user.getUid());
                                    storeEmail(email);
                                }
                                callback.onSuccess();
                            } else {
                                String msg = "Registration failed";
                                if (task.getException() != null) {
                                    msg = task.getException().getMessage();
                                }
                                Log.e(TAG, "Register failed: " + msg);
                                callback.onFailure(msg);
                            }
                        }
                    });
        } catch (Exception e) {
            Log.e(TAG, "Register exception: " + e.getMessage());
            callback.onFailure(e.getMessage());
        }
    }

    /**
     * Login existing user with email and password.
     */
    public void login(final String email, final String password, final AuthCallback callback) {
        try {
            firebaseAuth.signInWithEmailAndPassword(email, password)
                    .addOnCompleteListener(new OnCompleteListener<AuthResult>() {
                        public void onComplete(Task<AuthResult> task) {
                            if (task.isSuccessful()) {
                                FirebaseUser user = firebaseAuth.getCurrentUser();
                                if (user != null) {
                                    storeUid(user.getUid());
                                    storeEmail(email);
                                }
                                callback.onSuccess();
                            } else {
                                String msg = "Login failed";
                                if (task.getException() != null) {
                                    msg = task.getException().getMessage();
                                }
                                Log.e(TAG, "Login failed: " + msg);
                                callback.onFailure(msg);
                            }
                        }
                    });
        } catch (Exception e) {
            Log.e(TAG, "Login exception: " + e.getMessage());
            callback.onFailure(e.getMessage());
        }
    }

    /**
     * Logout current user and clear stored credentials.
     */
    public void logout() {
        try {
            firebaseAuth.signOut();
        } catch (Exception e) {
            Log.e(TAG, "Logout error: " + e.getMessage());
        }
        prefs.edit()
                .remove(KEY_UID)
                .remove(KEY_EMAIL)
                .apply();
    }

    /**
     * Check if user is currently logged into Firebase.
     */
    public boolean isLoggedIn() {
        return firebaseAuth.getCurrentUser() != null;
    }

    /**
     * Get the current Firebase user.
     */
    public FirebaseUser getCurrentUser() {
        return firebaseAuth.getCurrentUser();
    }

    /**
     * Get the current user's UID.
     * Returns from Firebase first, falls back to stored UID.
     */
    public String getUid() {
        FirebaseUser user = firebaseAuth.getCurrentUser();
        if (user != null) {
            return user.getUid();
        }
        return prefs.getString(KEY_UID, null);
    }

    /**
     * Get stored email address.
     */
    public String getStoredEmail() {
        return prefs.getString(KEY_EMAIL, "");
    }

    /**
     * Store UID locally for quick access.
     */
    private void storeUid(String uid) {
        prefs.edit().putString(KEY_UID, uid).apply();
    }

    /**
     * Store email locally.
     */
    private void storeEmail(String email) {
        prefs.edit().putString(KEY_EMAIL, email).apply();
    }

    /**
     * Callback interface for auth operations.
     */
    public interface AuthCallback {
        void onSuccess();
        void onFailure(String errorMessage);
    }
}
