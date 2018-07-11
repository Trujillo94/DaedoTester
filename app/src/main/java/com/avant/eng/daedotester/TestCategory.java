package com.avant.eng.daedotester;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.ImageButton;

public class TestCategory
        extends AppCompatActivity {
    ImageButton high;
    ImageButton medium;
    ImageButton low;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.layout_test_category);

        high = findViewById(R.id.high);
        medium = findViewById(R.id.medium);
        low = findViewById(R.id.low);

        high.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(TestCategory.this, TestActivity.class);
                Bundle bundle = new Bundle();
                bundle.putInt("Category_int", R.integer.high);
                bundle.putInt("Regime", R.integer.high);
                bundle.putString("Category_String", "High");
                intent.putExtras(bundle);
                startActivity(intent);
            }
        });

        medium.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(TestCategory.this, TestActivity.class);
                Bundle bundle = new Bundle();
                bundle.putInt("Category_int", R.integer.medium);
                bundle.putInt("Regime", R.integer.medium);
                bundle.putString("Category_String", "Medium");
                intent.putExtras(bundle);
                startActivity(intent);
            }
        });

        low.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(TestCategory.this, TestActivity.class);
                Bundle bundle = new Bundle();
                bundle.putInt("Category_int", R.integer.low);
                bundle.putInt("Regime", R.integer.low);
                bundle.putString("Category_String", "Low");
                intent.putExtras(bundle);
                startActivity(intent);
            }
        });
    }
}
