package com.hjq.demo.camera2;

import android.content.Context;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Point;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.graphics.YuvImage;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureFailure;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.text.TextUtils;
import android.util.Log;
import android.util.Size;
import android.view.Surface;
import android.view.TextureView;

import androidx.annotation.NonNull;

import com.hjq.demo.ui.activity.ScanByHWActivity;
import com.hjq.demo.utils.LogUtils;
import com.huawei.hms.hmsscankit.ScanUtil;
import com.huawei.hms.ml.scan.HmsScan;
import com.huawei.hms.ml.scan.HmsScanFrame;
import com.huawei.hms.ml.scan.HmsScanFrameOptions;
import com.huawei.hms.ml.scan.HmsScanResult;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

public class Camera2Helper {
    private static final String TAG = "Camera2Helper";

    private Point maxPreviewSize;
    private Point minPreviewSize;

    public static final String CAMERA_ID_FRONT = "1";
    public static final String CAMERA_ID_BACK = "0";


    private String mCameraId;
    private String specificCameraId;
    private Camera2Listener camera2Listener;
    private TextureView mTextureView;
    private int rotation;
    private Point previewViewSize;
    private Point specificPreviewSize;
    private boolean isMirror;
    private Context context;
    /**
     * A {@link CameraCaptureSession } for camera preview.
     */
    private CameraCaptureSession mCaptureSession;

    /**
     * A reference to the opened {@link CameraDevice}.
     */
    private CameraDevice mCameraDevice;

    private Size mPreviewSize;

    private static final int MAX_PREVIEW_WIDTH = 960;//960;//Max preview width that is guaranteed by Camera2 API
    private static final int MAX_PREVIEW_HEIGHT = 720;//720;//Max preview height that is guaranteed by Camera2 API

    private Camera2Helper(Builder builder) {
        mTextureView = builder.previewDisplayView;
        specificCameraId = builder.specificCameraId;
        camera2Listener = builder.camera2Listener;
        rotation = builder.rotation;
        previewViewSize = builder.previewViewSize;
        specificPreviewSize = builder.previewSize;
        maxPreviewSize = builder.maxPreviewSize;
        minPreviewSize = builder.minPreviewSize;
        isMirror = builder.isMirror;
        context = builder.context;
        if (isMirror) {
            mTextureView.setScaleX(-1);
        }
    }

    public void switchCamera() {
        if (CAMERA_ID_BACK.equals(mCameraId)) {
            specificCameraId = CAMERA_ID_FRONT;
        } else if (CAMERA_ID_FRONT.equals(mCameraId)) {
            specificCameraId = CAMERA_ID_BACK;
        }
        stop();
        start();
    }

    private int getCameraOri(int rotation, String cameraId) {
        int degrees = rotation * 90;
        switch (rotation) {
            case Surface.ROTATION_0:
                degrees = 0;
                break;
            case Surface.ROTATION_90:
                degrees = 90;
                break;
            case Surface.ROTATION_180:
                degrees = 180;
                break;
            case Surface.ROTATION_270:
                degrees = 270;
                break;
            default:
                break;
        }
        int result;
        if (CAMERA_ID_FRONT.equals(cameraId)) {
            result = (mSensorOrientation + degrees) % 360;
            result = (360 - result) % 360;
        } else {
            result = (mSensorOrientation - degrees + 360) % 360;
        }
        LogUtils.i( "getCameraOri: " + rotation + " " + result + " " + mSensorOrientation);
        return result;
    }

    private final TextureView.SurfaceTextureListener mSurfaceTextureListener = new TextureView.SurfaceTextureListener() {

        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture texture, int width, int height) {
            LogUtils.i( "onSurfaceTextureAvailable: ");
            openCamera();
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture texture, int width, int height) {
            LogUtils.i( "onSurfaceTextureSizeChanged: ");
            configureTransform(width, height);
        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture texture) {
            LogUtils.i( "onSurfaceTextureDestroyed: ");
            return true;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture texture) {
        }

    };

    private CameraDevice.StateCallback mDeviceStateCallback = new CameraDevice.StateCallback() {

        @Override
        public void onOpened(@NonNull CameraDevice cameraDevice) {
            LogUtils.i( "onOpened: ");
            // This method is called when the camera is opened.  We start camera preview here.
            mCameraOpenCloseLock.release();
            mCameraDevice = cameraDevice;
            createCameraPreviewSession();
            if (camera2Listener != null) {
                camera2Listener.onCameraOpened(cameraDevice, mCameraId, mPreviewSize, getCameraOri(rotation, mCameraId), isMirror);
            }
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice cameraDevice) {
            LogUtils.i( "onDisconnected: ");
            mCameraOpenCloseLock.release();
            cameraDevice.close();
            mCameraDevice = null;
            if (camera2Listener != null) {
                camera2Listener.onCameraClosed();
            }
        }

        @Override
        public void onError(@NonNull CameraDevice cameraDevice, int error) {
            LogUtils.i( "onError: ");
            mCameraOpenCloseLock.release();
            cameraDevice.close();
            mCameraDevice = null;

            if (camera2Listener != null) {
                camera2Listener.onCameraError(new Exception("error occurred, code is " + error));
            }
        }

    };
    private CameraCaptureSession.StateCallback mCaptureStateCallback = new CameraCaptureSession.StateCallback() {

        @Override
        public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
            LogUtils.i( "onConfigured: ");
            // The camera is already closed
            if (null == mCameraDevice) {
                return;
            }

            // When the session is ready, we start displaying the preview.
            mCaptureSession = cameraCaptureSession;
            try {
                mCaptureSession.setRepeatingRequest(mPreviewRequestBuilder.build(),
                        new CameraCaptureSession.CaptureCallback() {
                            @Override
                            public void onCaptureStarted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, long timestamp, long frameNumber) {
                                super.onCaptureStarted(session, request, timestamp, frameNumber);
                                LogUtils.i( "onCaptureStarted: ");
                            }

                            @Override
                            public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
                                super.onCaptureCompleted(session, request, result);
//                                try {
//                                    CaptureRequest.Builder mCaptureRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
//                                    mCaptureRequestBuilder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO);
////                                    mCaptureRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
//                                    mCaptureRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_START);
//                                    mCaptureSession.capture(mCaptureRequestBuilder.build(),null,null);
//                                } catch (Exception e) {
//                                    e.printStackTrace();
//                                }
                                LogUtils.i( "onCaptureCompleted: ");
                            }

                            @Override
                            public void onCaptureProgressed(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull CaptureResult partialResult) {
                                super.onCaptureProgressed(session, request, partialResult);
                                LogUtils.i( "onCaptureProgressed: ");
                            }

                            @Override
                            public void onCaptureFailed(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull CaptureFailure failure) {
                                super.onCaptureFailed(session, request, failure);
                                LogUtils.i( "onCaptureFailed: ");
                            }
                        }, mBackgroundHandler);
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onConfigureFailed(
                @NonNull CameraCaptureSession cameraCaptureSession) {
            LogUtils.i( "onConfigureFailed: ");
            if (camera2Listener != null) {
                camera2Listener.onCameraError(new Exception("configureFailed"));
            }
        }
    };
    /**
     * An additional thread for running tasks that shouldn't block the UI.
     */
    private HandlerThread mBackgroundThread;

    /**
     * A {@link Handler} for running tasks in the background.
     */
    private Handler mBackgroundHandler;

    private ImageReader mImageReader;


    /**
     * {@link CaptureRequest.Builder} for the camera preview
     */
    private CaptureRequest.Builder mPreviewRequestBuilder;


    /**
     * A {@link Semaphore} to prevent the app from exiting before closing the camera.
     */
    private Semaphore mCameraOpenCloseLock = new Semaphore(1);


    /**
     * Orientation of the camera sensor
     */
    private int mSensorOrientation;

    private Size getBestSupportedSize(List<Size> sizes) {
        Size defaultSize = sizes.get(0);
        Size[] tempSizes = sizes.toArray(new Size[0]);
        LogUtils.i( "tempSizes: "+tempSizes.length);
        Arrays.sort(tempSizes, new Comparator<Size>() {
            @Override
            public int compare(Size o1, Size o2) {
                if (o1.getWidth() > o2.getWidth()) {
                    return -1;
                } else if (o1.getWidth() == o2.getWidth()) {
                    return o1.getHeight() > o2.getHeight() ? -1 : 1;
                } else {
                    return 1;
                }
            }
        });
        sizes = new ArrayList<>(Arrays.asList(tempSizes));
        float tmpAspect = 1f;
        float aspect = 1f;
        Size tmpSize = null;
        for (int i = sizes.size() - 1; i >= 0; i--) {
//            if (maxPreviewSize != null) {
//                if (sizes.get(i).getWidth() > maxPreviewSize.x || sizes.get(i).getHeight() > maxPreviewSize.y) {
//                    sizes.remove(i);
//                    continue;
//                }
//            }

            if (minPreviewSize != null) {
                aspect =Math.abs((minPreviewSize.x*1.0f/minPreviewSize.y) - (sizes.get(i).getWidth()*1.0f/sizes.get(i).getHeight()));
                if(aspect< tmpAspect){
                    tmpAspect = aspect;
                    tmpSize = sizes.get(i);
                }
//                if (sizes.get(i).getWidth() < minPreviewSize.x || sizes.get(i).getHeight() < minPreviewSize.y) {
//                    sizes.remove(i);
//                }
            }
        }
        LogUtils.i( "getBestSupportedSize: "+tmpSize.getWidth() + "--"+tmpSize.getHeight());
        return  tmpSize;

//        LogUtils.i( "getBestSupportedSize: "+sizes.size());
//        if (sizes.size() == 0) {
//            String msg = "can not find suitable previewSize, now using default";
//            if (camera2Listener != null) {
//                LogUtils.i( msg);
//                camera2Listener.onCameraError(new Exception(msg));
//            }
//            return defaultSize;
//        }
//        Size bestSize = sizes.get(0);
//        float previewViewRatio;
//        if (previewViewSize != null) {
//            previewViewRatio = (float) previewViewSize.x / (float) previewViewSize.y;
//        } else {
//            previewViewRatio = (float) bestSize.getWidth() / (float) bestSize.getHeight();
//        }
//
//        if (previewViewRatio > 1) {
//            previewViewRatio = 1 / previewViewRatio;
//        }
//
//        for (Size s : sizes) {
//            if (specificPreviewSize != null && specificPreviewSize.x == s.getWidth() && specificPreviewSize.y == s.getHeight()) {
//                return s;
//            }
//            if (Math.abs((s.getHeight() / (float) s.getWidth()) - previewViewRatio) < Math.abs(bestSize.getHeight() / (float) bestSize.getWidth() - previewViewRatio)) {
//                bestSize = s;
//            }
//        }
//        return bestSize;
    }

    public synchronized void start() {
        if (mCameraDevice != null) {
            return;
        }
        startBackgroundThread();

        // When the screen is turned off and turned back on, the SurfaceTexture is already
        // available, and "onSurfaceTextureAvailable" will not be called. In that case, we can open
        // a camera and start preview from here (otherwise, we wait until the surface is ready in
        // the SurfaceTextureListener).
        if (mTextureView.isAvailable()) {
            openCamera();
        } else {
            mTextureView.setSurfaceTextureListener(mSurfaceTextureListener);
        }
    }

    public synchronized void stop() {
        if (mCameraDevice == null) {
            return;
        }
        closeCamera();
        stopBackgroundThread();
    }

    public void release() {
        stop();
        mTextureView = null;
        camera2Listener = null;
        context = null;
    }

    private void setUpCameraOutputs(CameraManager cameraManager) {
        try {
            if (configCameraParams(cameraManager, specificCameraId)) {
                return;
            }
            for (String cameraId : cameraManager.getCameraIdList()) {
                if (configCameraParams(cameraManager, cameraId)) {
                    return;
                }
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        } catch (NullPointerException e) {
            // Currently an NPE is thrown when the Camera2API is used but not supported on the
            // device this code runs.

            if (camera2Listener != null) {
                camera2Listener.onCameraError(e);
            }
        }
    }

    private boolean configCameraParams(CameraManager manager, String cameraId) throws CameraAccessException {
        CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);

        StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        if (map == null) {
            return false;
        }
        mPreviewSize = getBestSupportedSize(new ArrayList<Size>(Arrays.asList(map.getOutputSizes(SurfaceTexture.class))));
        mImageReader = ImageReader.newInstance(mPreviewSize.getWidth(), mPreviewSize.getHeight(), ImageFormat.YUV_420_888, 2);
        mImageReader.setOnImageAvailableListener(new OnImageAvailableListenerImpl(), mBackgroundHandler);

        mSensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
        mCameraId = cameraId;
        return true;
    }

    private void openCamera() {
        CameraManager cameraManager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
        setUpCameraOutputs(cameraManager);
        configureTransform(mTextureView.getWidth(), mTextureView.getHeight());
        try {
            if (!mCameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
                throw new RuntimeException("Time out waiting to lock camera opening.");
            }
            cameraManager.openCamera(mCameraId, mDeviceStateCallback, mBackgroundHandler);
        } catch (CameraAccessException e) {
            if (camera2Listener != null) {
                camera2Listener.onCameraError(e);
            }
        } catch (InterruptedException e) {
            if (camera2Listener != null) {
                camera2Listener.onCameraError(e);
            }
        }
    }

    /**
     * Closes the current {@link CameraDevice}.
     */
    private void closeCamera() {
        try {
            mCameraOpenCloseLock.acquire();
            if (null != mCaptureSession) {
                mCaptureSession.close();
                mCaptureSession = null;
            }
            if (null != mCameraDevice) {
                mCameraDevice.close();
                mCameraDevice = null;
            }
            if (null != mImageReader) {
                mImageReader.close();
                mImageReader = null;
            }
            if (camera2Listener != null) {
                camera2Listener.onCameraClosed();
            }
        } catch (InterruptedException e) {
            if (camera2Listener != null) {
                camera2Listener.onCameraError(e);
            }
        } finally {
            mCameraOpenCloseLock.release();
        }
    }

    /**
     * Starts a background thread and its {@link Handler}.
     */
    private void startBackgroundThread() {
        mBackgroundThread = new HandlerThread("CameraBackground");
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
    }

    /**
     * Stops the background thread and its {@link Handler}.
     */
    private void stopBackgroundThread() {
        mBackgroundThread.quitSafely();
        try {
            mBackgroundThread.join();
            mBackgroundThread = null;
            mBackgroundHandler = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    /**
     * Creates a new {@link CameraCaptureSession} for camera preview.
     */
    private void createCameraPreviewSession() {
        try {
            SurfaceTexture texture = mTextureView.getSurfaceTexture();
            assert texture != null;
            LogUtils.i( "mPreviewSize.getWidth(): "+mPreviewSize.getWidth());
            LogUtils.i( "mPreviewSize.getHeight(): "+mPreviewSize.getHeight());

            // We configure the size of default buffer to be the size of camera preview we want.
            if (CAMERA_ID_BACK.equals(mCameraId)) {
                texture.setDefaultBufferSize(1920, 1080);
            } else if (CAMERA_ID_FRONT.equals(mCameraId)) {
                texture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());
            }

            // This is the output Surface we need to start preview.
            Surface surface = new Surface(texture);

            // We set up a CaptureRequest.Builder with the output Surface.
            mPreviewRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);

//            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO);
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF);// 定焦
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON);
            mPreviewRequestBuilder.set(CaptureRequest.LENS_FOCUS_DISTANCE, 4f); // 焦距，屈光度 1/N Meter
            mPreviewRequestBuilder.addTarget(surface);
            mPreviewRequestBuilder.addTarget(mImageReader.getSurface());

            // Here, we create a CameraCaptureSession for camera preview.
            mCameraDevice.createCaptureSession(Arrays.asList(surface, mImageReader.getSurface()), mCaptureStateCallback, mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     * Configures the necessary {@link Matrix} transformation to `mTextureView`.
     * This method should be called after the camera preview size is determined in
     * setUpCameraOutputs and also the size of `mTextureView` is fixed.
     *
     * @param viewWidth  The width of `mTextureView`
     * @param viewHeight The height of `mTextureView`
     */
    private void configureTransform(int viewWidth, int viewHeight) {
        if (null == mTextureView || null == mPreviewSize) {
            return;
        }
        Matrix matrix = new Matrix();
        RectF viewRect = new RectF(0, 0, viewWidth, viewHeight);
        RectF bufferRect = new RectF(0, 0, mPreviewSize.getHeight(), mPreviewSize.getWidth());
        float centerX = viewRect.centerX();
        float centerY = viewRect.centerY();
        if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation) {
            bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY());
            matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL);
            float scale = Math.max(
                    (float) viewHeight / mPreviewSize.getHeight(),
                    (float) viewWidth / mPreviewSize.getWidth());
            matrix.postScale(scale, scale, centerX, centerY);
            matrix.postRotate((90 * (rotation - 2)) % 360, centerX, centerY);
        } else if (Surface.ROTATION_180 == rotation) {
            matrix.postRotate(180, centerX, centerY);
        }
        LogUtils.i( "configureTransform: " + getCameraOri(rotation, mCameraId) + "  " + rotation * 90);
        mTextureView.setTransform(matrix);
    }

    public static final class Builder {

        /**
         * 预览显示的view，目前仅支持textureView
         */
        private TextureView previewDisplayView;

        /**
         * 是否镜像显示，只支持textureView
         */
        private boolean isMirror;
        /**
         * 指定的相机ID
         */
        private String specificCameraId;
        /**
         * 事件回调
         */
        private Camera2Listener camera2Listener;
        /**
         * 屏幕的长宽，在选择最佳相机比例时用到
         */
        private Point previewViewSize;
        /**
         * 传入getWindowManager().getDefaultDisplay().getRotation()的值即可
         */
        private int rotation;
        /**
         * 指定的预览宽高，若系统支持则会以这个预览宽高进行预览
         */
        private Point previewSize;
        /**
         * 最大分辨率
         */
        private Point maxPreviewSize;
        /**
         * 最小分辨率
         */
        private Point minPreviewSize;
        /**
         * 上下文，用于获取CameraManager
         */
        private Context context;

        public Builder() {
        }


        public Builder previewOn(TextureView val) {
            previewDisplayView = val;
            return this;
        }


        public Builder isMirror(boolean val) {
            isMirror = val;
            return this;
        }

        public Builder previewSize(Point val) {
            previewSize = val;
            return this;
        }

        public Builder maxPreviewSize(Point val) {
            maxPreviewSize = val;
            return this;
        }

        public Builder minPreviewSize(Point val) {
            minPreviewSize = val;
            return this;
        }

        public Builder previewViewSize(Point val) {
            previewViewSize = val;
            return this;
        }

        public Builder rotation(int val) {
            rotation = val;
            return this;
        }


        public Builder specificCameraId(String val) {
            specificCameraId = val;
            return this;
        }

        public Builder cameraListener(Camera2Listener val) {
            camera2Listener = val;
            return this;
        }

        public Builder context(Context val) {
            context = val;
            return this;
        }

        public Camera2Helper build() {
            if (previewViewSize == null) {
                LogUtils.i( "previewViewSize is null, now use default previewSize");
            }
            if (camera2Listener == null) {
                LogUtils.i( "camera2Listener is null, callback will not be called");
            }
            if (previewDisplayView == null) {
                throw new NullPointerException("you must preview on a textureView or a surfaceView");
            }
            if (maxPreviewSize != null && minPreviewSize != null) {
                if (maxPreviewSize.x < minPreviewSize.x || maxPreviewSize.y < minPreviewSize.y) {
                    throw new IllegalArgumentException("maxPreviewSize must greater than minPreviewSize");
                }
            }
            return new Camera2Helper(this);
        }
    }

    private static byte[] YUV_420_888toNV21_Y(Image image) {
        ByteBuffer yBuffer = image.getPlanes()[0].getBuffer();
        //ByteBuffer uBuffer = image.getPlanes()[1].getBuffer();
        //ByteBuffer vBuffer = image.getPlanes()[2].getBuffer();

        int ySize = yBuffer.capacity();
        //int uSize = uBuffer.capacity();
        //int vSize = vBuffer.capacity();
        //LogUtils.i( "YUV_420_888toNV21 total size is " + (ySize + uSize + vSize) + " ySize " + ySize + " uSize " + uSize + " vSize " + vSize);

        int width = image.getWidth();
        int height = image.getHeight();
        // 申请最终结果nv21数组
        byte[] nv21 = new byte[width * height * 3 / 2];
        // 先取y通道数据，直接拷贝即可
        yBuffer.get(nv21, 0, ySize);
        // vuvuvuvu
        // vBuffer.get(nv21, ySize, vSize);
        // uBuffer.get(nv21, ySize + vSize, uSize);

        return nv21;
    }

    private int excut = 0;
    private void decode_huawei(YuvImage yuv) {
        excut++;
        Log.d(TAG, ">>>>>>>>>>> DECODEHUAWEI thread id: " + Thread.currentThread().getId() + " thread name: " + Thread.currentThread().getName() + " excut: " + excut + " <<<<<<<<<<");
        HmsScanFrame frame = new HmsScanFrame(yuv);
        // “QRCODE_SCAN_TYPE”和“PDF417_SCAN_TYPE”表示只扫描QR和PDF417的码
        HmsScanFrameOptions option = new HmsScanFrameOptions.Creator().setHmsScanTypes(HmsScan.ALL_SCAN_TYPE).setMultiMode(false).setParseResult(true).setPhotoMode(true).create();
        HmsScanResult result = ScanUtil.decode(context, frame, option);
        HmsScan[] hmsScans = result.getHmsScans();
        // 扫码成功时处理解码结果
        if (hmsScans != null && hmsScans.length > 0 && !TextUtils.isEmpty(hmsScans[0].getOriginalValue())) {
            Log.d(TAG, ">>>>>>>>>>> DECODEHUAWEI success，excut: " + excut + " <<<<<<<<<<");
            // 展示扫码结果
            isDecodeSuccess = true;
            int scanType = hmsScans[0].getScanType();
            String codeFormat = "";
            if (scanType == HmsScan.QRCODE_SCAN_TYPE) {
                codeFormat = "QR code";
            } else if (scanType == HmsScan.AZTEC_SCAN_TYPE) {
                codeFormat = "AZTEC code";
            } else if (scanType == HmsScan.DATAMATRIX_SCAN_TYPE) {
                codeFormat = "DATAMATRIX code";
            } else if (scanType == HmsScan.PDF417_SCAN_TYPE) {
                codeFormat = "PDF417 code";
            } else if (scanType == HmsScan.CODE93_SCAN_TYPE) {
                codeFormat = "CODE93";
            } else if (scanType == HmsScan.CODE39_SCAN_TYPE) {
                codeFormat = "CODE39";
            } else if (scanType == HmsScan.CODE128_SCAN_TYPE) {
                codeFormat = "CODE128";
            } else if (scanType == HmsScan.EAN13_SCAN_TYPE) {
                codeFormat = "EAN13 code";
            } else if (scanType == HmsScan.EAN8_SCAN_TYPE) {
                codeFormat = "EAN8 code";
            } else if (scanType == HmsScan.ITF14_SCAN_TYPE) {
                codeFormat = "ITF14 code";
            } else if (scanType == HmsScan.UPCCODE_A_SCAN_TYPE) {
                codeFormat = "UPCCODE_A";
            } else if (scanType == HmsScan.UPCCODE_E_SCAN_TYPE) {
                codeFormat = "UPCCODE_E";
            } else if (scanType == HmsScan.CODABAR_SCAN_TYPE) {
                codeFormat = "CODABAR";
            }

            Message message = Message.obtain();
            message.what = 1000;
            Bundle bundle = new Bundle();
            bundle.putParcelable(ScanByHWActivity.BUNDLE_KEY, hmsScans[0]);
            message.setData(bundle);
            ScanByHWActivity.send_message(message);
        }
    }


    private boolean isDecodeSuccess = false;
    private class OnImageAvailableListenerImpl implements ImageReader.OnImageAvailableListener {
        private byte[] y;
        private byte[] u;
        private byte[] v;
        private ReentrantLock lock = new ReentrantLock();

        @Override
        public void onImageAvailable(ImageReader reader) {
            Image image = reader.acquireNextImage();
            // Y:U:V == 4:2:2
            if (camera2Listener != null && image != null && image.getFormat() == ImageFormat.YUV_420_888) {
//                Image.Plane[] planes = image.getPlanes();
//                // 加锁确保y、u、v来源于同一个Image
//                lock.lock();
//                // 重复使用同一批byte数组，减少gc频率
//                if (y == null) {
//                    y = new byte[planes[0].getBuffer().limit() - planes[0].getBuffer().position()];
//                    u = new byte[planes[1].getBuffer().limit() - planes[1].getBuffer().position()];
//                    v = new byte[planes[2].getBuffer().limit() - planes[2].getBuffer().position()];
//                }
//                if (image.getPlanes()[0].getBuffer().remaining() == y.length) {
//                    planes[0].getBuffer().get(y);
//                    planes[1].getBuffer().get(u);
//                    planes[2].getBuffer().get(v);
//                    camera2Listener.onPreview(y, u, v, mPreviewSize, planes[0].getRowStride());
//                }

                Log.d(TAG, ">>>>>>>>>>>> IMAGE decode tid: " + android.os.Process.myTid() + " " + Thread.currentThread().getId() + " " + Thread.currentThread().getName() + " <<<<<<<<<<");
                if(!isDecodeSuccess){
                    YuvImage yuv = new YuvImage(YUV_420_888toNV21_Y(image), ImageFormat.NV21, mPreviewSize.getWidth(), mPreviewSize.getHeight(), null);
                    image.close();
                    decode_huawei(yuv);
                }else{
                    image.close();
                }

//                lock.unlock();
            }

            if(image != null){
                image.close();
            }
        }
    }
}
