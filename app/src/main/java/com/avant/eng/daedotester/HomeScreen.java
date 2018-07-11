package com.avant.eng.daedotester;

import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
import android.widget.Toast;

public class HomeScreen extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        ImageButton testButton;
        ImageButton settingsButton;
        ImageButton infoButton;
        super.onCreate(savedInstanceState);
        setContentView(R.layout.layout_home);

        testButton = findViewById(R.id.test);
        settingsButton = findViewById(R.id.settings);
        infoButton = findViewById(R.id.instructions);

        testButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(HomeScreen.this, TestCategory.class);
                startActivity(intent);
            }
        });

        settingsButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(HomeScreen.this, SettingsActivity.class);
                startActivity(intent);
            }
        });

        infoButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(HomeScreen.this, InfoActivity.class);
                startActivity(intent);
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
        String lowRegimePref = sharedPref.getString("low_regime", "");
        checkRegime(lowRegimePref);
        String mediumRegimePref = sharedPref.getString("medium_regime", "");
        checkRegime(mediumRegimePref);
        String highRegimePref = sharedPref.getString("high_regime", "");
        checkRegime(highRegimePref);
    }

    private void checkRegime(String regimePref) {
        boolean error_flag = false;

        if (regimePref.length() == 0) {
            error_flag = true;
        } else {
            if (regimePref.length() > 2 & !regimePref.equalsIgnoreCase("100")) {
                error_flag = true;
            } else if (regimePref.length() < 2) {
                error_flag = true;
            }
        }
        if (error_flag) {
            Toast.makeText(this, R.string.invalid_regime, Toast.LENGTH_LONG).show();
            Intent intent = new Intent(HomeScreen.this, SettingsActivity.class);
            startActivity(intent);
        }
    }
}
