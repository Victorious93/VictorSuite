package com.termux.victor.activity;

import android.os.Bundle;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.termux.victor.util.UUtils;
import com.termux.R;
import com.termux.victor.config.VsLinks;

public class UncaughtExceptionHandlerActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_uncaught_exception_handler);
        String error = getIntent().getStringExtra("error");
        TextView errorText = findViewById(R.id.error_text);
        errorText.setText(errorText.getText().toString()
            + VsLinks.ISSUES
            + "\nVersion:" + UUtils.getVersionName(UUtils.getContext())
            + "\n\n" + error);
    }
}
