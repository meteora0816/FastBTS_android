package com.example.fastbts;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
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

        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 1);

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
                    String connectionType = getNetworkType();
                    List<MyNetworkInfo.CellInfo> cellInfo = getCellInfo();
                    MyNetworkInfo.WifiInfo wifiInfo = getWifiInfo();
                    MyNetworkInfo myNetworkInfo = new MyNetworkInfo(String.valueOf(Build.VERSION.SDK_INT), connectionType, cellInfo, wifiInfo);
                    Gson gson = new Gson();
                    String networkInfoStr = gson.toJson(myNetworkInfo);
                    Log.d("NetworkInfo:", networkInfoStr);

                    double bandwidth = 0;
                    bandwidth = new FastBTS().SpeedTest("1712382", "", "", "", "", "", "", "", "", "", "", "", "", "", "500");
                    Log.d("bandwidth result", String.valueOf(bandwidth));
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

    /*
        This method is deprecated in API level 28 by Android documentation,
        but still work in my phone with API level 30.
    */
    String getNetworkType() {
        ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = connectivityManager.getActiveNetworkInfo();
        if (networkInfo == null || !networkInfo.isAvailable())
            return "NONE";
        int connectionType = networkInfo.getType();
        if (connectionType == ConnectivityManager.TYPE_WIFI)
            return "WIFI";
        if (connectionType == ConnectivityManager.TYPE_MOBILE) {
            int cellType = networkInfo.getSubtype();
            switch (cellType) {
                case TelephonyManager.NETWORK_TYPE_GPRS:
                case TelephonyManager.NETWORK_TYPE_EDGE:
                case TelephonyManager.NETWORK_TYPE_CDMA:
                case TelephonyManager.NETWORK_TYPE_1xRTT:
                case TelephonyManager.NETWORK_TYPE_IDEN:     // api< 8: replace by 11
                case TelephonyManager.NETWORK_TYPE_GSM:      // api<25: replace by 16
                    return "2G";
                case TelephonyManager.NETWORK_TYPE_UMTS:
                case TelephonyManager.NETWORK_TYPE_EVDO_0:
                case TelephonyManager.NETWORK_TYPE_EVDO_A:
                case TelephonyManager.NETWORK_TYPE_HSDPA:
                case TelephonyManager.NETWORK_TYPE_HSUPA:
                case TelephonyManager.NETWORK_TYPE_HSPA:
                case TelephonyManager.NETWORK_TYPE_EVDO_B:   // api< 9: replace by 12
                case TelephonyManager.NETWORK_TYPE_EHRPD:    // api<11: replace by 14
                case TelephonyManager.NETWORK_TYPE_HSPAP:    // api<13: replace by 15
                case TelephonyManager.NETWORK_TYPE_TD_SCDMA: // api<25: replace by 17
                    return "3G";
                case TelephonyManager.NETWORK_TYPE_LTE:      // api<11: replace by 13
                case TelephonyManager.NETWORK_TYPE_IWLAN:    // api<25: replace by 18
                case 19: // LTE_CA
                    return "4G";
                case TelephonyManager.NETWORK_TYPE_NR:       // api<29: replace by 20
                    return "5G";
                default:
                    return "unknown";
            }
        }
        return "unknown";
    }

    MyNetworkInfo.CellInfo.CellIdentityCdma getCellIdentityCdma(CellIdentityCdma cellIdentity) {
        String basestationId = String.valueOf(cellIdentity.getBasestationId());
        String latitude = String.valueOf(cellIdentity.getLatitude());
        String longitude = String.valueOf(cellIdentity.getLongitude());
        String networkId = String.valueOf(cellIdentity.getNetworkId());
        String systemId = String.valueOf(cellIdentity.getSystemId());
        return new MyNetworkInfo.CellInfo.CellIdentityCdma(basestationId, latitude, longitude, networkId, systemId);
    }

    MyNetworkInfo.CellInfo.CellIdentityGsm getCellIdentityGsm(CellIdentityGsm cellIdentity) {
        String arfcn;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
            arfcn = String.valueOf(cellIdentity.getArfcn());
        else arfcn = "Added in API level 24";
        String bsic;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
            bsic = String.valueOf(cellIdentity.getBsic());
        else bsic = "Added in API level 24";
        String cid = String.valueOf(cellIdentity.getCid());
        String lac = String.valueOf(cellIdentity.getLac());
        String mcc, mnc, mobileNetworkOperator;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            mcc = cellIdentity.getMccString();
            mnc = cellIdentity.getMncString();
            mobileNetworkOperator = cellIdentity.getMobileNetworkOperator();
        } else {
            mcc = String.valueOf(cellIdentity.getMcc());
            mnc = String.valueOf(cellIdentity.getMnc());
            mobileNetworkOperator = "Added in API level 28";
        }
        return new MyNetworkInfo.CellInfo.CellIdentityGsm(arfcn, bsic, cid, lac, mcc, mnc, mobileNetworkOperator);
    }

    MyNetworkInfo.CellInfo.CellIdentityLte getCellIdentityLte(CellIdentityLte cellIdentity) {
        String[] bands;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            int[] bands_int = cellIdentity.getBands();
            bands = new String[bands_int.length];
            for (int i = 0; i < bands_int.length; ++i)
                bands[i] = String.valueOf(bands_int[i]);
        } else bands = new String[]{"Added in API level 30"};
        String bandwidth;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
            bandwidth = String.valueOf(cellIdentity.getBandwidth());
        else bandwidth = "Added in API level 28";
        String ci = String.valueOf(cellIdentity.getCi());
        String earfcn;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
            earfcn = String.valueOf(cellIdentity.getEarfcn());
        else earfcn = "Added in API level 24";
        String mcc, mnc, mobileNetworkOperator;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            mcc = cellIdentity.getMccString();
            mnc = cellIdentity.getMncString();
            mobileNetworkOperator = cellIdentity.getMobileNetworkOperator();
        } else {
            mcc = String.valueOf(cellIdentity.getMcc());
            mnc = String.valueOf(cellIdentity.getMnc());
            mobileNetworkOperator = "Added in API level 28";
        }
        String pci = String.valueOf(cellIdentity.getPci());
        String tac = String.valueOf(cellIdentity.getTac());
        return new MyNetworkInfo.CellInfo.CellIdentityLte(bands, bandwidth, ci, earfcn, mcc, mnc, mobileNetworkOperator, pci, tac);
    }

    MyNetworkInfo.CellInfo.CellIdentityWcdma getCellIdentityWcdma(CellIdentityWcdma cellIdentity) {
        String cid = String.valueOf(cellIdentity.getCid());
        String lac = String.valueOf(cellIdentity.getLac());
        String mcc, mnc, mobileNetworkOperator;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            mcc = cellIdentity.getMccString();
            mnc = cellIdentity.getMncString();
            mobileNetworkOperator = cellIdentity.getMobileNetworkOperator();
        } else {
            mcc = String.valueOf(cellIdentity.getMcc());
            mnc = String.valueOf(cellIdentity.getMnc());
            mobileNetworkOperator = "Added in API level 28";
        }
        String psc = String.valueOf(cellIdentity.getPsc());
        String uarfcn;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
            uarfcn = String.valueOf(cellIdentity.getUarfcn());
        else uarfcn = "Added in API level 24";
        return new MyNetworkInfo.CellInfo.CellIdentityWcdma(cid, lac, mcc, mnc, mobileNetworkOperator, psc, uarfcn);
    }

    @RequiresApi(api = Build.VERSION_CODES.P)
    MyNetworkInfo.CellInfo.CellIdentityTdscdma getCellIdentityTdscdma(CellIdentityTdscdma cellIdentity) {
        String cid = String.valueOf(cellIdentity.getCid());
        String cpid = String.valueOf(cellIdentity.getCpid());
        String lac = String.valueOf(cellIdentity.getLac());
        String mcc = cellIdentity.getMccString();
        String mnc = cellIdentity.getMncString();
        String mobileNetworkOperator;
        String uarfcn;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            mobileNetworkOperator = String.valueOf(cellIdentity.getMobileNetworkOperator());
            uarfcn = String.valueOf(cellIdentity.getUarfcn());
        } else mobileNetworkOperator = uarfcn = "Added in API level 29";
        return new MyNetworkInfo.CellInfo.CellIdentityTdscdma(cid, cpid, lac, mcc, mnc, mobileNetworkOperator, uarfcn);
    }

    @RequiresApi(api = Build.VERSION_CODES.Q)
    MyNetworkInfo.CellInfo.CellIdentityNr getCellIdentityNr(CellIdentityNr cellIdentity) {
        String[] bands;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            int[] bands_int = cellIdentity.getBands();
            bands = new String[bands_int.length];
            for (int i = 0; i < bands_int.length; ++i)
                bands[i] = String.valueOf(bands_int[i]);
        } else bands = new String[]{"Added in API level 30"};
        String mcc = cellIdentity.getMccString();
        String mnc = cellIdentity.getMncString();
        String nci = String.valueOf(cellIdentity.getNci());
        String nrarfcn = String.valueOf(cellIdentity.getNrarfcn());
        String pci = String.valueOf(cellIdentity.getPci());
        String tac = String.valueOf(cellIdentity.getTac());
        return new MyNetworkInfo.CellInfo.CellIdentityNr(bands, mcc, mnc, nci, nrarfcn, pci, tac);
    }


    MyNetworkInfo.CellInfo.CellSignalStrengthCdma getCellSignalStrengthCdma(CellSignalStrengthCdma signalStrength) {
        String asuLevel = String.valueOf(signalStrength.getAsuLevel());
        String cdmaDbm = String.valueOf(signalStrength.getCdmaDbm());
        String cdmaEcio = String.valueOf(signalStrength.getCdmaEcio());
        String cdmaLevel = String.valueOf(signalStrength.getCdmaLevel());
        String dbm = String.valueOf(signalStrength.getDbm());
        String evdodbm = String.valueOf(signalStrength.getEvdoDbm());
        String evdoEcio = String.valueOf(signalStrength.getEvdoEcio());
        String evdoLevel = String.valueOf(signalStrength.getEvdoLevel());
        String evdoSnr = String.valueOf(signalStrength.getEvdoSnr());
        String level = String.valueOf(signalStrength.getLevel());
        return new MyNetworkInfo.CellInfo.CellSignalStrengthCdma(asuLevel, cdmaDbm, cdmaEcio, cdmaLevel, dbm, evdodbm, evdoEcio, evdoLevel, evdoSnr, level);
    }

    MyNetworkInfo.CellInfo.CellSignalStrengthGsm getCellSignalStrengthGsm(CellSignalStrengthGsm signalStrength) {
        String asuLevel = String.valueOf(signalStrength.getAsuLevel());
        String bitErrorRate;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            bitErrorRate = String.valueOf(signalStrength.getBitErrorRate());
        else bitErrorRate = "Added in API level 29";
        String dbm = String.valueOf(signalStrength.getDbm());
        String level = String.valueOf(signalStrength.getLevel());
        String rssi;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
            rssi = String.valueOf(signalStrength.getRssi());
        else rssi = "Added in API level 30";
        String timingAdvance;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            timingAdvance = String.valueOf(signalStrength.getTimingAdvance());
        else timingAdvance = "Added in API level 26";
        return new MyNetworkInfo.CellInfo.CellSignalStrengthGsm(asuLevel, bitErrorRate, dbm, level, rssi, timingAdvance);
    }

    MyNetworkInfo.CellInfo.CellSignalStrengthLte getCellSignalStrengthLte(CellSignalStrengthLte signalStrength) {
        String asuLevel = String.valueOf(signalStrength.getAsuLevel());
        String cqi;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            cqi = String.valueOf(signalStrength.getCqi());
        else cqi = "Added in API level 26";
        String cqiTableIndex;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            cqiTableIndex = String.valueOf(signalStrength.getCqiTableIndex());
        else cqiTableIndex = "Added in API level 31";
        String dbm = String.valueOf(signalStrength.getDbm());
        String level = String.valueOf(signalStrength.getLevel());
        String rsrp, rsrq;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            rsrp = String.valueOf(signalStrength.getRsrp());
            rsrq = String.valueOf(signalStrength.getRsrq());
        } else rsrp = rsrq = "Added in API level 26";
        String rssi;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            rssi = String.valueOf(signalStrength.getRssi());
        else rssi = "Added in API level Q";
        String rssnr;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            rssnr = String.valueOf(signalStrength.getRssnr());
        else rssnr = "Added in API level Q";
        String timingAdvance = String.valueOf(signalStrength.getTimingAdvance());
        return new MyNetworkInfo.CellInfo.CellSignalStrengthLte(asuLevel, cqi, cqiTableIndex, dbm, level, rsrp, rsrq, rssi, rssnr, timingAdvance);
    }

    MyNetworkInfo.CellInfo.CellSignalStrengthWcdma getCellSignalStrengthWcdma(CellSignalStrengthWcdma signalStrength) {
        String asuLevel = String.valueOf(signalStrength.getAsuLevel());
        String dbm = String.valueOf(signalStrength.getDbm());
        String ecNo;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
            ecNo = String.valueOf(signalStrength.getEcNo());
        else ecNo = "Added in API level 30";
        String level = String.valueOf(signalStrength.getLevel());
        return new MyNetworkInfo.CellInfo.CellSignalStrengthWcdma(asuLevel, dbm, ecNo, level);
    }

    @RequiresApi(api = Build.VERSION_CODES.Q)
    MyNetworkInfo.CellInfo.CellSignalStrengthTdscdma getCellSignalStrengthTdscdma(CellSignalStrengthTdscdma signalStrength) {
        String asuLevel = String.valueOf(signalStrength.getAsuLevel());
        String dbm = String.valueOf(signalStrength.getDbm());
        String level = String.valueOf(signalStrength.getLevel());
        String rscp = String.valueOf(signalStrength.getRscp());
        return new MyNetworkInfo.CellInfo.CellSignalStrengthTdscdma(asuLevel, dbm, level, rscp);
    }

    @RequiresApi(api = Build.VERSION_CODES.Q)
    MyNetworkInfo.CellInfo.CellSignalStrengthNr getCellSignalStrengthNr(CellSignalStrengthNr signalStrength) {
        String asuLevel = String.valueOf(signalStrength.getAsuLevel());
        List<String> csicqiReport;
        String csicqiTableIndex;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            List<Integer> csicqiReport_int = signalStrength.getCsiCqiReport();
            csicqiReport = new ArrayList<>(csicqiReport_int.size());
            for (int i = 0; i < csicqiReport_int.size(); ++i)
                csicqiReport.set(i, String.valueOf(csicqiReport_int.get(i)));
            csicqiTableIndex = String.valueOf(signalStrength.getCsiCqiTableIndex());
        } else {
            csicqiReport = new ArrayList<>();
            csicqiTableIndex = "Added in API level 31";
        }
        String csiRsrp = String.valueOf(signalStrength.getCsiRsrp());
        String csiRsrq = String.valueOf(signalStrength.getCsiRsrq());
        String csiSinr = String.valueOf(signalStrength.getCsiSinr());
        String dbm = String.valueOf(signalStrength.getDbm());
        String level = String.valueOf(signalStrength.getLevel());
        String ssRsrp = String.valueOf(signalStrength.getSsRsrp());
        String ssRsrq = String.valueOf(signalStrength.getSsRsrq());
        String ssSinr = String.valueOf(signalStrength.getSsSinr());
        return new MyNetworkInfo.CellInfo.CellSignalStrengthNr(asuLevel, csicqiReport, csicqiTableIndex, csiRsrp, csiRsrq, csiSinr, dbm, level, ssRsrp, ssRsrq, ssSinr);
    }

    List<MyNetworkInfo.CellInfo> getCellInfo() {
        TelephonyManager telephonyManager = (TelephonyManager) getSystemService(TELEPHONY_SERVICE);
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Looper.prepare();
            Toast.makeText(this, "No permission: ACCESS_FINE_LOCATION", Toast.LENGTH_SHORT).show();
            Looper.loop();
            return null;
        }
        List<CellInfo> cellInfoList = telephonyManager.getAllCellInfo();
        List<MyNetworkInfo.CellInfo> myCellInfoList = new ArrayList<>();
        for (CellInfo cellInfo : cellInfoList) {
            if (cellInfo.isRegistered()) {
                if (cellInfo instanceof CellInfoCdma) {
                    CellInfoCdma cellInfoCdma = (CellInfoCdma) cellInfo;
                    MyNetworkInfo.CellInfo.CellIdentityCdma cellIdentity = getCellIdentityCdma(cellInfoCdma.getCellIdentity());
                    MyNetworkInfo.CellInfo.CellSignalStrengthCdma cellSignalStrength = getCellSignalStrengthCdma(cellInfoCdma.getCellSignalStrength());
                    myCellInfoList.add(new MyNetworkInfo.CellInfo("CDMA", cellIdentity, cellSignalStrength));
                }
                if (cellInfo instanceof CellInfoGsm) {
                    CellInfoGsm cellInfoGsm = (CellInfoGsm) cellInfo;
                    MyNetworkInfo.CellInfo.CellIdentityGsm cellIdentity = getCellIdentityGsm(cellInfoGsm.getCellIdentity());
                    MyNetworkInfo.CellInfo.CellSignalStrengthGsm cellSignalStrength = getCellSignalStrengthGsm(cellInfoGsm.getCellSignalStrength());
                    myCellInfoList.add(new MyNetworkInfo.CellInfo("GSM", cellIdentity, cellSignalStrength));
                }
                if (cellInfo instanceof CellInfoLte) {
                    CellInfoLte cellInfoLte = (CellInfoLte) cellInfo;
                    MyNetworkInfo.CellInfo.CellIdentityLte cellIdentity = getCellIdentityLte(cellInfoLte.getCellIdentity());
                    MyNetworkInfo.CellInfo.CellSignalStrengthLte cellSignalStrength = getCellSignalStrengthLte(cellInfoLte.getCellSignalStrength());
                    myCellInfoList.add(new MyNetworkInfo.CellInfo("LTE", cellIdentity, cellSignalStrength));
                }
                if (cellInfo instanceof CellInfoWcdma) {
                    CellInfoWcdma cellInfoWcdma = (CellInfoWcdma) cellInfo;
                    MyNetworkInfo.CellInfo.CellIdentityWcdma cellIdentity = getCellIdentityWcdma(cellInfoWcdma.getCellIdentity());
                    MyNetworkInfo.CellInfo.CellSignalStrengthWcdma cellSignalStrength = getCellSignalStrengthWcdma(cellInfoWcdma.getCellSignalStrength());
                    myCellInfoList.add(new MyNetworkInfo.CellInfo("WCDMA", cellIdentity, cellSignalStrength));
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    if (cellInfo instanceof CellInfoTdscdma) {
                        CellInfoTdscdma cellInfoTdscdma = (CellInfoTdscdma) cellInfo;
                        MyNetworkInfo.CellInfo.CellIdentityTdscdma cellIdentity = getCellIdentityTdscdma(cellInfoTdscdma.getCellIdentity());
                        MyNetworkInfo.CellInfo.CellSignalStrengthTdscdma cellSignalStrength = getCellSignalStrengthTdscdma(cellInfoTdscdma.getCellSignalStrength());
                        myCellInfoList.add(new MyNetworkInfo.CellInfo("TDSCDMA", cellIdentity, cellSignalStrength));
                    }

                    if (cellInfo instanceof CellInfoNr) {
                        CellInfoNr cellInfoNr = (CellInfoNr) cellInfo;
                        MyNetworkInfo.CellInfo.CellIdentityNr cellIdentity = getCellIdentityNr((CellIdentityNr) cellInfoNr.getCellIdentity());
                        MyNetworkInfo.CellInfo.CellSignalStrengthNr cellSignalStrength = getCellSignalStrengthNr((CellSignalStrengthNr) cellInfoNr.getCellSignalStrength());
                        myCellInfoList.add(new MyNetworkInfo.CellInfo("NR", cellIdentity, cellSignalStrength));
                    }
                }
            }
        }
        return myCellInfoList;
    }

    MyNetworkInfo.WifiInfo getWifiInfo() {
        WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
        WifiInfo wifiInfo = wifiManager.getConnectionInfo();
        String SSID = wifiInfo.getSSID();
        String BSSID = wifiInfo.getBSSID();
        String rssi = String.valueOf(wifiInfo.getRssi());
        String linkSpeed = String.valueOf(wifiInfo.getLinkSpeed());
        String networkId = String.valueOf(wifiInfo.getNetworkId());
        String frequency = String.valueOf(wifiInfo.getFrequency());

        String passpointFqdn, passpointProviderFriendlyName, rxLinkSpeedMbps, txLinkSpeedMbps;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            passpointFqdn = wifiInfo.getPasspointFqdn();
            passpointProviderFriendlyName = wifiInfo.getPasspointProviderFriendlyName();
            rxLinkSpeedMbps = String.valueOf(wifiInfo.getRxLinkSpeedMbps());
            txLinkSpeedMbps = String.valueOf(wifiInfo.getTxLinkSpeedMbps());
        }
        else {
            passpointFqdn = "Added in API level 29";
            passpointProviderFriendlyName = "Added in API level 29";
            rxLinkSpeedMbps = "Added in API level 29";
            txLinkSpeedMbps = "Added in API level 29";
        }
        String maxSupportedRxLinkSpeedMbps, maxSupportedTxLinkSpeedMbps, wifiStandard;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            maxSupportedRxLinkSpeedMbps = String.valueOf(wifiInfo.getMaxSupportedRxLinkSpeedMbps());
            maxSupportedTxLinkSpeedMbps = String.valueOf(wifiInfo.getMaxSupportedTxLinkSpeedMbps());
            wifiStandard = String.valueOf(wifiInfo.getWifiStandard());
        } else {
            maxSupportedRxLinkSpeedMbps = "Added in API level 30";
            maxSupportedTxLinkSpeedMbps = "Added in API level 30";
            wifiStandard = "Added in API level 30";
        }
        String currentSecurityType, subscriptionId;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            currentSecurityType = String.valueOf(wifiInfo.getCurrentSecurityType());
            subscriptionId = String.valueOf(wifiInfo.getSubscriptionId());
        } else {
            currentSecurityType = "Added in API level 31";
            subscriptionId = "Added in API level 31";
        }
        return new MyNetworkInfo.WifiInfo(SSID, BSSID, rssi, linkSpeed, networkId, frequency,
                passpointFqdn, passpointProviderFriendlyName, rxLinkSpeedMbps, txLinkSpeedMbps,
                maxSupportedRxLinkSpeedMbps, maxSupportedTxLinkSpeedMbps, wifiStandard,
                currentSecurityType, subscriptionId);
    }
}