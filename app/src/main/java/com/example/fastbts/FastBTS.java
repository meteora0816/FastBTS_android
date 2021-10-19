package com.example.fastbts;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Locale;


public class FastBTS {
    static private int DownloadSizeSleep = 50;// ms
    static private int CISSleep = 200;// ms
    static private int TimeWindow = 2000; // ms
    final static private int Timeout = 8000;
    final static private int PingTimeout = 8000;
    static private int TestTimeout = 8000; // failed after 8000ms
    static private int MaxTrafficUse = 200; // MB
    static private int KSimilar = 5;
    static private double Threshold = 0.95;
    final static private String MasterServerIP = "118.31.164.30";
    private FastBTSRecord fastBTSRecord;


    class CISChecker extends Thread {
        ArrayList<Double> speedSample;
        boolean finish;
        Double CISSpeed;
        Object lock;
        CISChecker(ArrayList<Double> speedSample, Object lock){
            this.speedSample = speedSample;
            finish = false;
            this.lock = lock;
        }
        public void run(){
            int similarCnt = 0;
            double lastUp = 0;
            double lastDown = 0;
            long startTime = System.currentTimeMillis();
            while(!finish){
                try {
                    sleep(CISSleep);
                    synchronized (lock){
                        Collections.sort(speedSample);
                    }
                    int n=speedSample.size();
                    if(n<=2){
                        continue;
                    }
                    double up = 0;
                    double down = 0;
                    double k2l = 0;
                    double minInterval = (speedSample.get(n-1)-speedSample.get(0))/(n-1);
                    for(int i=0;i<n;i++){
                        for(int j=i+1;j<n;j++){
                            double k2ltemp = (j-i+1)*(j-i+1)/Math.max(speedSample.get(j)-speedSample.get(i), minInterval);
                            if(k2ltemp>k2l){
                                k2l = k2ltemp;
                                up = speedSample.get(j);
                                down = speedSample.get(i);
                            }
                        }
                    }
                    double res=0;
                    double cnt=0;
                    for(int i=0;i<n;i++){
                        if(speedSample.get(i)>=down && speedSample.get(i)<=up){
                            res+=speedSample.get(i);
                            cnt++;
                        }
                    }
                    CISSpeed = res/cnt;
                    if(isSimilarCIS(up,down,lastUp,lastDown)){
                        similarCnt++;
                        if(similarCnt >= KSimilar){
                            finish = true;
                        }
                    }else{
                        similarCnt=0;
                    }
                    lastDown = down;
                    lastUp = up;
                    if(System.currentTimeMillis() - startTime >= TestTimeout){
                        finish = true;
                    }
                } catch (InterruptedException e) {
//                    e.printStackTrace();
                }
            }
        }
        public boolean isSimilarCIS(double up1, double down1, double up2, double down2){
            double v = (Math.min(up1, up2) - Math.max(down1, down2));
            double u = (Math.max(up1, up2) - Math.min(down1, down2));
            if (v/u >= Threshold)return true;
            return false;
        }
    }

    class DownloadThread extends Thread{
        URL url;
        long size; // byte
        DownloadThread(String url){
            try {
                this.url=new URL(url);
            } catch (MalformedURLException e) {
//                e.printStackTrace();
            }
        }
        public void run(){
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
                        size+=byteRead;
                        if(Thread.interrupted()){
                            inStream.close();
                            conn.disconnect();
                            break;
                        }
                    }
                }
            } catch (Exception e){
//                e.printStackTrace();
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
        InitThread(String ip, String _networkType) {
            this.networkType = _networkType;
            this.masterIp = ip;
            this.ipList = new ArrayList<>();
            this.ipSelected = new ArrayList<>();
            this.selected_server_ips = new StringBuilder();
            this.all_server_ip_latencies_ms = new StringBuilder();
        }
        public void run() {
            try {
                URL url = new URL("http://" + masterIp + ":8080/speedtest/iplist/available");
//                Log.d("url", url.toString());
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(Timeout);
                conn.setReadTimeout(Timeout);
                conn.connect();
                if (conn.getResponseCode()==200) {
                    InputStream inStream = conn.getInputStream();
                    BufferedReader streamReader = new BufferedReader(new InputStreamReader(inStream, StandardCharsets.UTF_8));
                    StringBuilder responseStrBuilder = new StringBuilder();
                    String inputStr;
                    while ((inputStr = streamReader.readLine()) != null)
                        responseStrBuilder.append(inputStr);
                    JSONObject jsonObject = new JSONObject(responseStrBuilder.toString());
                    serverTot = jsonObject.getInt("server_num");
                    JSONArray jsonArray = jsonObject.getJSONArray("ip_list");
                    for (int i=0;i<serverTot;i++) {
                        ipList.add(jsonArray.getString(i));
                    }
                    clientIp = jsonObject.getString("client_ip");
                    conn.disconnect();
//                    Log.d("serverNum", String.valueOf(serverNum));
//                    Log.d("ipList", ipList.toString());
//                    Log.d("clientIp", clientIp);
                    // ping test
                    ArrayList<PingThread> pingThread = new ArrayList<PingThread>();
                    for(String ip: ipList){
                        pingThread.add(new PingThread(ip));
                    }
                    try {
                        for(PingThread t : pingThread){
                            t.start();
                        }
                        for(PingThread t : pingThread){
                            t.join();
                        }
                        if (fastBTSRecord.is_valid=="0") { // Ping failed
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
                    URL url2 = new URL("http://" + masterIp + ":8080/speedtest/info");
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
                        DownloadSizeSleep = jsonObject.getInt("download_size_sleep");
                        TimeWindow = jsonObject.getInt("time_window");
                        KSimilar = jsonObject.getInt("k_similar");
                        MaxTrafficUse = jsonObject.getInt("max_traffic_use");
                        Threshold = jsonObject.getDouble("threshold");
                        double serverSelectCnt = jsonObject.getInt("server_num");
                        jsonArray = jsonObject.getJSONArray("ip_list");
                        for (int i = 0; i< serverSelectCnt; i++) {
                            ipSelected.add(jsonArray.getString(i));
                        }
                        for(String ip : ipSelected) {
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
    class PingThread extends Thread implements Comparable{
        URL url;
        String ip;
        long rtt;
        PingThread(String ip){
            try {
                rtt = PingTimeout;
                this.ip = ip;
                this.url=new URL("http://"+ip+"/testping.html");
            } catch (MalformedURLException e) {
//                e.printStackTrace();
            }
        }
        public void run(){
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
            } catch (Exception e){
                fastBTSRecord.othersAdd("Connect to download server(" + ip + ") failed");
//                e.printStackTrace();
            }
        }

        @Override
        public int compareTo(Object o) {
            if(this.rtt < ((PingThread)o).rtt) return -1;
            if(this.rtt == ((PingThread)o).rtt) return 0;
            return 1;
        }
    }

    public ArrayList<String> getServerIP(String networkType){
        /*
         * 先向服务器要所有可用的ip list，再测试ping，测试结果发送给服务端，服务端发来供使用的下载服务器
         * */
        InitThread initThread = new InitThread(MasterServerIP, networkType);
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

    static class FastBTSRecord{
        String ip;
        String bandwidth_Mbps;
        String web_or_app = "app";
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
        FastBTSRecord(){
            collected_samples_Mbps = new StringBuilder();
            selected_server_ips = new StringBuilder();
            all_server_ip_latencies_ms = new StringBuilder();
        }
        public void othersAdd(String _others) {
            if (others=="") {
                others = _others;
            } else {
                others += ";" + _others;
            }
        }
        public void setDataFromYouSheng(String user_uid, String brand, String model, String os_type, String os_version,
                                        String soft_version, String network_type, String user_isp_id,
                                        String user_region_id, String user_city_id, String user_lat,
                                        String user_lon, String user_as, String user_timezone){
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
        }
        public boolean sendRecord(){
            try {
                JSONObject obj = new JSONObject();
                obj.put("user_uid", user_uid);
                obj.put("ip", ip);
                obj.put("bandwidth_Mbps", bandwidth_Mbps);
                obj.put("web_or_app", web_or_app);
                obj.put("duration_s", duration_s);
                obj.put("traffic_MB", traffic_MB);
                obj.put("collected_samples_Mbps", collected_samples_Mbps);
                obj.put("selected_server_ips",selected_server_ips);
                obj.put("all_server_ip_latencies_ms",all_server_ip_latencies_ms);
                obj.put("brand",brand);
                obj.put("model",model);
                obj.put("os_type",os_type);
                obj.put("os_version",os_version);
                obj.put("soft_version",soft_version);
                obj.put("network_type",network_type);
                obj.put("user_isp_id",user_isp_id);
                obj.put("user_region_id",user_region_id);
                obj.put("user_city_id",user_city_id);
                obj.put("user_lat",user_lat);
                obj.put("user_lon",user_lon);
                obj.put("user_as",user_as);
                obj.put("user_timezone",user_timezone);
                if (others!=null) {
                    obj.put("others",others);
                }
                obj.put("baseline_bandwidth_Mbps",baseline_bandwidth_Mbps);
                obj.put("is_valid", is_valid);

//                Log.d("json = ",obj.toString());

                String path = "http://47.104.134.102/fastbts/";
                URL url = new URL(path);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setConnectTimeout(Timeout);
                conn.setReadTimeout(Timeout);
                conn.setRequestProperty("content-type", "application/json");
                conn.connect();
                OutputStream outStream = conn.getOutputStream();
                outStream.write(obj.toString().getBytes());
                outStream.flush();
                outStream.close();
                // get response
                StringBuilder msg = new StringBuilder();
                if (conn.getResponseCode() == 200) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    String line;
                    while ((line = reader.readLine()) != null) {
                        msg.append(line).append("\n");
                    }
                    reader.close();
                }
//                Log.d("database response", msg.toString());
                conn.disconnect();
            } catch (Exception e) {
//                e.printStackTrace();
                return false;
            }
            return true;
        }
    }

    /*
    * 测速流程：
    * 1.选服务器，并设置几个线程
    * 2.多线程下载，每个线程维护自己的下载量
    * 3.主线程维护总下载量，并记录对应时间点，记入队列；每次队首元素与当前时间相差超过2s，剔除超时数据
    * 4.CIS线程每隔一段时间进行，并控制全局变量指导测速结束
    * */
    public double SpeedTest(String user_uid, String brand, String model, String os_type, String os_version,
                            String soft_version, String network_type, String user_isp_id,
                            String user_region_id, String user_city_id, String user_lat,
                            String user_lon, String user_as, String user_timezone) {
        fastBTSRecord = new FastBTSRecord();
        fastBTSRecord.setDataFromYouSheng(user_uid, brand, model, os_type, os_version,
                                            soft_version, network_type, user_isp_id,
                                            user_region_id, user_city_id, user_lat,
                                            user_lon, user_as, user_timezone);
        // 服务器选择机制
        ArrayList<String> serverIP = getServerIP(network_type);
        if (serverIP.isEmpty()&&fastBTSRecord.is_valid!="0") {
            fastBTSRecord.is_valid = "0";
            fastBTSRecord.othersAdd("Server is null");
        }

        if (fastBTSRecord.is_valid == "0") {
            fastBTSRecord.collected_samples_Mbps.append("0");
            if (fastBTSRecord.ip==null) fastBTSRecord.ip = "0";
            fastBTSRecord.bandwidth_Mbps = "0";
            fastBTSRecord.duration_s = "0";
            fastBTSRecord.traffic_MB = "0";
            fastBTSRecord.sendRecord();
            return 0;
        }

        ArrayList<DownloadThread> downloadThread = new ArrayList<>();
        for(String ip : serverIP) {
            downloadThread.add( new DownloadThread("http://"+ip+"/datafile?"+Math.floor(Math.random()*100000)));
        }

        ArrayList<Double> speedSample = new ArrayList<Double>();
        Object lock = new Object();
        CISChecker checker = new CISChecker(speedSample, lock);

        long startTime = System.currentTimeMillis();

        for(DownloadThread t : downloadThread){
            t.start();
        }
        checker.start();

        ArrayList<Double> sizeRecord = new ArrayList<>();
        ArrayList<Long> timeRecord = new ArrayList<>();
        int posRecord=0;
        while(true){
            try {
                Thread.sleep(DownloadSizeSleep);
            } catch (InterruptedException e) {
//                e.printStackTrace();
            }
            long downloadSize = 0;
            for(DownloadThread t : downloadThread){
                downloadSize += t.size;
            }
            double downloadSizeMBits = (double)(downloadSize)/1024/1024*8;

            long nowTime = System.currentTimeMillis();

            sizeRecord.add(downloadSizeMBits);
            timeRecord.add(nowTime);

            if(timeRecord.size()>=2){
                synchronized (lock){
                    speedSample.add((downloadSizeMBits - sizeRecord.get(posRecord))*1000.0/(nowTime - timeRecord.get(posRecord)));
                }
                fastBTSRecord.collected_samples_Mbps.append(String.format(Locale.CHINA, "%.4f,",(downloadSizeMBits - sizeRecord.get(posRecord))*1000.0/(nowTime - timeRecord.get(posRecord))));
            }

            while(nowTime - timeRecord.get(posRecord) >= TimeWindow){
                posRecord++;
            }
//            Log.d("speed ", String.valueOf(checker.CISSpeed));
            if (checker.finish) {
                fastBTSRecord.is_valid = "1";
                break;
            } else if (nowTime - startTime >= TestTimeout) {
                fastBTSRecord.is_valid = "0";
                fastBTSRecord.othersAdd("Exceeding the time limit");
                break;
            } else if (downloadSizeMBits/8>=MaxTrafficUse) {
                fastBTSRecord.is_valid = "0";
                fastBTSRecord.othersAdd("Exceeding the traffic limit");
                break;
            }
        }
        for(DownloadThread t : downloadThread){
            t.interrupt();
        }
        // send records to our server

        fastBTSRecord.bandwidth_Mbps = String.format(Locale.CHINA, "%.4f", checker.CISSpeed);
        fastBTSRecord.duration_s = String.format(Locale.CHINA, "%.2f", (double)(System.currentTimeMillis()-startTime)/1000);
        fastBTSRecord.traffic_MB = String.format(Locale.CHINA, "%.4f", (double)(sizeRecord.get(sizeRecord.size()-1))/8);

        fastBTSRecord.sendRecord();

        return checker.CISSpeed;
    }
}
