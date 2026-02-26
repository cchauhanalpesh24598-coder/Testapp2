package com.mknotes.app;

import android.app.Activity;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.mknotes.app.cloud.FirebaseAuthManager;
import com.mknotes.app.db.NotesRepository;
import com.mknotes.app.util.PrefsManager;
import com.mknotes.app.util.SessionManager;

/**
 * Gatekeeper activity that requires master password before allowing app access.
 * Two modes: CREATE (first launch) and UNLOCK (subsequent launches after session expiry).
 * This is the LAUNCHER activity -- all app entry goes through here.
 *
 * On first unlock after encryption feature is added, migrates existing plaintext
 * notes to encrypted format using the derived key.
 */
public class MasterPasswordActivity extends Activity {

    private static final int MODE_CREATE = 0;
    private static final int MODE_UNLOCK = 1;

    private int currentMode;
    private SessionManager sessionManager;

    private TextView toolbarTitle;
    private TextView textSubtitle;
    private EditText editPassword;
    private EditText editConfirmPassword;
    private TextView textError;
    private TextView textStrengthHint;
    private Button btnAction;

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        sessionManager = SessionManager.getInstance(this);

        // If password is set and session is still valid AND key is cached, skip to main
        if (sessionManager.isPasswordSet() && sessionManager.isSessionValid() && sessionManager.hasKey()) {
            sessionManager.updateSessionTimestamp();
            launchMain();
            return;
        }

        setContentView(R.layout.activity_master_password);
        setupStatusBar();
        initViews();

        if (sessionManager.isPasswordSet()) {
            setupUnlockMode();
        } else {
            setupCreateMode();
        }
    }

    private void setupStatusBar() {
        if (Build.VERSION.SDK_INT >= 21) {
            Window window = getWindow();
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            window.setStatusBarColor(getResources().getColor(R.color.colorPrimaryDark));
        }
    }

    private void initViews() {
        toolbarTitle = (TextView) findViewById(R.id.toolbar_title);
        textSubtitle = (TextView) findViewById(R.id.text_subtitle);
        editPassword = (EditText) findViewById(R.id.edit_password);
        editConfirmPassword = (EditText) findViewById(R.id.edit_confirm_password);
        textError = (TextView) findViewById(R.id.text_error);
        textStrengthHint = (TextView) findViewById(R.id.text_strength_hint);
        btnAction = (Button) findViewById(R.id.btn_action);
    }

    private void setupCreateMode() {
        currentMode = MODE_CREATE;
        toolbarTitle.setText(R.string.master_password_title_create);
        textSubtitle.setText(R.string.master_password_subtitle_create);
        editConfirmPassword.setVisibility(View.VISIBLE);
        textStrengthHint.setVisibility(View.VISIBLE);
        btnAction.setText(R.string.master_password_btn_create);
        textError.setVisibility(View.GONE);

        btnAction.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                handleCreate();
            }
        });
    }

    private void setupUnlockMode() {
        currentMode = MODE_UNLOCK;
        toolbarTitle.setText(R.string.master_password_title_unlock);
        textSubtitle.setText(R.string.master_password_subtitle_unlock);
        editConfirmPassword.setVisibility(View.GONE);
        textStrengthHint.setVisibility(View.GONE);
        btnAction.setText(R.string.master_password_btn_unlock);
        textError.setVisibility(View.GONE);

        btnAction.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                handleUnlock();
            }
        });
    }

    private void handleCreate() {
        String password = editPassword.getText().toString();
        String confirm = editConfirmPassword.getText().toString();

        // Validate length
        if (password.length() < 8) {
            showError(getString(R.string.master_password_error_short));
            return;
        }

        // Validate match
        if (!password.equals(confirm)) {
            showError(getString(R.string.master_password_error_mismatch));
            return;
        }

        // Set password (this also caches the derived key in SessionManager)
        boolean success = sessionManager.setMasterPassword(password);
        if (success) {
            // Migrate existing plaintext notes to encrypted format
            migrateExistingNotes();
            Toast.makeText(this, R.string.master_password_set_success, Toast.LENGTH_SHORT).show();
            launchMain();
        } else {
            showError(getString(R.string.master_password_error_generic));
        }
    }

    private void handleUnlock() {
        String password = editPassword.getText().toString();

        if (password.length() == 0) {
            showError(getString(R.string.master_password_error_empty));
            return;
        }

        // verifyMasterPassword caches the derived key on success
        boolean valid = sessionManager.verifyMasterPassword(password);
        if (valid) {
            sessionManager.updateSessionTimestamp();

            // If encryption migration hasn't happened yet, do it now
            if (!sessionManager.isEncryptionMigrated()) {
                migrateExistingNotes();
            }

            launchMain();
        } else {
            showError(getString(R.string.master_password_error_wrong));
            editPassword.setText("");
        }
    }

    /**
     * Migrate all existing plaintext notes to encrypted format.
     * Called once after encryption feature is first activated.
     */
    private void migrateExistingNotes() {
        try {
            byte[] key = sessionManager.getCachedKey();
            if (key == null) {
                return;
            }
            NotesRepository repo = NotesRepository.getInstance(this);
            boolean success = repo.migrateToEncrypted(key);
            if (success) {
                sessionManager.setEncryptionMigrated(true);
            }
            // If !success, transaction rolled back, will retry on next unlock
        } catch (Exception e) {
            // Migration failed -- will retry on next unlock
        }
    }

    private void showError(String message) {
        textError.setText(message);
        textError.setVisibility(View.VISIBLE);
    }

    private void launchMain() {
        // Check if Firebase auth is needed for cloud sync
        PrefsManager prefs = PrefsManager.getInstance(this);
        FirebaseAuthManager authManager = FirebaseAuthManager.getInstance(this);

        // If user has never logged in to Firebase AND cloud sync is not explicitly disabled,
        // show Firebase login screen on first launch after unlock.
        // If user previously skipped or logged in, go straight to MainActivity.
        if (!authManager.isLoggedIn() && !prefs.isCloudSyncEnabled()
                && authManager.getUid() == null) {
            // First time: show Firebase login so user can choose to enable sync or skip
            Intent intent = new Intent(this, FirebaseLoginActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
            return;
        }

        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    /**
     * Prevent back press from bypassing the password screen.
     */
    public void onBackPressed() {
        // Move task to back instead of finishing -- user cannot bypass
        moveTaskToBack(true);
    }
}
