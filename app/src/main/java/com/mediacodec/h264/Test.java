package com.mediacodec.h264;

import android.media.MediaCodec;
import android.util.Log;

import java.io.IOException;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

public class Test {

    public void test(){
        List<String> b;
        List<String> a[] = new List[2];
        a[0].add("a");
        a[1].add("b");

        int[] c = new int[3];

        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                Log.d("test", "run: ");
            }
        });
        thread.start();
    }
}
