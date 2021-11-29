package com.example.fastbts;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import 	android.net.wifi.ScanResult;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.telecom.TelecomManager;
import android.telephony.CellIdentity;
import android.telephony.CellIdentityCdma;
import android.telephony.CellIdentityGsm;
import android.telephony.CellIdentityLte;
import android.telephony.CellIdentityNr;
import android.telephony.CellIdentityTdscdma;
import android.telephony.CellIdentityWcdma;
import android.telephony.CellInfo;
import android.telephony.CellInfoCdma;
import android.telephony.CellInfoGsm;
import android.telephony.CellInfoLte;
import android.telephony.CellInfoNr;
import android.telephony.CellInfoTdscdma;
import android.telephony.CellInfoWcdma;
import android.telephony.CellSignalStrength;
import android.telephony.CellSignalStrengthCdma;
import android.telephony.CellSignalStrengthGsm;
import android.telephony.CellSignalStrengthLte;
import android.telephony.CellSignalStrengthNr;
import android.telephony.CellSignalStrengthTdscdma;
import android.telephony.CellSignalStrengthWcdma;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import java.util.Timer;
import java.util.TimerTask;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.google.gson.Gson;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

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
        textView.setText(NDKTools.stringFromJNI());

        Button button = findViewById(R.id.button);
        button.setOnClickListener(this);
        Button button3 = findViewById(R.id.button3);
        button3.setOnClickListener(this);
    }

    @SuppressLint({"SetTextI18n", "NonConstantResourceId"})
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.button:
                new Thread(() -> {
                    double bandwidth = 0;
                    bandwidth = new FastBTS(this).SpeedTest("1712382", "", "", "", "", "", "", "", "", "", "", "", "", "", "500");
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