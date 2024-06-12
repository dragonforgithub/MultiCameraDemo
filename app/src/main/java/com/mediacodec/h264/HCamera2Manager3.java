package com.mediacodec.h264;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.media.AudioRecord;
import android.media.Image;
import android.media.ImageReader;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.text.TextUtils;
import android.util.Log;
import android.util.Size;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.TextureView;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.core.app.ActivityCompat;

import org.jetbrains.annotations.Nullable;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Objects;

@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
class HCamera2Manager3 {
    private static final String TAG = "HCamera2Manager3";

    private static HCamera2Manager3 ins;

    private ArrayList<H264Decoder> mH264DecoderArrayList = new ArrayList<H264Decoder>();
    private Context mContext;
    private SurfaceHolder mSurfaceHolder;
    private Handler mThreadHandler;
    private  TextureView mTextureView;
    private final int mCameraId = 154;
    private final int mWidth = 1920;
    private final int mHeight = 1080;
    private final Size mPreviewSize = new Size(mWidth,mHeight);
    private CaptureRequest.Builder mCaptureRequestBuilder;
    private CaptureRequest mCaptureRequest;
    private CameraCaptureSession mCameraCaptureSession;
    HandlerThread handlerThread;
    private CameraDevice mCameraDevice;
    private H264Encoder mH264Encoder;
    private ImageReader mImageReader;
    private Thread mListThread;

    public static byte[] h264 = new byte[2073600]; //y-2073600 u-1036799 v-1036799

    public static HCamera2Manager3 getInstance() {
        if (ins == null) {
            synchronized (HCamera2Manager3.class) {
                if (ins == null) {
                    ins = new HCamera2Manager3();
                }
            }
        }
        return ins;
    }

    private HCamera2Manager3() {
    }

    public void addH264Decoder(H264Decoder h264Decoder){
        mH264DecoderArrayList.add(h264Decoder);
    }
    private TextureView.SurfaceTextureListener mTextureListener = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
            //当SurfaceTexture可用的时候，设置相机参数并打开相机
            startCamera();
            //开启设备列表监听，在设备可用时打开
            //mListThread.start();
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
            return false;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surface) {

        }
    };

    private ImageReader.OnImageAvailableListener onImageAvailableListener = new ImageReader.OnImageAvailableListener(){

        @Override
        public void onImageAvailable(ImageReader reader) {
            Image image = reader.acquireNextImage();
            if (image != null){
                Image.Plane[] planes = image.getPlanes();
                ByteBuffer yBuffer = planes[0].getBuffer();//y
                ByteBuffer uBuffer = planes[1].getBuffer();//u
                ByteBuffer vBuffer = planes[2].getBuffer();//v
                byte[] yBytes = new byte[yBuffer.remaining()];
                byte[] uBytes = new byte[uBuffer.remaining()];
                byte[] vBytes = new byte[vBuffer.remaining()];
                yBuffer.get(yBytes);//由缓冲区写入字节数组
                uBuffer.get(uBytes);//由缓冲区写入字节数组
                vBuffer.get(vBytes);//由缓冲区写入字节数组
                HLog.d(TAG, "onImageAvailable: planes.size=" + planes.length + ",yBytes=" + yBytes.length
                        + ",uBytes=" + uBytes.length + ",vBytes=" + vBytes.length);
                byte[] inputs = new byte[yBytes.length + uBytes.length + vBytes.length];
                System.arraycopy(yBytes,0, inputs, 0, yBytes.length);
                System.arraycopy(uBytes,0, inputs, yBytes.length, uBytes.length);
                System.arraycopy(vBytes,0, inputs, yBytes.length + uBytes.length, vBytes.length);
                int len = mH264Encoder.Encode(inputs, h264);
                if (len > 0) {
                    if (mH264DecoderArrayList.size() > 0) {
                        for (int i = 0; i < mH264DecoderArrayList.size(); i++) {
                            mH264DecoderArrayList.get(i).Decode(h264, Constant.FRAME_RATE, len);
                        }
                    }
                }
                image.close();
//                reader.close();
            }
        }
    };

    byte[] mYuvBytes  = new byte[mWidth * mHeight * 3 / 2];
    private ImageReader.OnImageAvailableListener onImageAvailableListener2 = new ImageReader.OnImageAvailableListener(){
        @Override
        public void onImageAvailable(ImageReader reader) {
            Image image = reader.acquireNextImage();
            if (image != null){
                Image.Plane[] planes = image.getPlanes();
                int yLen = mWidth * mHeight;
                // Y通道，对应planes[0]
                // Y size = width * height
                // yBuffer.remaining() = width * height;
                // pixelStride = 1
                ByteBuffer yBuffer = planes[0].getBuffer();//y
                yBuffer.get(mYuvBytes, 0, yLen);

                // U通道，对应planes[1]
                // U size = width * height / 4;
                // uBuffer.remaining() = width * height / 2;
                // pixelStride = 2
                ByteBuffer uBuffer = planes[1].getBuffer();//u
                int pixelStride = planes[1].getPixelStride();
                for (int i = 0; i < uBuffer.remaining(); i+=pixelStride) {
                    mYuvBytes[yLen++] = uBuffer.get(i);
                }

                // V通道，对应planes[2]
                // V size = width * height / 4;
                // vBuffer.remaining() = width * height / 2;
                // pixelStride = 2
                ByteBuffer vBuffer = planes[2].getBuffer();//v
                pixelStride = planes[2].getPixelStride(); // pixelStride = 2
                for (int i = 0; i < vBuffer.remaining(); i+=pixelStride) {
                    mYuvBytes[yLen++] = vBuffer.get(i);
                }
                int len = mH264Encoder.Encode(mYuvBytes, h264);
                if (len > 0) {
                    if (mH264DecoderArrayList.size() > 0) {
                        for (int i = 0; i < mH264DecoderArrayList.size(); i++) {
                            mH264DecoderArrayList.get(i).Decode(h264, Constant.FRAME_RATE, len);
                        }
                    }
                }
                image.close();
//                reader.close();
            }
        }
    };

    public void initCamera(TextureView view, @Nullable Context applicationContext){
        if (view == null || applicationContext == null){
            Log.e(TAG, "initCamera: return");
            return;
        }

        Log.d(TAG, "initCamera: start");
        mContext = applicationContext;
        mTextureView = view;
        if (!mTextureView.isAvailable()) {
            mTextureView.setSurfaceTextureListener(mTextureListener);
        }

        handlerThread = new HandlerThread("Camera2");
        handlerThread.start();
        mThreadHandler = new Handler(handlerThread.getLooper());
        //mImageReader = ImageReader.newInstance(Constant.WIDTH,Constant.HEIGHT, ImageFormat.YUV_420_888,1);
        //mImageReader.setOnImageAvailableListener(onImageAvailableListener2, mThreadHandler);

        CameraManager cameraManager = (CameraManager) mContext.getSystemService(Context.CAMERA_SERVICE);
        mListThread = new Thread(new Runnable() {
            @Override
            public void run() {
                do {
                    try {
                        String[] cameraIdList = cameraManager.getCameraIdList();
                        //Log.i(TAG, "startCamera: monitoring ...... cameraList=" + Arrays.toString(cameraIdList));
                        for (String cameraId : cameraIdList) {
                            if (Objects.equals(cameraId, Integer.toString(mCameraId))) {
                                Thread.sleep(500);
                                if (mCameraDevice == null) {
                                    startCamera();
                                }
                                break;
                            }
                        }
                        Thread.sleep(1000);
                    } catch (CameraAccessException e) {
                        throw new RuntimeException(e);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                } while (true);
            }
        });
    }

    //close camera
    public void closeCamera() {
        if (mCameraDevice != null){
            mCameraDevice.close();
            mCameraDevice = null;
        }
        if (mImageReader != null){
            mImageReader.close();
            mImageReader = null;
        }
    }

    private void startCamera() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            Log.d(TAG, "startCamera: start");
            CameraManager cameraManager = (CameraManager) mContext.getSystemService(Context.CAMERA_SERVICE);
            if (cameraManager == null){
                Log.d(TAG, "startCamera: cameraManager is empty, so return");
                return;
            }
            Log.d(TAG, "startCamera: get camera service over");
            try {
                if (ActivityCompat.checkSelfPermission(mContext, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                    Log.d(TAG, "startCamera: lack permission");
                    return;
                }
                Log.d(TAG, "startCamera: before get camera list");
                String[] cameraIdList = cameraManager.getCameraIdList();

                String useCameraId = "";
                Log.i(TAG, "startCamera: cameraList=" + Arrays.toString(cameraIdList));
                for (String cameraId : cameraIdList) {
                    if (Objects.equals(cameraId, Integer.toString(mCameraId))) {
                        useCameraId = cameraId;
                        Log.i(TAG, "Found useCameraId : " + useCameraId);
                        break;
                    }
                }

                if (TextUtils.isEmpty(useCameraId)){
                    Log.d(TAG, "startCamera: do not get an available cameraId,so return");
                    return;
                }

                cameraManager.openCamera(useCameraId, new CameraDevice.StateCallback() {
                    public void onOpened(@NonNull CameraDevice cameraDevice) {
                        Log.d(TAG, "onOpened: open success ");
                        mCameraDevice = cameraDevice;
                        startPreview(cameraDevice);
                    }

                    @Override
                    public void onDisconnected(@NonNull CameraDevice cameraDevice) {
                        Log.d(TAG, "onDisconnected: ");
                        closeCamera();
                    }

                    @Override
                    public void onError(@NonNull CameraDevice camera, int error) {
                        Log.d(TAG, "onError: " + error);
                        closeCamera();
                    }
                }, new Handler(mContext.getMainLooper()));
            } catch (CameraAccessException e) {
                Log.e(TAG, "startCamera: open fail", e);
            }finally {
                Log.d(TAG, "startCamera: over");
            }
        }
    }

    private void startPreview(@NonNull CameraDevice cameraDevice){
        SurfaceTexture mSurfaceTexture = mTextureView.getSurfaceTexture();
        assert mSurfaceTexture != null;
        mSurfaceTexture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());

        Surface previewSurface = new Surface(mSurfaceTexture);
        try {
            mCaptureRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            mCaptureRequestBuilder.addTarget(previewSurface);
            mCameraDevice.createCaptureSession(Collections.singletonList(previewSurface), new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession session) {
                    mCaptureRequest = mCaptureRequestBuilder.build();
                    mCameraCaptureSession = session;
                    try {
                        mCameraCaptureSession.setRepeatingRequest(mCaptureRequest, null, mThreadHandler);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession session) {

                }
            }, mThreadHandler);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

