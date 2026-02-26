package com.mknotes.app;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import com.mknotes.app.cloud.FirebaseAuthManager;
import com.mknotes.app.util.PrefsManager;

/**
 * Firebase Email/Password login/register screen.
 * Separate from MasterPassword - this is for cloud sync authentication.
 * Two modes: LOGIN and REGISTER with toggle.
 * No lambdas, no AndroidX.
 */
public class FirebaseLoginActivity extends Activity {

    private static final int MODE_LOGIN = 0;
    private static final int MODE_REGISTER = 1;

    private int currentMode = MODE_LOGIN;

    private EditText etEmail;
    private EditText etPassword;
    private TextView tvError;
    private TextView tvModeTitle;
    private Button btnAction;
    private TextView tvToggleMode;
    private TextView btnSkip;

    private FirebaseAuthManager authManager;

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_firebase_login);

        authManager = FirebaseAuthManager.getInstance(this);

        initViews();
        setupLoginMode();
    }

    private void initViews() {
        etEmail = (EditText) findViewById(R.id.et_firebase_email);
        etPassword = (EditText) findViewById(R.id.et_firebase_password);
        tvError = (TextView) findViewById(R.id.tv_firebase_error);
        tvModeTitle = (TextView) findViewById(R.id.tv_mode_title);
        btnAction = (Button) findViewById(R.id.btn_firebase_action);
        tvToggleMode = (TextView) findViewById(R.id.tv_toggle_mode);
        btnSkip = (TextView) findViewById(R.id.btn_skip);

        btnAction.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                handleAction();
            }
        });

        tvToggleMode.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                toggleMode();
            }
        });

        btnSkip.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                // Disable cloud sync and proceed offline
                PrefsManager.getInstance(FirebaseLoginActivity.this).setCloudSyncEnabled(false);
                launchMain();
            }
        });
    }

    private void setupLoginMode() {
        currentMode = MODE_LOGIN;
        tvModeTitle.setText(R.string.firebase_login_subtitle);
        btnAction.setText(R.string.firebase_btn_login);
        tvToggleMode.setText(R.string.firebase_no_account);
        tvError.setVisibility(View.GONE);
    }

    private void setupRegisterMode() {
        currentMode = MODE_REGISTER;
        tvModeTitle.setText(R.string.firebase_register_subtitle);
        btnAction.setText(R.string.firebase_btn_register);
        tvToggleMode.setText(R.string.firebase_has_account);
        tvError.setVisibility(View.GONE);
    }

    private void toggleMode() {
        if (currentMode == MODE_LOGIN) {
            setupRegisterMode();
        } else {
            setupLoginMode();
        }
    }

    private void handleAction() {
        String email = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString().trim();

        if (email.length() == 0) {
            showError(getString(R.string.firebase_error_email_empty));
            return;
        }
        if (password.length() < 6) {
            showError(getString(R.string.firebase_error_password_short));
            return;
        }

        // Disable button to prevent double-tap
        btnAction.setEnabled(false);
        tvError.setVisibility(View.GONE);

        FirebaseAuthManager.AuthCallback callback = new FirebaseAuthManager.AuthCallback() {
            public void onSuccess() {
                runOnUiThread(new Runnable() {
                    public void run() {
                        PrefsManager.getInstance(FirebaseLoginActivity.this).setCloudSyncEnabled(true);
                        launchMain();
                    }
                });
            }

            public void onFailure(final String errorMessage) {
                runOnUiThread(new Runnable() {
                    public void run() {
                        btnAction.setEnabled(true);
                        showError(errorMessage);
                    }
                });
            }
        };

        if (currentMode == MODE_LOGIN) {
            authManager.login(email, password, callback);
        } else {
            authManager.register(email, password, callback);
        }
    }

    private void showError(String message) {
        tvError.setText(message);
        tvError.setVisibility(View.VISIBLE);
    }

    private void launchMain() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    /**
     * Prevent back press from bypassing - user must login or skip.
     */
    public void onBackPressed() {
        // Treat back as skip - proceed offline
        PrefsManager.getInstance(this).setCloudSyncEnabled(false);
        launchMain();
    }
}
