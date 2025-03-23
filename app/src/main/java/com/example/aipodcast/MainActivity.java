package com.example.aipodcast;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import android.content.Intent;
import android.widget.Button;


public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Find buttons
        Button btnTechnology = findViewById(R.id.btnTechnology);
        Button btnSports = findViewById(R.id.btnSports);
        Button btnEntertainment = findViewById(R.id.btnEntertainment);
        Button btnHealth = findViewById(R.id.btnHealth);
        Button btnPolitics = findViewById(R.id.btnPolitics);

        // Set click listeners
        btnTechnology.setOnClickListener(v -> openNewsList("Technology"));
        btnSports.setOnClickListener(v -> openNewsList("Sports"));
        btnEntertainment.setOnClickListener(v -> openNewsList("Entertainment"));
        btnHealth.setOnClickListener(v -> openNewsList("Health"));
        btnPolitics.setOnClickListener(v -> openNewsList("Politics"));
    }

    // Method to open the news list activity
    private void openNewsList(String topic) {
        Intent intent = new Intent(this, NewsListActivity.class);
        intent.putExtra("TOPIC", topic); // Pass the selected topic to the next activity
        startActivity(intent);
    }
}