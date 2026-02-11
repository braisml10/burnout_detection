package com.example.burnout_app;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;

public class ActivityMultitask extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_multitask);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

    }
}

