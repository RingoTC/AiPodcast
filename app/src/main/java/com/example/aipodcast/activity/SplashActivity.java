package com.example.aipodcast.activity;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.aipodcast.MainActivity;
import com.example.aipodcast.R;
import com.google.android.material.card.MaterialCardView;

public class SplashActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Hide status bar for a more immersive splash screen
        View decorView = getWindow().getDecorView();
        int uiOptions = View.SYSTEM_UI_FLAG_FULLSCREEN;
        decorView.setSystemUiVisibility(uiOptions);

        setContentView(R.layout.activity_splash);

        // Find views to animate
        MaterialCardView logoContainer = findViewById(R.id.splash_logo_container);
        TextView appName = findViewById(R.id.app_name_text);
        TextView tagline = findViewById(R.id.app_tagline);

        // Simple delay then navigate
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            Intent intent = new Intent(SplashActivity.this, MainActivity.class);
            startActivity(intent);
            finish();
        }, 2000);
    }
}