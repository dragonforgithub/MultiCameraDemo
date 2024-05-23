package com.mediacodec.h264;

import android.annotation.SuppressLint;
import android.hardware.input.InputManager;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import androidx.annotation.NonNull;

import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

public class H264Decoder {
    private final static String TAG = H264Decoder.class.getSimpleName();
    private MediaCodec mediaCodec;
    int m_width;
    int m_height;
    int m_slice_height;
    int m_frameRate;
    int m_frame_count;
    int m_color_format;
    int m_stride;
    int m_ssrc;

    boolean isAllowDecode = false;   //有些设备送入MediaCodec的第一帧必须为I帧，因此需要等到收到I帧才能开始解码    
    boolean isExecuting = false;

    FileOutputStream fo;
    InputThread inputThread;

    private final ReentrantLock mediacLock = new ReentrantLock();

    public H264Decoder() {
        m_frameRate = Constant.FRAME_RATE;
        m_frame_count = 0;
        m_width = Constant.WIDTH;
        m_height = Constant.HEIGHT;
        m_slice_height = 0;
        m_stride = 0;
        isExecuting = false;
        isAllowDecode = false;
    }

    public void initMediaCodec(SurfaceView surfaceView, int ssrc) {
        Log.i(TAG, "H264Decoder start------------------------------------------------" + ssrc);
        m_ssrc = ssrc;
		if (surfaceView.getHolder().getSurface().isValid()) {
			startMediaCodec(surfaceView.getHolder().getSurface(), ssrc);
		}else {
			surfaceView.getHolder().addCallback(new SurfaceHolder.Callback() {
				@Override
				public void surfaceCreated(@NonNull SurfaceHolder surfaceHolder) {
					startMediaCodec(surfaceHolder.getSurface(), m_ssrc);
				}

				@Override
				public void surfaceChanged(@NonNull SurfaceHolder surfaceHolder, int i, int i1, int i2) {

				}

				@Override
				public void surfaceDestroyed(@NonNull SurfaceHolder surfaceHolder) {

				}
			});
		}
        inputThread = new InputThread();
        inputThread.start();
    }

    //开启解码器
    private void startMediaCodec(Surface surface, int ssrc) {
        mediacLock.lock();
        Log.i(TAG,"start mediacodec start------------------------------------------------" + ssrc);
        try {
            if (!isExecuting) {
                String decodecType = "video/avc";
                mediaCodec = MediaCodec.createDecoderByType(decodecType);
                MediaFormat mediaFormat = MediaFormat.createVideoFormat(decodecType, m_width, m_height);
                mediaFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 1024 * 1024);
                mediaCodec.configure(mediaFormat, surface, null, 0);
                mediaCodec.start();

                isAllowDecode = false;
                isExecuting = true;
            }
            mediacLock.unlock();
            Log.i(TAG, "start mediacodec success -----------------------------------" + ssrc);
        } catch (Exception e) {
            mediacLock.unlock();
            Log.i(TAG, "start mediacodec failure -------------------------------------" + ssrc);
            e.printStackTrace();
        }
    }

    //开启解码器
    @SuppressLint("NewApi")
    public void Close() {
        Log.i(TAG, "Close start ------------------------------------ssrc = " + m_ssrc);
        if (inputThread != null) {
            inputThread.exit = true;
            try {
                inputThread.join();
            } catch (InterruptedException e1) {
                e1.printStackTrace();
            }
        }
//        if (mDecodeSurface != null) {
//            mDecodeSurface.release();
//            mDecodeSurface = null;
//        }
        try {
            Log.i(TAG, "stop mediacodec start ------------------------------------ssrc = " + m_ssrc);
            mediacLock.lock();
            isAllowDecode = false;
            isExecuting = false;
            h264List.clear();
            if (mediaCodec != null) {
                mediaCodec.stop();
                mediaCodec.release();
                mediaCodec = null;
            }
            mediacLock.unlock();
            Log.i(TAG, "stop mediacodec success ------------------------------------");
        } catch (Exception e) {
            mediacLock.unlock();
            e.printStackTrace();
            Log.i(TAG, "stop mediacodec failure ------------------------------------");

        }

    }

    List<byte[]> h264List = new LinkedList<byte[]>();

    public int Decode(byte[] input, int frameRate, int length) {

        if (!isExecuting)
            return 0;

        //有数据的时候，将数据放入队列，
        if (length > 0) {
            if (frameRate != 0)
                m_frameRate = frameRate;

//            if (!isAllowDecode) {    //确保第一帧输入的是I帧
//                if (isIFrame) {
//                    isAllowDecode = true;
//                } else {
//                    return 0;
//                }
//            }
//            byte[] h264Data = new byte[lenght];
//            input.get(h264Data);
            synchronized (h264List) {
                h264List.add(input);
            }
        }
        return 0;
    }

    class InputThread extends Thread {
        public boolean exit = false;

        public void run() {
            while (!exit) {
                try {
                    if (!isExecuting) {
                        Thread.sleep(100);
                        continue;
                    }

                    ByteBuffer[] inputBuffers = mediaCodec.getInputBuffers();
                    //检查h264数据的队列
                    if (h264List.size() == 0) {
                        Thread.sleep(10);
                        continue;
                    }
                    mediacLock.lock();
                    if (isExecuting) {
                        int inputBufferIndex = mediaCodec.dequeueInputBuffer(0);
                        Log.d(TAG, "decode inputBufferIndex=" + inputBufferIndex);
                        if (inputBufferIndex >= 0) {
                            //拿出待解码的数据
                            byte[] inputBytes = null;
                            synchronized (h264List) {
                                inputBytes = h264List.get(0);
                                h264List.remove(0);
                            }
		    		            	
		    		            	/*
		    		            	if (nWindowSsrc % 10 == 1) {
			    		            	if (fo == null) {
			    		            		fo = new FileOutputStream("/sdcard/share/Media/temp.h264");
			    		            	} 
			    		            	fo.write(inputBytes, 0, inputBytes.length);
		    		            	}
		    		            	*/
                            //送入解码器
                            ByteBuffer inputBuffer = inputBuffers[inputBufferIndex];
                            inputBuffer.clear();
                            inputBuffer.put(inputBytes);
                            inputBuffer.flip();
                            long presentationTimeUs = 1000000L * m_frame_count / m_frameRate;
                            m_frame_count++;
                            mediaCodec.queueInputBuffer(inputBufferIndex, 0, inputBuffer.limit(), presentationTimeUs, 0);
                            Log.d(TAG, "decode queueInputBuffer inputBufferIndex=" + inputBufferIndex);
                        }

                        MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
                        int outputBufferIndex = mediaCodec.dequeueOutputBuffer(bufferInfo, 20 * 1000);

                        Log.d(TAG, "decode dequeueOutputBuffer outputBufferIndex=" + outputBufferIndex);

                        switch (outputBufferIndex) {
                            case MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED:
                                Log.i(TAG,"INFO_OUTPUT_BUFFERS_CHANGED");
                                break;
                            case MediaCodec.INFO_OUTPUT_FORMAT_CHANGED:
                                Log.d(TAG,
                                        "   width: " + m_width
                                                + "	height: " + m_height
                                                + "	slice-height: " + m_slice_height
                                                + "	color_format: " + m_color_format
                                                + "	stride: " + m_stride);
                                break;

                            case MediaCodec.INFO_TRY_AGAIN_LATER:
                                Log.d(TAG, "INFO_TRY_AGAIN_LATER ssrc = " + m_ssrc);
                                break;
                            default:
                                Log.i(TAG, "decode OK: " + outputBufferIndex);
                                mediaCodec.releaseOutputBuffer(outputBufferIndex, true);
                        }
                    }
                    mediacLock.unlock();
                } catch (Exception e) {
                    if (mediacLock.isLocked()) {
                        mediacLock.unlock();
                    }
                    e.printStackTrace();
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e1) {
                        // TODO Auto-generated catch block
                        e1.printStackTrace();
                    }
                }
            }//while
        }//run
    }//thread    

}
