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
public class LoginActivity extends AppCompatActivity {
    private AuthService authService;
    private TextInputEditText usernameInput;
    private TextInputEditText passwordInput;
    private TextView errorText;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);
        authService = AuthService.getInstance(this);
        if (authService.isLoggedIn()) {
            startMainActivity();
            return;
        }
        usernameInput = findViewById(R.id.usernameInput);
        passwordInput = findViewById(R.id.passwordInput);
        errorText = findViewById(R.id.errorText);
        MaterialButton loginButton = findViewById(R.id.loginButton);
        MaterialButton registerButton = findViewById(R.id.registerButton);
        loginButton.setOnClickListener(v -> handleLogin());
        registerButton.setOnClickListener(v -> startRegisterActivity());
    }
    private void handleLogin() {
        String username = usernameInput.getText() != null ? usernameInput.getText().toString() : "";
        String password = passwordInput.getText() != null ? passwordInput.getText().toString() : "";
        AuthService.AuthResult result = authService.login(username, password);
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
    private void startRegisterActivity() {
        Intent intent = new Intent(this, RegisterActivity.class);
        startActivity(intent);
    }
}