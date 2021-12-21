package com.example.fastbts;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.lang.ref.WeakReference;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    static class mHandler extends Handler {
        private final WeakReference<MainActivity> mActivity;

        mHandler(MainActivity activity) {
            mActivity = new WeakReference<>(activity);
        }

        @Override
        public void handleMessage(Message msg) {
            MainActivity activity = mActivity.get();
            if (activity != null) {
                activity.downloadFinish((String) msg.obj);
            }
        }
    }

    private final mHandler handler = new mHandler(this);
    TextView textView;

    @SuppressLint("SetTextI18n")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
                requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 1);
        }

        setContentView(R.layout.activity_main);

        textView = findViewById(R.id.text);

        Button button = findViewById(R.id.button);
        button.setOnClickListener(this);
        Button button3 = findViewById(R.id.button3);
        button3.setOnClickListener(this);
    }

    @SuppressLint({"SetTextI18n", "NonConstantResourceId"})
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.button:
                FastBTS.Stop();
                new Thread(() -> {
                    double bandwidth = 0;

                    try {
                        bandwidth = new FastBTS(this).SpeedTest("1712382", "", "", "", "", "", "", "", "", "", "", "", "", "", "500");
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
//                    Log.d("bandwidth result", String.valueOf(bandwidth));
                    Message msg = Message.obtain();
                    msg.obj = bandwidth + "Mbps";
                    handler.sendMessage(msg);
                }).start();
                break;
            case R.id.button3:
                FastBTS.Stop();
                break;
        }
    }

    public void downloadFinish(String result) {
        System.out.println(result);
        textView.setText(result);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
    }

}