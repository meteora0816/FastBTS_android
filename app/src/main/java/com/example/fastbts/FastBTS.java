package com.example.fastbts;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.util.Log;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.NetworkInterface;
import java.net.URL;
import java.util.Enumeration;
import java.util.Locale;

public class FastBTS {
    final static private int interval = 50;
    final static private int CIS_interval = 4; // CIS is executed every {4} intervals
    final static private int window_len = 2000; // ms
    final static private int timeout = 8; // failed after 8s
    final static private int K = 3;
    final static private double threshold = 0.95;

    public static double Test() {
        double bandwidth = -1;
        long downloadTime = 0;
        long byteSum = 0;
        int num = 0;
        int fileSize = 1024000; // Download a 1000M file from server
        String path = "http://118.31.164.30:8080/file";
        path += "/" + fileSize;
        URL url;
        try {
            url = new URL(path);
        } catch (MalformedURLException e) {
            e.printStackTrace();
            return -1;
        }

        long[] timeLine = new long[1000*timeout/interval + 10];
        long[] byteLine = new long[1000*timeout/interval + 10];
        long[] speedLine = new long[1000*timeout/interval + 10];
        try {
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(3000);

            if (conn.getResponseCode() == 200) {
                InputStream inStream = conn.getInputStream();
                byte[] buffer = new byte[10240]; // download 10k every loop
                long startTime = System.currentTimeMillis();
                long lastTime = startTime;
                long lastSize = 0;
                long byteRead = 0;
                double lastUp = 1e18, up;
                double lastDown = 0, down;
                int j = 0, k = 0;
                num = 0;
                while ((byteRead = inStream.read(buffer)) != -1) {
                    byteSum += byteRead;
                    long curTime = System.currentTimeMillis();
                    if (curTime-lastTime>=interval) {
                        timeLine[num] = curTime;
                        byteLine[num] = byteSum;
                        int windowStartPos = Math.max(0, num-(window_len/interval));
                        speedLine[num] = (long) ((double)(byteLine[num]-byteLine[windowStartPos])/(timeLine[num]-timeLine[windowStartPos])*1000);
//                        Log.d("time", String.valueOf(curTime));
//                        Log.d("size", String.valueOf(byteSum-lastSize));
//                        Log.d("speed(2s,byte/second)", String.valueOf(speedLine[i]));
                        j++;
                        if (j >= CIS_interval) {
                            j = 0;
                            double[] crucial_interval = NDKTools.CIS(speedLine, num);
                            up = crucial_interval[1];
                            down = crucial_interval[2];
                            double v = (Math.min(up, lastUp) - Math.max(down, lastDown));
                            double u = (Math.max(up, lastUp) - Math.min(down, lastDown));
                            if (v>0 && u>0) {
                                Log.d("NDK threshold", String.valueOf(v/u));
                                if (v/u>=threshold) {
                                    k++;
                                    if (k >= K) {
                                        Log.d("NDK finished", "finished");
                                        bandwidth = crucial_interval[0];
                                        break;
                                    }
                                } else {
                                    k = 0;
                                }
                            } else {
                                k = 0;
                            }
                            lastUp = up;
                            lastDown = down;
//                            Log.d("NDK mid", String.valueOf(crucial_interval[0]));
//                            Log.d("NDK up", String.valueOf(crucial_interval[1]));
//                            Log.d("NDK down", String.valueOf(crucial_interval[2]));
                        }
                        lastTime = curTime;
                        lastSize = byteSum;
                        num++;
                        if (curTime-timeLine[0]>timeout*1000) {
                            Log.d("NDK timeout", "timeout");
                            break;
                        }
                    }
                }
                long endTime = System.currentTimeMillis();
                inStream.close();
                downloadTime = endTime- startTime;
                Log.d("TimeUse(ms)", String.valueOf(downloadTime));
                Log.d("byteSum", String.valueOf(byteSum));
//                Log.d("bandwidth(kb/s)", String.valueOf(bandwidth/1024));
            }
        } catch (Exception e) {
            e.printStackTrace();
            return -1;
        }
        if (bandwidth<0) {
            return -1;
        } else {
            String bandwidth_Mbps = String.format(Locale.CHINA, "%.2f", bandwidth*8/1024/1024);
            String duration_s = String.format(Locale.CHINA, "%.2f", (double)(downloadTime)/1024);
            String Traffic_MB = String.format(Locale.CHINA, "%.2f", (double)(byteSum)/1024/1024);
            StringBuilder collected_samples_Mbps = new StringBuilder(String.format(Locale.CHINA, "%.2f", (double) speedLine[0] * 8 / 1024 / 1024));
            for (int i=1;i<=num;i++) {
                collected_samples_Mbps.append(String.format(Locale.CHINA, ",%.2f", (double) speedLine[i] * 8 / 1024 / 1024));
            }
            if (!postToDatabase("testip", bandwidth_Mbps, "app", duration_s, Traffic_MB, collected_samples_Mbps.toString())) {
                Log.d("Failed", "post to database.");
            } else {
                Log.d("Succeed", "post to database.");
            }
            return bandwidth;
        }
    }

    private static boolean postToDatabase(String ip, String bandwidth_Mbps, String web_or_app, String duration_s, String traffic_MB, String collected_samples_Mbps) {
        String path = "http://47.104.134.102/fastbts/";
        URL url;
        try {
            url = new URL(path);
        } catch (MalformedURLException e) {
            e.printStackTrace();
            return false;
        }
        try {
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(3000);
            conn.setRequestProperty("content-type", "application/json");
            JSONObject obj = new JSONObject();
            obj.put("ip", ip);
            obj.put("bandwidth_Mbps", bandwidth_Mbps);
            obj.put("web_or_app", web_or_app);
            obj.put("duration_s", duration_s);
            obj.put("traffic_MB", traffic_MB);
            obj.put("collected_samples_Mbps", collected_samples_Mbps);
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
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
}
