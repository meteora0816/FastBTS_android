package com.example.fastbts;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Looper;
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
import android.telephony.CellSignalStrengthCdma;
import android.telephony.CellSignalStrengthGsm;
import android.telephony.CellSignalStrengthLte;
import android.telephony.CellSignalStrengthNr;
import android.telephony.CellSignalStrengthTdscdma;
import android.telephony.CellSignalStrengthWcdma;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.RequiresApi;

import com.google.gson.Gson;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.SocketException;
import java.net.URL;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;


public class FastBTS {

    Context context;

    final static private int Timeout = 8000;        // connect timeout
    final static private int PingTimeout = 8000;
    final static private String MasterServerIP = "118.31.164.30";
    final static private String databaseIp = "47.104.134.102";
    static private Timer timer;
    private int DownloadSizeSleep = 50;// ms
    private int CISSleep = 200;// ms
    private int GetInfoInterval = 500; // ms
    private int TimeWindow = 2000; // ms
    private int TestTimeout = 8000; // failed after 8000ms
    private int MaxTrafficUse = 200; // MB
    private int KSimilar = 5;
    private double Threshold = 0.95;
    private FastBTSRecord fastBTSRecord;
    private MyNetworkInfo myNetworkInfo;

    static boolean stop;

    static public void Stop() {
        if (timer != null) {
            timer.cancel();
        }
        stop = true;
    }

    public FastBTS(Context context) {
        this.context = context;
    }

    class CISChecker extends Thread {
        ArrayList<Double> speedSample;
        boolean finish;
        Double CISSpeed;
        Object lock;

        CISChecker(ArrayList<Double> speedSample, Object lock) {
            this.speedSample = speedSample;
            this.finish = false;
            this.CISSpeed = 0.0;
            this.lock = lock;
        }

        public void run() {
            int similarCnt = 0;
            double lastUp = 0;
            double lastDown = 0;
            long startTime = System.currentTimeMillis();
            while (!finish) {
                try {
                    sleep(CISSleep);
                    synchronized (lock) {
                        Collections.sort(speedSample);
                    }
                    int bias = 0;
                    while (bias<speedSample.size() && speedSample.get(bias) == 0) bias++;
//                    Log.d("bias:", String.valueOf(bias));
                    int n = speedSample.size() - bias;
                    if (n <= 2) {
                        continue;
                    }
//                    Log.d("speedSample",speedSample.toString());
                    double up = 0;
                    double down = 0;
                    double k2l = 0;
                    double minInterval = (speedSample.get(n - 1 + bias) - speedSample.get(0 + bias)) / (n - 1);
//                    Log.d("minInterval", String.valueOf(minInterval));
                    for (int i = 0; i < n; i++) {
                        for (int j = i + 1; j < n; j++) {
                            double k2ltemp = (j - i + 1) * (j - i + 1) / Math.max((speedSample.get(j + bias) - speedSample.get(i + bias)), minInterval);
                            if (k2ltemp > k2l) {
                                k2l = k2ltemp;
                                up = speedSample.get(j + bias);
                                down = speedSample.get(i + bias);
                            }
                        }
                    }
                    double res = 0;
                    double cnt = 0;
                    for (int i = 0; i < n; i++) {
                        if (speedSample.get(i + bias) >= down && speedSample.get(i + bias) <= up) {
                            res += speedSample.get(i + bias);
                            cnt++;
                        }
                    }
//                    Log.d("up and down", "up: " + up + " down: " + down);
//                    Log.d("CISSpeed", "res:"+res+" cnt:"+cnt+" Speed:"+String.valueOf(res/cnt));
                    CISSpeed = res / cnt;
                    if (isSimilarCIS(up, down, lastUp, lastDown)) {
                        similarCnt++;
                        if (similarCnt >= KSimilar) {
                            finish = true;
                        }
                    } else {
                        similarCnt = 0;
                    }
                    lastDown = down;
                    lastUp = up;
//                    if(System.currentTimeMillis() - startTime >= TestTimeout){
//                        finish = true;
//                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

        public boolean isSimilarCIS(double up1, double down1, double up2, double down2) {
            double v = (Math.min(up1, up2) - Math.max(down1, down2));
            double u = (Math.max(up1, up2) - Math.min(down1, down2));
            if (v / u >= Threshold) return true;
            return false;
        }
    }

    class DownloadThread extends Thread {
        URL url;
        long size; // byte

        DownloadThread(String url) {
            try {
                this.url = new URL(url);
            } catch (MalformedURLException e) {
//                e.printStackTrace();
            }
        }

        public DownloadThread() {

        }

        public void run() {
            try {
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(Timeout);
                conn.setReadTimeout(Timeout);
                if (conn.getResponseCode() == 200) {
                    InputStream inStream = conn.getInputStream();
                    byte[] buffer = new byte[10240]; // download 10k every loop
                    long byteRead = 0;
                    while ((byteRead = inStream.read(buffer)) != -1) {
                        size += byteRead;
                        if (Thread.interrupted()) {
                            inStream.close();
                            conn.disconnect();
                            break;
                        }
                    }
                }
            } catch (Exception e) {
//                e.printStackTrace();
            }
        }
    }

    class UDPDownloadThread extends DownloadThread {
        InetAddress address;
        int port;

        UDPDownloadThread(String ip, int port) {
            super();
            try {
                this.address = InetAddress.getByName(ip);
                this.port = port;
            } catch (IOException e) {
//                e.printStackTrace();
            }
        }

        public void run() {
            byte[] send_data = "1".getBytes();
            DatagramPacket send_packet = new DatagramPacket(send_data, send_data.length, address, port);
            try {
                DatagramSocket socket = new DatagramSocket();
                socket.setSoTimeout(Timeout);
                socket.send(send_packet);

                int BUFFER_SIZE = 1024;
                byte[] receive_buf = new byte[BUFFER_SIZE * 2];
                DatagramPacket receive_packet = new DatagramPacket(receive_buf, receive_buf.length);

                long start_time = System.currentTimeMillis();
                while (true) {
                    socket.receive(receive_packet);
                    String receive_data = new String(receive_packet.getData(), 0, receive_packet.getLength());
                    size += receive_data.length();
                    // NOTICE: if blocked at socket.receive, interrupted will not work.
                    if (Thread.interrupted()) {
                        socket.close();
                        break;
                    }
                }
                long end_time = System.currentTimeMillis();
                Log.d("UDP Test", "Time cost:" + (float) (end_time - start_time) / 1000 + "ms");
            } catch (IOException e) {
//                e.printStackTrace();
                Log.d("UDP Test", "receive_packet blocked.");
            }

        }
    }


    class InitThread extends Thread {
        String masterIp;
        ArrayList<String> ipList;
        ArrayList<String> ipSelected;
        String clientIp;
        int serverTot;
        StringBuilder selected_server_ips;
        StringBuilder all_server_ip_latencies_ms;
        String networkType;
        MyNetworkInfo networkInfo;

        InitThread(String ip, String _networkType, MyNetworkInfo _networkInfo) {
            this.networkType = _networkType;
            this.networkInfo = _networkInfo;
            this.masterIp = ip;
            this.ipList = new ArrayList<>();
            this.ipSelected = new ArrayList<>();
            this.selected_server_ips = new StringBuilder();
            this.all_server_ip_latencies_ms = new StringBuilder();
        }

        public void run() {
            try {
                URL url = new URL("http://" + masterIp + ":8080/speedtest/iplist/available");
                Log.d("url", url.toString());
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(Timeout);
                conn.setReadTimeout(Timeout);
                conn.connect();
                if (conn.getResponseCode() == 200) {
                    InputStream inStream = conn.getInputStream();
                    BufferedReader streamReader = new BufferedReader(new InputStreamReader(inStream, StandardCharsets.UTF_8));
                    StringBuilder responseStrBuilder = new StringBuilder();
                    String inputStr;
                    while ((inputStr = streamReader.readLine()) != null)
                        responseStrBuilder.append(inputStr);
                    JSONObject jsonObject = new JSONObject(responseStrBuilder.toString());
                    serverTot = jsonObject.getInt("server_num");
                    JSONArray jsonArray = jsonObject.getJSONArray("ip_list");
                    for (int i = 0; i < serverTot; i++) {
                        ipList.add(jsonArray.getString(i));
                    }
                    clientIp = jsonObject.getString("client_ip");
                    conn.disconnect();
//                    Log.d("serverNum", String.valueOf(serverNum));
                    Log.d("ipList", ipList.toString());
                    Log.d("clientIp", clientIp);
                    // ping test
                    ArrayList<PingThread> pingThread = new ArrayList<PingThread>();
                    for (String ip : ipList) {
                        pingThread.add(new PingThread(ip));
                    }
                    try {
                        for (PingThread t : pingThread) {
                            t.start();
                        }
                        for (PingThread t : pingThread) {
                            t.join();
                        }
                        if (fastBTSRecord.is_valid == "0") { // Ping failed
                            conn.disconnect();
                            return;
                        }
                    } catch (InterruptedException e) {
//                      e.printStackTrace();
                    }
                    Collections.sort(pingThread);
                    ArrayList<String> serverSortedByRTT = new ArrayList<>();
                    for (PingThread t : pingThread) {
                        serverSortedByRTT.add(t.ip);
                        all_server_ip_latencies_ms.append(t.ip).append(":").append(t.rtt).append(";");
                    }
//                    Log.d("latency", all_server_ip_latencies_ms.toString());
                    JSONObject reqJSON = new JSONObject();
                    reqJSON.put("network_type", networkType);
                    JSONArray arr = new JSONArray(serverSortedByRTT);
                    reqJSON.put("servers_sorted_by_rtt", arr);
                    Gson gson = new Gson();
                    ArrayList<String> cell_info_strs = new ArrayList<>();
                    for (MyNetworkInfo.CellInfo cellInfo : networkInfo.cellInfo) {
                        MyNetworkInfo.CellInfo.CellIdentity cellIdentity = cellInfo.cellIdentity;
                        MyNetworkInfo.CellInfo.CellSignalStrength cellSignalStrength = cellInfo.cellSignalStrength;

                        String cell_identity_str = gson.toJson(cellIdentity);
                        String cell_signal_strength_str = gson.toJson(cellSignalStrength);

                        String cell_info_str = "{" +
                                "\"cell_Type\": " + "\"" + cellInfo.cell_Type + "\"," +
                                cell_identity_str.substring(1, cell_identity_str.length() - 1) + "," +
                                cell_signal_strength_str.substring(1, cell_signal_strength_str.length() - 1) +
                                "}";
                        cell_info_strs.add(cell_info_str);
                    }
                    String cell_info_json = "[";
                    for (int i = 0; i < cell_info_strs.size(); ++i) {
                        String cell_info_str = cell_info_strs.get(i);
                        cell_info_json = cell_info_json + cell_info_str;
                        if (i < cell_info_strs.size() - 1)
                            cell_info_json = cell_info_json + ",";
                    }
                    cell_info_json = cell_info_json + "]";
                    String wifi_info_json = gson.toJson(networkInfo.wifiInfo);
                    reqJSON.put("network_wifi_info", wifi_info_json);
                    reqJSON.put("network_cell_info", cell_info_json);
                    URL url2 = new URL("http://" + masterIp + ":8080/v2/speedtest/info");
                    HttpURLConnection conn2 = (HttpURLConnection) url2.openConnection();
                    conn2.setRequestMethod("POST");
                    conn2.setConnectTimeout(Timeout);
                    conn2.setReadTimeout(Timeout);
                    conn2.setRequestProperty("content-type", "application/json");
                    conn2.connect();
                    OutputStream outStream = conn2.getOutputStream();
                    outStream.write(reqJSON.toString().getBytes());
                    outStream.flush();
                    outStream.close();
                    // get response
                    if (conn2.getResponseCode() == 200) {
                        StringBuilder msg = new StringBuilder();
                        BufferedReader reader = new BufferedReader(new InputStreamReader(conn2.getInputStream()));
                        String line;
                        while ((line = reader.readLine()) != null)
                            msg.append(line);
                        reader.close();
                        conn2.disconnect();
                        jsonObject = new JSONObject(msg.toString());
                        CISSleep = jsonObject.getInt("cis_sleep");
                        TestTimeout = jsonObject.getInt("test_timeout");
                        GetInfoInterval = jsonObject.getInt("get_info_interval");
                        DownloadSizeSleep = jsonObject.getInt("download_size_sleep");
                        TimeWindow = jsonObject.getInt("time_window");
                        KSimilar = jsonObject.getInt("k_similar");
                        MaxTrafficUse = jsonObject.getInt("max_traffic_use");
                        Threshold = jsonObject.getDouble("threshold");
                        double serverSelectCnt = jsonObject.getInt("server_num");
//                        Log.d("GetInfoInterval", String.valueOf(GetInfoInterval));
                        jsonArray = jsonObject.getJSONArray("ip_list");
                        for (int i = 0; i < serverSelectCnt; i++) {
                            ipSelected.add(jsonArray.getString(i));
                        }
                        for (String ip : ipSelected) {
                            selected_server_ips.append(ip).append(",");
                        }
                    } else {
                        fastBTSRecord.is_valid = "0";
                        fastBTSRecord.othersAdd("http://" + masterIp + ":8080/speedtest/info " + "response " + conn2.getResponseCode());
                    }
                    conn2.disconnect();
                } else {
                    conn.disconnect();
                    fastBTSRecord.is_valid = "0";
                    fastBTSRecord.othersAdd("http://" + masterIp + ":8080/speedtest/iplist/available" + "response " + conn.getResponseCode());
                }
            } catch (Exception ignored) {
                fastBTSRecord.is_valid = "0";
                fastBTSRecord.othersAdd("Connect to master server failed");
            }
        }

        public StringBuilder getSelected_server_ips() {
            return selected_server_ips;
        }

        public StringBuilder getAll_server_ip_latencies_ms() {
            return all_server_ip_latencies_ms;
        }

        public ArrayList<String> getIpSelected() {
            return ipSelected;
        }
    }

    class PingThread extends Thread implements Comparable {
        URL url;
        String ip;
        long rtt;

        PingThread(String ip) {
            try {
                rtt = PingTimeout;
                this.ip = ip;
                this.url = new URL("http://" + ip + "/testping.html");
            } catch (MalformedURLException e) {
//                e.printStackTrace();
            }
        }

        public void run() {
            try {
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(PingTimeout);
                conn.setReadTimeout(PingTimeout);
                long nowTime = System.currentTimeMillis();
                conn.connect();
                conn.getResponseCode();
                rtt = System.currentTimeMillis() - nowTime;
                conn.disconnect();
            } catch (Exception e) {
                fastBTSRecord.othersAdd("Connect to download server(" + ip + ") failed");
//                e.printStackTrace();
            }
        }

        @Override
        public int compareTo(Object o) {
            if (this.rtt < ((PingThread) o).rtt) return -1;
            if (this.rtt == ((PingThread) o).rtt) return 0;
            return 1;
        }
    }

    public ArrayList<String> getServerIP(String networkType, MyNetworkInfo networkInfo) {
        /*
         * 先向服务器要所有可用的ip list，再测试ping，测试结果发送给服务端，服务端发来供使用的下载服务器
         * */
        InitThread initThread = new InitThread(MasterServerIP, networkType, networkInfo);
        try {
            initThread.start();
            initThread.join();
        } catch (Exception e) {
            return new ArrayList<>();
        }
        fastBTSRecord.all_server_ip_latencies_ms = initThread.getAll_server_ip_latencies_ms();
        fastBTSRecord.selected_server_ips = initThread.getSelected_server_ips();
        fastBTSRecord.ip = initThread.clientIp;
//        Log.d("ip_selected", initThread.getIpSelected().toString());
        return initThread.getIpSelected();
    }

    class FastBTSRecord {
        String id; // the record id in database
        String ip;
        String bandwidth_Mbps;
//        String web_or_app = "app2";
        String duration_s;
        String traffic_MB;
        StringBuilder collected_samples_Mbps;
        StringBuilder selected_server_ips;
        StringBuilder all_server_ip_latencies_ms;
        String brand;
        String model;
        String os_type;
        String os_version;
        String soft_version;
        String network_type;
        String user_isp_id;
        String user_region_id;
        String user_city_id;
        String user_lat;
        String user_lon;
        String user_as;
        String user_timezone;
        //        String dataset_server_timestamp;
        String others = "";
        String baseline_bandwidth_Mbps;
        String user_uid;
        String is_valid;
        String wifi_info;
        String cell_info;

        FastBTSRecord() {
            collected_samples_Mbps = new StringBuilder();
            selected_server_ips = new StringBuilder();
            all_server_ip_latencies_ms = new StringBuilder();
        }

        public void othersAdd(String _others) {
            if (others == "") {
                others = _others;
            } else {
                others += ";" + _others;
            }
        }

        public void setDataFromYouSheng(String user_uid, String brand, String model, String os_type, String os_version,
                                        String soft_version, String network_type, String user_isp_id,
                                        String user_region_id, String user_city_id, String user_lat,
                                        String user_lon, String user_as, String user_timezone, String baseline_bandwidth_Mbps) {
            this.user_uid = user_uid;
            this.brand = brand;
            this.model = model;
            this.os_type = os_type;
            this.os_version = os_version;
            this.soft_version = soft_version;
            this.network_type = network_type;
            this.user_isp_id = user_isp_id;
            this.user_region_id = user_region_id;
            this.user_city_id = user_city_id;
            this.user_lat = user_lat;
            this.user_lon = user_lon;
            this.user_as = user_as;
            this.user_timezone = user_timezone;
            this.baseline_bandwidth_Mbps = baseline_bandwidth_Mbps;
        }

        public void getIdFromServer() {
            try {
                JSONObject obj = new JSONObject();
                obj.put("web_or_app", "app3");
                obj.put("user_uid", "xxx");
                obj.put("ip", "xxx");
                obj.put("bandwidth_Mbps", "xxx");
                obj.put("duration_s", "xxx");
                obj.put("traffic_MB", "xxx");
                obj.put("collected_samples_Mbps", "xxx");
                obj.put("selected_server_ips", "xxx");
                obj.put("all_server_ip_latencies_ms", "xxx");
                obj.put("brand", "xxx");
                obj.put("model", "xxx");
                obj.put("os_type", "xxx");
                obj.put("os_version", "xxx");
                obj.put("soft_version", "xxx");
                obj.put("network_type", "xxx");
                obj.put("user_isp_id", "xxx");
                obj.put("user_region_id", "xxx");
                obj.put("user_city_id", "xxx");
                obj.put("user_lat", "xxx");
                obj.put("user_lon", "xxx");
                obj.put("user_as", "xxx");
                obj.put("user_timezone", "xxx");
                if (others != null) {
                    obj.put("others", "xxx");
                }
                obj.put("baseline_bandwidth_Mbps", "xxx");
                obj.put("is_valid", "0");

                obj.put("wifi_info", "{\"wifi_SSID\":\"aaa\",\"wifi_BSSID\":\"bbb\", \"wifi_rssi\":\"1;2;3;4;5;\", \"wifi_linkSpeed\":\"100;200;300;400;500\",\"wifi_networkId\":\"1\",\"wifi_hiddenSSID\":\"False\", \"wifi_frequency\":\"100;100;100;100;100;\",\"wifi_passpointFqdn\":\"xxx\",\"wifi_passpointProviderFriendlyName\":\"xxxxx\",\"wifi_rxLinkSpeedMbps\":\"1000;1000;1000;1000;1000;\",\"wifi_txLinkSpeedMbps\":\"1000;1000;1000;1000;1000;\",\"wifi_maxSupportedRxLinkSpeedMbps\":\"10000\",\"wifi_maxSupportedTxLinkSpeedMbps\":\"10000\",\"wifi_wifiStandard\":\"6\",\"wifi_currentSecurityType\":\"xxxxxx\",\"wifi_subscriptionId\":\"aaa\",\"wifi_ScanResultLength\":\"50\",\"wifi_ScanResultInfo\":\"a,b,v,d,f,r;1,2,3,4,5,6;\"}");
                obj.put("cell_info", "[{\"cell_Type\":\"CDMA\", \"cell_basestationId\":\"xxx\", \"cell_latitude\":\"111\"},{\"cell_Type\":\"LTE\", \"cell_asuLevel\":\"asuLevel\"}]");
                String path = "http://47.104.134.102/fastbts/";
                URL url = new URL(path);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setConnectTimeout(Timeout);
                conn.setReadTimeout(Timeout);
                conn.setRequestProperty("content-type", "application/json");
                Log.d("json = ",obj.toString());
                OutputStream outStream = conn.getOutputStream();
                outStream.write(obj.toString().getBytes());
                outStream.flush();
                outStream.close();
                conn.connect();
                // get response
                StringBuilder msg = new StringBuilder();
                if (conn.getResponseCode() == 200) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    String line;
                    while ((line = reader.readLine()) != null) {
                        msg.append(line).append("\n");
                    }

                    Log.d("msg:", String.valueOf(msg));
                    id = msg.toString();
                    reader.close();
                }
                Log.d("conn response", String.valueOf(conn.getResponseCode()));
                Log.d("database response", msg.toString());
                conn.disconnect();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    class sendThread extends Thread {
        String databaseIp;

        sendThread(String ip) {
            this.databaseIp = ip;
        }

        public void run() {
            Gson gson = new Gson();
            ArrayList<String> cell_info_strs = new ArrayList<>();
            for (MyNetworkInfo.CellInfo cellInfo : myNetworkInfo.cellInfo) {
                MyNetworkInfo.CellInfo.CellIdentity cellIdentity = cellInfo.cellIdentity;
                MyNetworkInfo.CellInfo.CellSignalStrength cellSignalStrength = cellInfo.cellSignalStrength;

                String cell_identity_str = gson.toJson(cellIdentity);
                String cell_signal_strength_str = gson.toJson(cellSignalStrength);

                String cell_info_str = "{" +
                        "\"cell_Type\": " + "\"" + cellInfo.cell_Type + "\"," +
                        cell_identity_str.substring(1, cell_identity_str.length() - 1) + "," +
                        cell_signal_strength_str.substring(1, cell_signal_strength_str.length() - 1) +
                        "}";
                cell_info_strs.add(cell_info_str);
            }
            String cell_info_json = "[";
            for (int i = 0; i < cell_info_strs.size(); ++i) {
                String cell_info_str = cell_info_strs.get(i);
                cell_info_json = cell_info_json + cell_info_str;
                if (i < cell_info_strs.size() - 1)
                    cell_info_json = cell_info_json + ",";
            }
            cell_info_json = cell_info_json + "]";
            String wifi_info_json = gson.toJson(myNetworkInfo.wifiInfo);
            fastBTSRecord.cell_info = cell_info_json;
            fastBTSRecord.wifi_info = wifi_info_json;
            Log.d("CellInfo", cell_info_json);
            Log.d("WifiInfo", wifi_info_json);
            try {
                JSONObject obj = new JSONObject();
//                Log.d("id=", fastBTSRecord.id);
                obj.put("id", fastBTSRecord.id);
                obj.put("user_uid", fastBTSRecord.user_uid);
                obj.put("ip", fastBTSRecord.ip);
                obj.put("bandwidth_Mbps", fastBTSRecord.bandwidth_Mbps);
                obj.put("web_or_app", "add2");
                obj.put("duration_s", fastBTSRecord.duration_s);
                obj.put("traffic_MB", fastBTSRecord.traffic_MB);
                obj.put("collected_samples_Mbps", fastBTSRecord.collected_samples_Mbps);
                obj.put("selected_server_ips", fastBTSRecord.selected_server_ips);
                obj.put("all_server_ip_latencies_ms", fastBTSRecord.all_server_ip_latencies_ms);
                obj.put("brand", fastBTSRecord.brand);
                obj.put("model", fastBTSRecord.model);
                obj.put("os_type", fastBTSRecord.os_type);
                obj.put("os_version", fastBTSRecord.os_version);
                obj.put("soft_version", fastBTSRecord.soft_version);
                obj.put("network_type", fastBTSRecord.network_type);
                obj.put("user_isp_id", fastBTSRecord.user_isp_id);
                obj.put("user_region_id", fastBTSRecord.user_region_id);
                obj.put("user_city_id", fastBTSRecord.user_city_id);
                obj.put("user_lat", fastBTSRecord.user_lat);
                obj.put("user_lon", fastBTSRecord.user_lon);
                obj.put("user_as", fastBTSRecord.user_as);
                obj.put("user_timezone", fastBTSRecord.user_timezone);
                if (fastBTSRecord.others != null) {
                    obj.put("others", fastBTSRecord.others);
                }
                obj.put("baseline_bandwidth_Mbps", fastBTSRecord.baseline_bandwidth_Mbps);
                obj.put("is_valid", fastBTSRecord.is_valid);

                obj.put("wifi_info", fastBTSRecord.wifi_info);
                obj.put("cell_info", fastBTSRecord.cell_info);


                String path = "http://"+databaseIp+"/fastbts/";
                URL url = new URL(path);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setConnectTimeout(Timeout);
                conn.setReadTimeout(Timeout);
                conn.setRequestProperty("content-type", "application/json");
                Log.d("json = ",obj.toString());
                Log.d("len(json) = ", String.valueOf(obj.toString().length()));
                OutputStream outStream = conn.getOutputStream();
                outStream.write(obj.toString().getBytes());
                outStream.flush();
                outStream.close();
                conn.connect();
                // get response
                StringBuilder msg = new StringBuilder();
                if (conn.getResponseCode() == 200) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    String line;
                    while ((line = reader.readLine()) != null) {
                        msg.append(line).append("\n");
                    }

//                    Log.d("msg:", String.valueOf(msg));
                    reader.close();
                }
                Log.d("conn response", String.valueOf(conn.getResponseCode()));
                Log.d("database response", msg.toString());
                conn.disconnect();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

        /*
         * 测速流程：
         * 1.选服务器，并设置几个线程
         * 2.多线程下载，每个线程维护自己的下载量
         * 3.主线程维护总下载量，并记录对应时间点，记入队列；每次队首元素与当前时间相差超过2s，剔除超时数据
         * 4.CIS线程每隔一段时间进行，并控制全局变量指导测速结束
         * */
        public double SpeedTest(String user_uid,
                                String brand, String model, String os_type, String os_version,
                                String soft_version, String network_type, String user_isp_id,
                                String user_region_id, String user_city_id, String user_lat,
                                String user_lon, String user_as, String user_timezone, String baseline_bandwidth_Mbps) throws InterruptedException {
            network_type = getNetworkType();
            List<MyNetworkInfo.CellInfo> cellInfos = getCellInfo();
            MyNetworkInfo.WifiInfo wifiInfo = getWifiInfo();
            myNetworkInfo = new MyNetworkInfo(String.valueOf(Build.VERSION.SDK_INT), network_type, cellInfos, wifiInfo);


            stop = false;
            fastBTSRecord = new FastBTSRecord();
            fastBTSRecord.setDataFromYouSheng(user_uid, brand, model, os_type, os_version,
                    soft_version, network_type, user_isp_id,
                    user_region_id, user_city_id, user_lat,
                    user_lon, user_as, user_timezone, baseline_bandwidth_Mbps);
            fastBTSRecord.getIdFromServer();

            /* Set constant timestamp */
            timer = new Timer();
            timer.schedule(new ContinuesUpdateTask(myNetworkInfo), 0, GetInfoInterval);

            // 服务器选择机制
//            ArrayList<String> serverIP = getServerIP(network_type, myNetworkInfo);
            ArrayList<String> serverIP = new ArrayList<String>(Arrays.asList("124.223.35.212", "124.223.41.138")); // Used for UDP

            if (serverIP.isEmpty() && fastBTSRecord.is_valid != "0") {
                fastBTSRecord.is_valid = "0";
                fastBTSRecord.othersAdd("Server is null");
            }

            if (fastBTSRecord.is_valid == "0") {
                timer.cancel();
                fastBTSRecord.collected_samples_Mbps.append("0");
                if (fastBTSRecord.ip == null) fastBTSRecord.ip = "0";
                fastBTSRecord.bandwidth_Mbps = "0";
                fastBTSRecord.duration_s = "0";
                fastBTSRecord.traffic_MB = "0";
                Thread t = new sendThread(databaseIp);
                t.start();
                t.join();
                return 0;
            }

            ArrayList<DownloadThread> downloadThread = new ArrayList<>();
            for (String ip : serverIP) {
//                downloadThread.add(new DownloadThread("http://" + ip + "/datafile?" + Math.floor(Math.random() * 100000)));
                downloadThread.add(new UDPDownloadThread(ip, 9876)); // Used for UDP
            }

            ArrayList<Double> speedSample = new ArrayList<>();
            Object lock = new Object();
            CISChecker checker = new CISChecker(speedSample, lock);

            long startTime = System.currentTimeMillis();

            for (DownloadThread t : downloadThread) {
                t.start();
            }
            checker.start();

            ArrayList<Double> sizeRecord = new ArrayList<>();
            ArrayList<Long> timeRecord = new ArrayList<>();
            int posRecord = 0;
            while (true) {
                try {
                    Thread.sleep(DownloadSizeSleep);
                } catch (InterruptedException e) {
//                e.printStackTrace();
                }
                long downloadSize = 0;
                for (DownloadThread t : downloadThread) {
                    downloadSize += t.size;
                }
                double downloadSizeMBits = (double) (downloadSize) / 1024 / 1024 * 8;

                long nowTime = System.currentTimeMillis();

                sizeRecord.add(downloadSizeMBits);
                timeRecord.add(nowTime);

                if (timeRecord.size() >= 2) {
                    synchronized (lock) {
                        speedSample.add((downloadSizeMBits - sizeRecord.get(posRecord)) * 1000.0 / (nowTime - timeRecord.get(posRecord)));
                    }
                    fastBTSRecord.collected_samples_Mbps.append(String.format(Locale.CHINA, "%.4f,", (downloadSizeMBits - sizeRecord.get(posRecord)) * 1000.0 / (nowTime - timeRecord.get(posRecord))));
                }

                while (nowTime - timeRecord.get(posRecord) >= TimeWindow) {
                    posRecord++;
                }


//                Log.d("time:", String.valueOf(nowTime - startTime));
//                Log.d("speed ", String.valueOf(checker.CISSpeed));
                if (nowTime - startTime >= 2500 && checker.finish) {
                    fastBTSRecord.is_valid = "1";
//                    if (nowTime - startTime >= TestTimeout) {
//                        break;
//                    }
                    break;
                }
                if (nowTime - startTime >= TestTimeout) {
                    fastBTSRecord.othersAdd("Exceeding the time limit");
                    break;
                }
                if (downloadSizeMBits / 8 >= MaxTrafficUse) {
                    fastBTSRecord.is_valid = "0";
                    fastBTSRecord.othersAdd("Exceeding the traffic limit");
                    break;
                }
                if (stop) {
                    fastBTSRecord.is_valid = "0";
                    fastBTSRecord.othersAdd("Another speed test job is running");
                    break;
                }
            }
            for (DownloadThread t : downloadThread) {
                t.interrupt();
            }

            // send records to our server
            fastBTSRecord.bandwidth_Mbps = String.format(Locale.CHINA, "%.4f", checker.CISSpeed);
            fastBTSRecord.duration_s = String.format(Locale.CHINA, "%.2f", (double) (System.currentTimeMillis() - startTime) / 1000);
            fastBTSRecord.traffic_MB = String.format(Locale.CHINA, "%.4f", (double) (sizeRecord.get(sizeRecord.size() - 1)) / 8);

            timer.cancel();
            Thread t = new sendThread(databaseIp);
            t.start();
            t.join();

            Log.d("timecost", fastBTSRecord.duration_s);

            return checker.CISSpeed;
        }


        /*
                This method is deprecated in API level 28 by Android documentation,
                but still work in my phone with API level 30.
            */
        String getNetworkType() {
            ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
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
            TelephonyManager telephonyManager = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                    Log.d("No permission:", "ACCESS_FINE_LOCATION");
                    return new ArrayList<>();
                }
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
            WifiManager wifiManager = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            WifiInfo wifiInfo = wifiManager.getConnectionInfo();
            String SSID = wifiInfo.getSSID();
            String BSSID;
            if (wifiInfo.getBSSID() == null)
                BSSID = "NULL";
            else
                BSSID = wifiInfo.getBSSID();
            String rssi = String.valueOf(wifiInfo.getRssi());
            String linkSpeed = String.valueOf(wifiInfo.getLinkSpeed());
            String networkId = String.valueOf(wifiInfo.getNetworkId());
            String frequency = String.valueOf(wifiInfo.getFrequency());
            String hiddenSSID = String.valueOf(wifiInfo.getHiddenSSID());

            //get nearby ScanResult Info
            List<ScanResult> myScanResults = wifiManager.getScanResults();
            String ScanResultLength = String.valueOf(myScanResults.size());
            // Log.d("###ScanResultLength", ScanResultLength);
            StringBuilder ScanResultInfo = new StringBuilder();
            for (ScanResult oneScanResult : myScanResults) {
                //API Level 1
                String tmpBSSID = oneScanResult.BSSID;
                String tmpSSID = oneScanResult.SSID;
                String tmpFrequency = String.valueOf(oneScanResult.frequency);
                String tmpLevel = String.valueOf(oneScanResult.level);
                //API Level 23
                String tmpChannelWidth, tmpStandard;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                    tmpChannelWidth = String.valueOf(oneScanResult.channelWidth);
                else
                    tmpChannelWidth = "Added in API level 23";
                //API Level 30
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
                    tmpStandard = String.valueOf(oneScanResult.getWifiStandard());
                else
                    tmpStandard = "Added in API level 30";
                ScanResultInfo.append(tmpBSSID).append(",").append(tmpSSID).append(",").append(tmpFrequency).append(",").append(tmpLevel).append(",").append(tmpChannelWidth).append(",").append(tmpStandard).append(";");
            }


            String passpointFqdn, passpointProviderFriendlyName, rxLinkSpeedMbps, txLinkSpeedMbps;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                if (wifiInfo.getPasspointFqdn() == null)
                    passpointFqdn = "NULL";
                else
                    passpointFqdn = wifiInfo.getPasspointFqdn();
                if (wifiInfo.getPasspointProviderFriendlyName() == null)
                    passpointProviderFriendlyName = "NULL";
                else
                    passpointProviderFriendlyName = wifiInfo.getPasspointProviderFriendlyName();
                rxLinkSpeedMbps = String.valueOf(wifiInfo.getRxLinkSpeedMbps());
                txLinkSpeedMbps = String.valueOf(wifiInfo.getTxLinkSpeedMbps());
            } else {
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
                    currentSecurityType, subscriptionId, hiddenSSID, ScanResultLength, ScanResultInfo.toString());
        }

        public class ContinuesUpdateTask extends TimerTask {
            public MyNetworkInfo MyNetworkInfo;

            ContinuesUpdateTask(MyNetworkInfo MyNetworkInfo) {
                this.MyNetworkInfo = MyNetworkInfo;
            }

            public void run() {
                long startTime = System.currentTimeMillis();
                monitorWiFiInfo();
                monitorCellInfo();
                Thread t = new sendThread(databaseIp);
                t.start();
//              t.join();
//                Log.d("info time", String.valueOf(System.currentTimeMillis() - startTime));
            }

            public void monitorWiFiInfo() {
                WifiManager wifiManager = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                WifiInfo wifiInfo = wifiManager.getConnectionInfo();
                String rssi = String.valueOf(wifiInfo.getRssi());
                String linkSpeed = String.valueOf(wifiInfo.getLinkSpeed());
                this.MyNetworkInfo.wifiInfo.wifi_linkSpeed += (";" + linkSpeed);
                this.MyNetworkInfo.wifiInfo.wifi_rssi += (";" + rssi);
                String rxLinkSpeedMbps, txLinkSpeedMbps;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    rxLinkSpeedMbps = String.valueOf(wifiInfo.getRxLinkSpeedMbps());
                    txLinkSpeedMbps = String.valueOf(wifiInfo.getTxLinkSpeedMbps());
                    this.MyNetworkInfo.wifiInfo.wifi_rxLinkSpeedMbps += (";" + rxLinkSpeedMbps);
                    this.MyNetworkInfo.wifiInfo.wifi_txLinkSpeedMbps += (";" + txLinkSpeedMbps);
                } else {
                    rxLinkSpeedMbps = "";
                    txLinkSpeedMbps = "";
                }
            }

            public void monitorCellInfo() {
                TelephonyManager telephonyManager = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    if (context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                        Log.d("No permission:", "ACCESS_FINE_LOCATION");
                        return;
                    }
                }


                List<CellInfo> cellInfoList = telephonyManager.getAllCellInfo();
                for (CellInfo cellInfo : cellInfoList) {
                    if (cellInfo.isRegistered()) {
                        if (cellInfo instanceof CellInfoCdma) {
                            CellInfoCdma cellInfoCdma = (CellInfoCdma) cellInfo;
                            for (MyNetworkInfo.CellInfo tmp : this.MyNetworkInfo.cellInfo) {
                                if (tmp.cell_Type.equals("CDMA")) {
                                    MyNetworkInfo.CellInfo.CellIdentityCdma identity_cdma = (MyNetworkInfo.CellInfo.CellIdentityCdma) tmp.cellIdentity;
                                    MyNetworkInfo.CellInfo.CellSignalStrengthCdma ss_cdma = (MyNetworkInfo.CellInfo.CellSignalStrengthCdma) tmp.cellSignalStrength;
                                    if (identity_cdma.cell_basestationId.equals(String.valueOf(cellInfoCdma.getCellIdentity().getBasestationId()))) {
                                        ss_cdma.cell_cdmaDbm += (";" + String.valueOf(cellInfoCdma.getCellSignalStrength().getCdmaDbm()));
                                        ss_cdma.cell_dbm += (";" + String.valueOf(cellInfoCdma.getCellSignalStrength().getDbm()));
                                        break;
                                    }
                                }
                            }
                        }
                        if (cellInfo instanceof CellInfoGsm) {
                            CellInfoGsm cellInfoGsm = (CellInfoGsm) cellInfo;
                            for (MyNetworkInfo.CellInfo tmp : this.MyNetworkInfo.cellInfo) {
                                if (tmp.cell_Type.equals("GSM")) {
                                    MyNetworkInfo.CellInfo.CellIdentityGsm identity_gsm = (MyNetworkInfo.CellInfo.CellIdentityGsm) tmp.cellIdentity;
                                    MyNetworkInfo.CellInfo.CellSignalStrengthGsm ss_gsm = (MyNetworkInfo.CellInfo.CellSignalStrengthGsm) tmp.cellSignalStrength;
                                    if (identity_gsm.cell_cid.equals(String.valueOf(cellInfoGsm.getCellIdentity().getCid()))) {
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
                                            ss_gsm.cell_rssi += (";" + String.valueOf(cellInfoGsm.getCellSignalStrength().getRssi()));
                                        ss_gsm.cell_dbm += (";" + String.valueOf(cellInfoGsm.getCellSignalStrength().getDbm()));
                                        break;
                                    }
                                }
                            }
                        }
                        if (cellInfo instanceof CellInfoLte) {
                            CellInfoLte cellInfoLte = (CellInfoLte) cellInfo;
                            for (MyNetworkInfo.CellInfo tmp : this.MyNetworkInfo.cellInfo) {
                                if (tmp.cell_Type.equals("LTE")) {
                                    MyNetworkInfo.CellInfo.CellIdentityLte identity_lte = (MyNetworkInfo.CellInfo.CellIdentityLte) tmp.cellIdentity;
                                    MyNetworkInfo.CellInfo.CellSignalStrengthLte ss_lte = (MyNetworkInfo.CellInfo.CellSignalStrengthLte) tmp.cellSignalStrength;
                                    if (identity_lte.cell_ci.equals(String.valueOf(cellInfoLte.getCellIdentity().getCi())) && identity_lte.cell_pci.equals(String.valueOf(cellInfoLte.getCellIdentity().getPci()))) {
                                        ss_lte.cell_dbm += (";" + String.valueOf(cellInfoLte.getCellSignalStrength().getDbm()));
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                            ss_lte.cell_rsrp += ";";
                                            ss_lte.cell_rsrp += String.valueOf(cellInfoLte.getCellSignalStrength().getRsrp());
                                            ss_lte.cell_rsrq += ";";
                                            ss_lte.cell_rsrq += String.valueOf(cellInfoLte.getCellSignalStrength().getRsrq());
                                        }
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                                            ss_lte.cell_rssi += (";" + String.valueOf(cellInfoLte.getCellSignalStrength().getRssi()));
                                        break;
                                    }
                                }
                            }
                        }
                        if (cellInfo instanceof CellInfoWcdma) {
                            CellInfoWcdma cellInfoWcdma = (CellInfoWcdma) cellInfo;
                            for (MyNetworkInfo.CellInfo tmp : this.MyNetworkInfo.cellInfo) {
                                if (tmp.cell_Type.equals("WCDMA")) {
                                    MyNetworkInfo.CellInfo.CellIdentityWcdma identity_wcdma = (MyNetworkInfo.CellInfo.CellIdentityWcdma) tmp.cellIdentity;
                                    MyNetworkInfo.CellInfo.CellSignalStrengthWcdma ss_wcdma = (MyNetworkInfo.CellInfo.CellSignalStrengthWcdma) tmp.cellSignalStrength;
                                    if (identity_wcdma.cell_cid.equals(String.valueOf(cellInfoWcdma.getCellIdentity().getCid()))) {
                                        ss_wcdma.cell_dbm += (";" + String.valueOf(cellInfoWcdma.getCellSignalStrength().getDbm()));
                                        break;
                                    }
                                }
                            }
                        }
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            if (cellInfo instanceof CellInfoTdscdma) {
                                CellInfoTdscdma cellInfoTdscdma = (CellInfoTdscdma) cellInfo;
                                for (MyNetworkInfo.CellInfo tmp : this.MyNetworkInfo.cellInfo) {
                                    if (tmp.cell_Type.equals("TDSCDMA")) {
                                        MyNetworkInfo.CellInfo.CellIdentityTdscdma identity_tdscdma = (MyNetworkInfo.CellInfo.CellIdentityTdscdma) tmp.cellIdentity;
                                        MyNetworkInfo.CellInfo.CellSignalStrengthTdscdma ss_tdscdma = (MyNetworkInfo.CellInfo.CellSignalStrengthTdscdma) tmp.cellSignalStrength;
                                        if (identity_tdscdma.cell_cid.equals(String.valueOf(cellInfoTdscdma.getCellIdentity().getCid()))) {
                                            ss_tdscdma.cell_dbm += (";" + String.valueOf(cellInfoTdscdma.getCellSignalStrength().getDbm()));
                                            ss_tdscdma.cell_rscp += (";" + String.valueOf(cellInfoTdscdma.getCellSignalStrength().getRscp()));
                                            break;
                                        }
                                    }
                                }

                            }
                            if (cellInfo instanceof CellInfoNr) {
                                CellInfoNr cellInfoNr = (CellInfoNr) cellInfo;
                                for (MyNetworkInfo.CellInfo tmp : this.MyNetworkInfo.cellInfo) {
                                    if (tmp.cell_Type.equals("NR")) {
                                        MyNetworkInfo.CellInfo.CellIdentityNr identity_nr = (MyNetworkInfo.CellInfo.CellIdentityNr) tmp.cellIdentity;
                                        MyNetworkInfo.CellInfo.CellSignalStrengthNr ss_nr = (MyNetworkInfo.CellInfo.CellSignalStrengthNr) tmp.cellSignalStrength;
                                        CellIdentityNr cell_identity_nr = (CellIdentityNr) cellInfoNr.getCellIdentity();
                                        CellSignalStrengthNr cell_ss_nr = (CellSignalStrengthNr) cellInfoNr.getCellSignalStrength();
                                        if (identity_nr.cell_nci.equals(String.valueOf((cell_identity_nr.getNci())))) {
                                            ss_nr.cell_dbm += (";" + String.valueOf(cellInfoNr.getCellSignalStrength().getDbm()));
                                            ss_nr.cell_ssRsrp += (";" + String.valueOf(cell_ss_nr.getSsRsrp()));
                                            ss_nr.cell_ssRsrq += (";" + String.valueOf(cell_ss_nr.getSsRsrq()));
                                            ss_nr.cell_ssSinr += (";" + String.valueOf(cell_ss_nr.getSsSinr()));
                                            break;
                                        }
                                    }
                                }

                            }
                        }
                    }
                }
            }
        }
    }