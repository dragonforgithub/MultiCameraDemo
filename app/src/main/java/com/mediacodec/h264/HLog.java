package com.mediacodec.h264;

import android.util.Log;

import java.util.logging.Handler;

public class HLog {

    private static final boolean DEBUG = true;

    public static void i(String tag, String msg) {
        Log.i(tag, msg);
    }

    public static void d(String tag, String msg) {
        if (DEBUG){
            Log.d(tag, msg);
        }
    }

    public static void e(String tag, String msg, Throwable tr) {
        Log.e(tag, msg, tr);
    }
}
