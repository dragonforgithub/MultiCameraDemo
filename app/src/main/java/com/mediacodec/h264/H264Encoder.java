package com.mediacodec.h264;

import android.annotation.SuppressLint;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecInfo.CodecCapabilities;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.os.Build;
import android.os.Bundle;
import android.os.Trace;
import android.util.Log;

import androidx.annotation.RequiresApi;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;

public class H264Encoder {
    public final String TAG = H264Encoder.class.getSimpleName();

    private MediaCodec mediaCodec;
    private MediaFormat mediaFormat;
    private byte[] m_info = null;
    private int m_colorFormat;
    private boolean m_isNeedSwap = true;
    private int m_frameRate;
    private int m_frame_count;
    private byte[] m_uvTmp;
    private int mBitrate;
    private int mWidth;
    private int mHeight;

    private boolean mIsCamera2;

    private boolean isMediaCodecStart = false;

    @SuppressLint("NewApi")
    public H264Encoder(int width, int height, int frameRate, int bitrate, boolean isCamera2) {
        m_frameRate = frameRate;
        m_frame_count = 0;
        mBitrate = bitrate;
        m_uvTmp = new byte[width * height / 2];
        mWidth = width;
        mHeight = height;
        mIsCamera2 = isCamera2;
        // 创建编码器
        try {
            Log.e(TAG, "width is " + width + "    height is " + height + "    frameRate is " + frameRate + "    bitrate is " + bitrate);
            mediaCodec = MediaCodec.createEncoderByType("video/avc");
            mediaFormat = MediaFormat.createVideoFormat("video/avc", width, height); // 宽和高 1920 X 1080

            if (isCamera2){
                mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, CodecCapabilities.COLOR_FormatYUV420SemiPlanar); // YUV颜色格式 nv12
            }else {
                m_colorFormat = getPreferColorFormat();
                mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, m_colorFormat); // YUV颜色格式
            }
            mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, mBitrate); // 比特率 2500 * 1000
            mediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, m_frameRate); // 帧率 30
            mediaFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, Constant.I_FRAME_INTERVAL); // I帧间隔 2分钟
            mediaFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, Constant.MAX_INPUT_SIZE);

            mediaCodec.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE); // 设置媒体格式
            mediaCodec.start();

            isMediaCodecStart = true;
        } catch (Exception e) {
            Log.e(TAG, "H264Encoder: initialize codec fail, ", e);
        } // 开启编码器
        m_isNeedSwap = !(("MediaPad 10 Link+").equals(android.os.Build.MODEL));
        Log.d(TAG, "H264Encoder: m_isNeedSwap=" + m_isNeedSwap);
    }

    public void resetH264Encoder() {
        if (mediaCodec == null) {
            return;
        }
        close();
        try {
            m_frame_count = 0;
            mediaCodec = MediaCodec.createEncoderByType("video/avc");
            mediaCodec.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            mediaCodec.start();
            isMediaCodecStart = true;
        } catch (Exception e) {
            // TODO: handle exception
            Log.e(TAG, "error is " + e.getMessage());
        }
    }

    public void close() {
        try {
            if (mediaCodec != null){
                isMediaCodecStart = false;
                mediaCodec.stop();
                mediaCodec.release();
                mediaCodec = null;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public int getSupportColorFormat() {
        return m_colorFormat;
    }

    /**
     * 请求I帧
     */
    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    public void requestSyncFrame() {
        if (mediaCodec != null) {
            Bundle params = new Bundle();
            params.putInt(MediaCodec.PARAMETER_KEY_REQUEST_SYNC_FRAME, 0);
            mediaCodec.setParameters(params);
        }
    }

    public int Encode(byte[] input, byte[] output) {
//        if (m_isNeedSwap && !mIsCamera2) {
            // 根据编码器的支持情况，转化YUV格式，摄像头出来的都是NV21
            if (m_colorFormat == CodecCapabilities.COLOR_FormatYUV420SemiPlanar) {
                swapNV21toCOLOR_FormatYUV420SemiPlanar(input, mWidth, mHeight);
            } else {
                swapNV21toCOLOR_FormatYUV420Planar(input, mWidth, mHeight);
//                 swapYV12toCOLOR_FormatYUV420Planar(input, mWidth, mHeight);
            }
//        }

        if (!isMediaCodecStart){
//            Log.d(TAG, "Encode: MediaCodec not start");
            return -1;
        }

        // add osd
//        AndroidVideoApi5JniWrapper.addOSD(input, mSize.width, mSize.height);
        int pos = 0;
        try {
            ByteBuffer inputBuffer;
            ByteBuffer outputBuffer;
            int inputBufferIndex = mediaCodec.dequeueInputBuffer(0); // 获取输入缓冲去所有权
            HLog.d(TAG, "Encode: inputBufferIndex= " + inputBufferIndex);
            if (inputBufferIndex >= 0) {
                inputBuffer = mediaCodec.getInputBuffer(inputBufferIndex);
                byte[] inputBytes = new byte[inputBuffer.remaining()];
                HLog.d(TAG,"Encode: inputBytes.length=" + inputBytes.length + ", input.length=" + input.length);
                inputBuffer.clear();
                inputBuffer.put(input);
                long presentationTimeUs = 1000000L * m_frame_count / m_frameRate;
                m_frame_count++;
                mediaCodec.queueInputBuffer(inputBufferIndex, 0, input.length, presentationTimeUs, 0); // 释放输入缓冲去所有权
            } else {
//                Log.d(TAG, "resetH264Encoder");
                resetH264Encoder();
            }

            MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
            int outputBufferIndex = mediaCodec.dequeueOutputBuffer(bufferInfo, 0);
            if (outputBufferIndex >= 0) {
                outputBuffer = mediaCodec.getOutputBuffer(outputBufferIndex);
                byte[] outData = new byte[bufferInfo.size];
                outputBuffer.get(outData);

                if ((outData[0] == 0x0) && (outData[1] == 0x0) && (outData[2] == 0x0) && (outData[3] == 0x1)) {
                    // sps and pps
                    if (((outData[4] & 0x1f) == 0x07) || ((outData[4] & 0x1f) == 0x08)) {
                        // Log.d("H264Encoder sps or pps");
                        if (m_info == null) {
                            m_info = new byte[outData.length];
                            System.arraycopy(outData, 0, m_info, 0, outData.length);
                        }
                    }
                    // I frame
                    else if ((outData[4] & 0x1f) == 0x05) {
                        // Log.d("H264Encoder I Frame");
                        System.arraycopy(m_info, 0, output, 0, m_info.length);
                        pos += m_info.length;
                        System.arraycopy(outData, 0, output, m_info.length, outData.length);
                        pos += outData.length;
                    }
                    // P/B frame
                    else {
                        // Log.d("H264Encoder P/B Frame");
                        System.arraycopy(outData, 0, output, 0, outData.length);
                        pos += outData.length;
                    }

                } else {
                    Log.d(TAG,"wrong h264 stream" + Arrays.toString(outData));
                    return -1;
                }

                mediaCodec.releaseOutputBuffer(outputBufferIndex,false);
                HLog.d(TAG,"encode releaseOutputBuffer pos=" + pos);
            } else {
                HLog.d(TAG,"wrong h264 stream" + outputBufferIndex);
            }
        } catch (Exception e) {
            HLog.e(TAG,"error, drop buffer, ", e);
        }
        Trace.endSection();
        return pos;
    }

    private int getPreferColorFormat() {
        int colorFormat = CodecCapabilities.COLOR_FormatYUV420Planar;
        // 查看编码器支持的颜色格式，并保存下来，作为后面YUV转化的标识
        int codecCount = MediaCodecList.getCodecCount();
        MediaCodecInfo mci = null;
        int i = 0;
        for (i = 0; i < codecCount; i++) {
            mci = MediaCodecList.getCodecInfoAt(i);
            if (mci.isEncoder()) {
                Log.d(TAG, "codec name: " + mci.getName());
                if (mci.getName().contains("264") || mci.getName().contains("AVC") || mci.getName().contains("avc") || mci.getName().contains("OMX.hisi.video.encoder")) {
                    break;
                }
            }
        }
        if (i == codecCount) {
            Log.e(TAG, "codec name find error");
        }
        if (mci != null) {
            Log.d(TAG, "get codec name: " + mci.getName());
            try {
                int colorFormat2[] = mci.getCapabilitiesForType("video/avc").colorFormats; // 部分设备该函数调用抛出异常
                ArrayList<Integer> listCF = new ArrayList<Integer>();
                for (int cf : colorFormat2) {
                    listCF.add(cf);
                }
                colorFormat = listCF.contains(CodecCapabilities.COLOR_FormatYUV420Planar) ? CodecCapabilities.COLOR_FormatYUV420Planar : CodecCapabilities.COLOR_FormatYUV420SemiPlanar;
            } catch (Exception e) {
                e.printStackTrace();
            }
            Log.d(TAG, "colorFormat = " + colorFormat);
        }
        return colorFormat;
    }

    private void swapYV12toCOLOR_FormatYUV420Planar(byte[] bytes, int width, int height) {
        int v_offset = width * height;
        int v_size = width * height / 4;
        int u_offset = v_offset + v_size;
        System.arraycopy(bytes, v_offset, m_uvTmp, 0, v_size);
        System.arraycopy(bytes, u_offset, bytes, v_offset, v_size);
        System.arraycopy(m_uvTmp, 0, bytes, u_offset, v_size);
    }

    private void swapNV21toCOLOR_FormatYUV420SemiPlanar(byte[] bytes, int width, int height) {
        int uv_offset = width * height;
        int u_size = width * height / 4;
        for (int i = 0; i < u_size; i++) {
            byte temp;
            temp = bytes[uv_offset + i * 2];
            bytes[uv_offset + i * 2] = bytes[uv_offset + i * 2 + 1];
            bytes[uv_offset + i * 2 + 1] = temp;
        }
    }

    private void swapNV21toCOLOR_FormatYUV420Planar(byte[] bytes, int width, int height) {
        int u_offset = width * height;
        int v_offset = u_offset + (u_offset / 4);
        int u_size = width * height / 4;

        try {
            System.arraycopy(bytes, u_offset, m_uvTmp, 0, u_size * 2);
            for (int i = 0; i < u_size; i++) {
                // U
                bytes[u_offset + i] = m_uvTmp[i * 2 + 1];
                // V
                bytes[v_offset + i] = m_uvTmp[i * 2];
            }
        } catch (Exception e) {
            // TODO: handle exception
        }
    }
}
