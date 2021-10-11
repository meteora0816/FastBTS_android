package com.example.fastbts;

import android.util.Log;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Locale;


public class FastBTS {
    final static private int DownloadSizeSleep = 50;// ms
    final static private int CISSleep = 200;// ms
    final static private int TimeWindow = 2000; // ms
    final static private int Timeout = 8000; // failed after 8000ms
    final static private int PingTimeout = 8000; // failed after 8000ms
    final static private int KSimilar = 5;
    final static private double Threshold = 0.95;
    final static private double serverSelectCnt = 4;

    class CISChecker extends Thread{
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
                    if(System.currentTimeMillis() - startTime >= Timeout){
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
        long size;
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

    public ArrayList<String> getServerIP(FastBTSRecord fastBTSRecord){
        /*
         * 方式1.先测试所有服务器的latency，然后直接选最小的4个
         * 方式2.先向服务器要ip list，再测所有ping，再发送给服务端，服务端发来可用服务器
         * */
        ArrayList<String> ipSelected = new ArrayList<String>();
        String iplist[] = new String[]{"49.233.50.165","49.233.50.165","49.233.50.165","49.233.50.165"};

        ArrayList<PingThread> pingThread = new ArrayList<PingThread>();

        for(String ip: iplist){
            pingThread.add(new PingThread(ip));
        }
        try {
            for(PingThread t : pingThread){
                t.start();
                t.join();
            }
        } catch (InterruptedException e) {
//            e.printStackTrace();
        }
        Collections.sort(pingThread);
        for(int i=0;i<serverSelectCnt;i++){
            ipSelected.add(pingThread.get(i).ip);
        }
        for (PingThread t : pingThread){
            fastBTSRecord.all_server_ip_latencies_ms.append(t.ip + ":" + t.rtt+";");
        }
        for(String ip : ipSelected){
            fastBTSRecord.selected_server_ips.append(ip+",");
        }
        return ipSelected;
    }
    class FastBTSRecord{
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
//        String others;
        String baseline_bandwidth_Mbps;
        FastBTSRecord(){
            collected_samples_Mbps = new StringBuilder();
            selected_server_ips = new StringBuilder();
            all_server_ip_latencies_ms = new StringBuilder();
        }
        public void setDataFromYouSheng(String ip, String brand, String model, String os_type, String os_version,
                                        String soft_version, String network_type, String user_isp_id,
                                        String user_region_id, String user_city_id, String user_lat,
                                        String user_lon, String user_as, String user_timezone){
            this.ip = ip;
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
//                obj.put("others",others);
                obj.put("baseline_bandwidth_Mbps",baseline_bandwidth_Mbps);

                Log.d("json = ",obj.toString());

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
                conn.disconnect();
                Log.d("database response", msg.toString());
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
    public double SpeedTest(String userIp, String brand, String model, String os_type, String os_version,
                            String soft_version, String network_type, String user_isp_id,
                            String user_region_id, String user_city_id, String user_lat,
                            String user_lon, String user_as, String user_timezone){
        FastBTSRecord fastBTSRecord = new FastBTSRecord();
        fastBTSRecord.setDataFromYouSheng(userIp, brand, model, os_type, os_version,
                                            soft_version, network_type, user_isp_id,
                                            user_region_id, user_city_id, user_lat,
                                            user_lon, user_as, user_timezone);
        // 服务器选择机制
        ArrayList<String> serverIP = getServerIP(fastBTSRecord);
        ArrayList<DownloadThread> downloadThread = new ArrayList<DownloadThread>();
        for(String ip : serverIP){
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

        ArrayList<Long> sizeRecord = new ArrayList<Long>();
        ArrayList<Long> timeRecord = new ArrayList<Long>();
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
            downloadSize = downloadSize/1024/1024;

            long nowTime = System.currentTimeMillis();

            sizeRecord.add(downloadSize);
            timeRecord.add(nowTime);

            if(timeRecord.size()>=2){
                synchronized (lock){
                    speedSample.add((downloadSize - sizeRecord.get(posRecord))*1000.0/(nowTime - timeRecord.get(posRecord)));
                }
                fastBTSRecord.collected_samples_Mbps.append(String.format(Locale.CHINA, "%.4f,",(downloadSize - sizeRecord.get(posRecord))*1000.0/(nowTime - timeRecord.get(posRecord))));
            }

            while(nowTime - timeRecord.get(posRecord) >= TimeWindow){
                posRecord++;
            }
//            Log.d("speed ", String.valueOf(checker.CISSpeed));
            if(checker.finish){
                break;
            }
        }
        for(DownloadThread t : downloadThread){
            t.interrupt();
        }
        // send records to our server

        fastBTSRecord.bandwidth_Mbps = String.format(Locale.CHINA, "%.4f", checker.CISSpeed);
        fastBTSRecord.duration_s = String.format(Locale.CHINA, "%.2f", (double)(System.currentTimeMillis()-startTime)/1000);
        fastBTSRecord.traffic_MB = String.format(Locale.CHINA, "%.4f", (double)(sizeRecord.get(sizeRecord.size()-1)));

        fastBTSRecord.sendRecord();

        return checker.CISSpeed;
    }
}
