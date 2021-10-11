package com.example.fastbts;

import androidx.appcompat.app.AppCompatActivity;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import java.lang.ref.WeakReference;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    static class mHandler extends Handler {
        private final WeakReference<MainActivity> mActivity;
        mHandler(MainActivity activity) {
            mActivity = new WeakReference<>(activity);
        }
        @Override
        public void handleMessage(Message msg)
        {
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
        setContentView(R.layout.activity_main);

        textView = findViewById(R.id.text);
        textView.setText(NDKTools.stringFromJNI());

        Button button = findViewById(R.id.button);
        button.setOnClickListener(this);
    }

    @SuppressLint("SetTextI18n")
    public void onClick(View view) {
        if (view.getId()==R.id.button) {
            TextView textView = findViewById(R.id.text);
            textView.setText("clicked");
            new Thread(() -> {
                double bandwidth = FastBTS.Test()/1024; // kb/s
                Log.d("bandwidth result", String.valueOf(bandwidth*8/1024));
//                handler.sendEmptyMessage(0);
                Message msg = Message.obtain();
                msg.obj = bandwidth + "kb/s";
                handler.sendMessage(msg);
            }).start();
        }
    }

    public void downloadFinish(String result) {
//        System.out.println(result);
        textView.setText(result);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
    }
}