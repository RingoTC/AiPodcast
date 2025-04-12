package com.example.aipodcast.activity;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.aipodcast.MainActivity;
import com.example.aipodcast.R;
import com.example.aipodcast.service.AuthService;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

/**
 * Activity handling user registration.
 */
public class RegisterActivity extends AppCompatActivity {
    private AuthService authService;
    private TextInputEditText usernameInput;
    private TextInputEditText emailInput;
    private TextInputEditText passwordInput;
    private TextInputEditText confirmPasswordInput;
    private TextView errorText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        // Initialize AuthService
        authService = AuthService.getInstance(this);

        // Initialize views
        usernameInput = findViewById(R.id.usernameInput);
        emailInput = findViewById(R.id.emailInput);
        passwordInput = findViewById(R.id.passwordInput);
        confirmPasswordInput = findViewById(R.id.confirmPasswordInput);
        errorText = findViewById(R.id.errorText);
        MaterialButton registerButton = findViewById(R.id.registerButton);
        MaterialButton backToLoginButton = findViewById(R.id.backToLoginButton);

        // Set click listeners
        registerButton.setOnClickListener(v -> handleRegistration());
        backToLoginButton.setOnClickListener(v -> finish());
    }

    private void handleRegistration() {
        // Get input values
        String username = usernameInput.getText() != null ? usernameInput.getText().toString() : "";
        String email = emailInput.getText() != null ? emailInput.getText().toString() : "";
        String password = passwordInput.getText() != null ? passwordInput.getText().toString() : "";
        String confirmPassword = confirmPasswordInput.getText() != null ? 
                                confirmPasswordInput.getText().toString() : "";

        // Validate password confirmation
        if (!password.equals(confirmPassword)) {
            showError("Passwords do not match");
            return;
        }

        // Attempt registration
        AuthService.AuthResult result = authService.register(username, password, email);

        if (result.isSuccess()) {
            startMainActivity();
        } else {
            showError(result.getMessage());
        }
    }

    private void showError(String message) {
        errorText.setText(message);
        errorText.setVisibility(View.VISIBLE);
    }

    private void startMainActivity() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
