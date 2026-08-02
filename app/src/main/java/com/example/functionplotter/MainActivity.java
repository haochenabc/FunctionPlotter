package com.example.functionplotter;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {

    private EditText functionInput;
    private TextView errorText;
    private FunctionPlotView plotView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        functionInput = findViewById(R.id.functionInput);
        errorText = findViewById(R.id.errorText);
        plotView = findViewById(R.id.plotView);
        Button plotButton = findViewById(R.id.plotButton);
        Button btnSin = findViewById(R.id.btnSin);
        Button btnCos = findViewById(R.id.btnCos);
        Button btnX2 = findViewById(R.id.btnX2);
        Button btnX3 = findViewById(R.id.btnX3);
        Button btnExp = findViewById(R.id.btnExp);

        // 默认画 sin(x)
        functionInput.setText("sin(x)");
        plotView.setFunction("sin(x)");

        // 绘制按钮
        plotButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                doPlot();
            }
        });

        // 回车直接绘制
        functionInput.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, android.view.KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_ACTION_GO) {
                    doPlot();
                    return true;
                }
                return false;
            }
        });

        // 快捷函数
        btnSin.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { quickSet("sin(x)"); }
        });
        btnCos.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { quickSet("cos(x)"); }
        });
        btnX2.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { quickSet("x^2"); }
        });
        btnX3.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { quickSet("x^3-x"); }
        });
        btnExp.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { quickSet("2^x"); }
        });
    }

    private void doPlot() {
        String expr = functionInput.getText().toString().trim();
        if (expr.isEmpty()) {
            Toast.makeText(this, "请输入函数表达式", Toast.LENGTH_SHORT).show();
            return;
        }
        errorText.setVisibility(View.GONE);
        plotView.setFunction(expr);
    }

    private void quickSet(String expr) {
        functionInput.setText(expr);
        functionInput.setSelection(expr.length());
        errorText.setVisibility(View.GONE);
        plotView.setFunction(expr);
    }
}
