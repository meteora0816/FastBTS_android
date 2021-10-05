package com.example.fastbts;

public class NDKTools {
    public static native String stringFromJNI();
    public static native double[] CIS(long[] speedList, int size);
    static {
        System.loadLibrary("fastbts");
    }
}
