package com.mknotes.app.util;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Arrays;

/**
 * Manages master password verification via encrypted verification token,
 * session-based auto-lock with configurable timeout,
 * and in-memory encryption key caching for AES-256-GCM note encryption.
 *
 * Security model:
 * - Derived key is NEVER stored on disk -- only cached in memory during active session.
 * - Password verification uses encrypted token approach (not key hash).
 * - Key material is zero-filled before being discarded to prevent memory leaks.
 * - Background/foreground tracking for session timeout enforcement.
 */
public class SessionManager {

    private static final String PREFS_NAME = "mknotes_security";
    private static final String KEY_SALT = "master_password_salt";
    private static final String KEY_VERIFY_TOKEN = "master_password_verify_token";
    private static final String KEY_IS_SET = "is_master_password_set";
    private static final String KEY_LAST_UNLOCK = "last_unlock_timestamp";
    private static final String KEY_ENCRYPTION_MIGRATED = "encryption_migrated";
    private static final String KEY_BACKGROUND_TIME = "app_background_timestamp";
    private static final String KEY_ITERATIONS = "pbkdf2_iterations";

    /** Session timeout in milliseconds. 5 minutes by default. */
    public static final long SESSION_TIMEOUT_MS = 5L * 60L * 1000L;

    /** Current PBKDF2 iteration count. Stored alongside salt for future upgrades. */
    private static final int CURRENT_ITERATIONS = 15000;

    private SharedPreferences prefs;
    private static SessionManager sInstance;

    /**
     * In-memory cached derived key for AES-256-GCM encryption/decryption.
     * This is NEVER written to disk. Cleared and zero-filled when session expires.
     */
    private byte[] cachedDerivedKey;

    /**
     * Runtime-only flag indicating whether meditation mantra is actively playing.
     * When true, session timeout is temporarily suspended.
     * This flag is NEVER persisted -- it resets to false on app kill/restart.
     * It depends ONLY on real-time playback state, NOT on meditation data existence.
     */
    private boolean isMeditationPlaying = false;

    public static synchronized SessionManager getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new SessionManager(context.getApplicationContext());
        }
        return sInstance;
    }

    private SessionManager(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        cachedDerivedKey = null;
    }

    /**
     * Check if master password has been configured.
     */
    public boolean isPasswordSet() {
        return prefs.getBoolean(KEY_IS_SET, false);
    }

    /**
     * Get the stored salt as hex string.
     */
    public String getSaltHex() {
        return prefs.getString(KEY_SALT, null);
    }

    /**
     * Get the stored encrypted verification token.
     * Used for backup export.
     */
    public String getVerifyToken() {
        return prefs.getString(KEY_VERIFY_TOKEN, null);
    }

    /**
     * Get the stored iteration count. Falls back to current default if not stored.
     */
    public int getStoredIterations() {
        return prefs.getInt(KEY_ITERATIONS, CURRENT_ITERATIONS);
    }

    /**
     * Set the master password for the first time (or overwrite).
     * Generates salt, derives key via PBKDF2WithHmacSHA256, encrypts verification token.
     * Caches the derived key in memory for encryption operations.
     * Does NOT store key hash -- only salt and encrypted token are persisted.
     *
     * @return true on success
     */
    public boolean setMasterPassword(String password) {
        try {
            byte[] salt = CryptoUtils.generateSalt();
            byte[] derivedKey = CryptoUtils.deriveKey(password, salt);
            if (derivedKey == null) {
                return false;
            }

            // Encrypt verification token with derived key
            String verifyToken = CryptoUtils.encryptVerificationToken(derivedKey);
            if (verifyToken == null) {
                zeroFill(derivedKey);
                return false;
            }

            String saltHex = CryptoUtils.bytesToHex(salt);

            prefs.edit()
                    .putString(KEY_SALT, saltHex)
                    .putString(KEY_VERIFY_TOKEN, verifyToken)
                    .putInt(KEY_ITERATIONS, CURRENT_ITERATIONS)
                    .putBoolean(KEY_IS_SET, true)
                    .putLong(KEY_LAST_UNLOCK, System.currentTimeMillis())
                    .commit();

            // Cache derived key in memory for encryption
            cachedDerivedKey = derivedKey;
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Verify a password against the stored master password.
     * Derives key from password + stored salt, then attempts to decrypt
     * the stored verification token. If decryption succeeds and matches
     * the known plaintext, password is correct.
     *
     * On failure: temp key is zero-filled, returns false, NEVER crashes.
     *
     * @return true if the password matches
     */
    public boolean verifyMasterPassword(String password) {
        String saltHex = prefs.getString(KEY_SALT, null);
        String verifyToken = prefs.getString(KEY_VERIFY_TOKEN, null);
        if (saltHex == null || verifyToken == null) {
            return false;
        }
        try {
            byte[] salt = CryptoUtils.hexToBytes(saltHex);
            byte[] tempKey = CryptoUtils.deriveKey(password, salt);
            if (tempKey == null) {
                return false;
            }

            boolean valid = CryptoUtils.verifyKeyWithToken(tempKey, verifyToken);
            if (valid) {
                // Cache derived key in memory on successful verification
                cachedDerivedKey = tempKey;
                return true;
            } else {
                // Wrong password -- zero-fill temp key immediately
                zeroFill(tempKey);
                return false;
            }
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Record that the user has successfully unlocked the app right now.
     */
    public void updateSessionTimestamp() {
        prefs.edit().putLong(KEY_LAST_UNLOCK, System.currentTimeMillis()).apply();
    }

    /**
     * Check if the current session is still valid (within timeout window).
     * If meditation mantra is actively playing, session is always considered valid
     * to prevent lock screen from interrupting an active meditation.
     */
    public boolean isSessionValid() {
        if (isMeditationPlaying) {
            return true;
        }
        long lastUnlock = prefs.getLong(KEY_LAST_UNLOCK, 0);
        long elapsed = System.currentTimeMillis() - lastUnlock;
        return elapsed < SESSION_TIMEOUT_MS;
    }

    /**
     * Force the session to expire immediately.
     * Zero-fills the cached derived key before nulling to prevent memory leaks.
     */
    public void clearSession() {
        prefs.edit()
                .putLong(KEY_LAST_UNLOCK, 0)
                .remove(KEY_BACKGROUND_TIME)
                .apply();
        if (cachedDerivedKey != null) {
            Arrays.fill(cachedDerivedKey, (byte) 0);
            cachedDerivedKey = null;
        }
    }

    /**
     * Get the cached derived key for encryption/decryption.
     * Returns null if session has expired or key was never cached.
     */
    public byte[] getCachedKey() {
        if (!isSessionValid()) {
            if (cachedDerivedKey != null) {
                Arrays.fill(cachedDerivedKey, (byte) 0);
            }
            cachedDerivedKey = null;
            return null;
        }
        return cachedDerivedKey;
    }

    /**
     * Set the cached derived key directly (used during password change re-encryption).
     */
    public void setCachedKey(byte[] key) {
        cachedDerivedKey = key;
    }

    /**
     * Check if the derived key is available in memory.
     */
    public boolean hasKey() {
        return cachedDerivedKey != null;
    }

    /**
     * Check if database has been migrated to encrypted format.
     */
    public boolean isEncryptionMigrated() {
        return prefs.getBoolean(KEY_ENCRYPTION_MIGRATED, false);
    }

    /**
     * Mark the database as migrated to encrypted format.
     */
    public void setEncryptionMigrated(boolean migrated) {
        prefs.edit().putBoolean(KEY_ENCRYPTION_MIGRATED, migrated).commit();
    }

    /**
     * Called when app goes to background.
     * Stores the current timestamp for timeout checking on return.
     */
    public void onAppBackgrounded() {
        prefs.edit().putLong(KEY_BACKGROUND_TIME, System.currentTimeMillis()).commit();
    }

    /**
     * Called when app returns to foreground.
     * If time spent in background exceeds session timeout, clears session.
     * Exception: If meditation mantra is actively playing, skip timeout enforcement
     * to avoid locking the user out during an active meditation.
     * Removes background timestamp immediately after reading.
     */
    public void onAppForegrounded() {
        long bgTime = prefs.getLong(KEY_BACKGROUND_TIME, 0);
        // Remove background timestamp immediately to prevent stale data
        prefs.edit().remove(KEY_BACKGROUND_TIME).apply();
        if (bgTime > 0 && !isMeditationPlaying) {
            long elapsed = System.currentTimeMillis() - bgTime;
            if (elapsed > SESSION_TIMEOUT_MS) {
                clearSession();
            }
        }
    }

    /**
     * Set the meditation playing state.
     * Called ONLY by MeditationPlayerManager when actual audio playback starts/stops.
     * This flag is runtime-only: it does NOT persist across app kills, restarts,
     * or reboots. It depends solely on real-time playback state,
     * NOT on meditation data existence, history, or note category.
     *
     * @param playing true if mantra audio is actively playing, false otherwise
     */
    public void setMeditationPlaying(boolean playing) {
        isMeditationPlaying = playing;
    }

    /**
     * Check if meditation mantra is currently playing.
     * Used to determine whether session timeout should be suspended.
     *
     * @return true only if a mantra is actively playing right now
     */
    public boolean isMeditationPlaying() {
        return isMeditationPlaying;
    }

    /**
     * Restore encryption credentials from a backup.
     * Stores the salt and verification token so the user can enter their
     * original master password to derive the same key and decrypt notes.
     *
     * Does NOT cache any key -- user must enter password to derive key.
     *
     * @param saltHex     hex-encoded salt from backup
     * @param verifyToken encrypted verification token from backup
     */
    public void restoreFromBackup(String saltHex, String verifyToken) {
        prefs.edit()
                .putString(KEY_SALT, saltHex)
                .putString(KEY_VERIFY_TOKEN, verifyToken)
                .putBoolean(KEY_IS_SET, true)
                .putBoolean(KEY_ENCRYPTION_MIGRATED, true)
                .commit();
    }

    /**
     * Change master password. Verifies old password first.
     * Does NOT re-encrypt notes -- caller must handle that.
     * Returns a CLONE of the OLD derived key for re-encryption purposes.
     * Caller MUST zero-fill the returned old key after re-encryption completes.
     *
     * @return cloned old derived key bytes if successful, null if old password wrong
     */
    public byte[] changeMasterPasswordGetOldKey(String oldPassword, String newPassword) {
        if (!verifyMasterPassword(oldPassword)) {
            return null;
        }
        // At this point cachedDerivedKey is the OLD key (set by verifyMasterPassword)
        byte[] oldKey = new byte[cachedDerivedKey.length];
        System.arraycopy(cachedDerivedKey, 0, oldKey, 0, cachedDerivedKey.length);

        // Set new password (this updates cachedDerivedKey to the NEW key)
        boolean success = setMasterPassword(newPassword);
        if (!success) {
            zeroFill(oldKey);
            return null;
        }
        return oldKey;
    }

    /**
     * Change master password (simple version for backward compat).
     *
     * @return true if old password was correct and new password was set
     */
    public boolean changeMasterPassword(String oldPassword, String newPassword) {
        byte[] oldKey = changeMasterPasswordGetOldKey(oldPassword, newPassword);
        if (oldKey != null) {
            zeroFill(oldKey);
            return true;
        }
        return false;
    }

    /**
     * Zero-fill a byte array to prevent key material from lingering in memory.
     */
    private void zeroFill(byte[] data) {
        if (data != null) {
            Arrays.fill(data, (byte) 0);
        }
    }
}
