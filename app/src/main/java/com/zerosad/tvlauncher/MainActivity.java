package com.zerosad.tvlauncher;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.TextView;

public class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        TextView tv = new TextView(this);
        tv.setText("TV Launcher test screen");
        tv.setTextSize(48);
        tv.setTextColor(Color.WHITE);
        tv.setBackgroundColor(Color.parseColor("#0A0A1A"));
        tv.setGravity(Gravity.CENTER);
        setContentView(tv);
    }
}
