package com.hjq.demo.camera2;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
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
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.text.TextUtils;
import android.util.Log;
import android.util.Size;
import android.view.Surface;
import android.view.TextureView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.core.app.ActivityCompat;

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

@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
public class CameraHelperHW {
    private static final String TAG = CameraHelperHW.class.getSimpleName();
    private Activity context;
    private CameraDevice cameraDevice;
    private ImageReader imageReader;
//    private SurfaceView surface;
    private TextureView textureView;
//    private SurfaceHolder surfaceHolder;
    private CameraManager cameraManager; //摄像头管理
    private Handler cameraHandler; //后台处理图片传输帧
    private CameraCaptureSession cameraCaptureSession;//控制摄像头预览或拍照
    private HandlerThread handlerThread;
    private Size mPreviewSize;

    private Point maxPreviewSize;
    private Point minPreviewSize;

    private int rotation;

    CameraDevice.StateCallback deviceCallback; //摄像头监听
    CameraCaptureSession.CaptureCallback captureCallback; // 预览拍照监听

    private static final int MAX_PREVIEW_WIDTH = 960;//960;//Max preview width that is guaranteed by Camera2 API
    private static final int MAX_PREVIEW_HEIGHT = 720;//720;//Max preview height that is guaranteed by Camera2 API
    private boolean isDecodeSuccess = false;
    public CameraHelperHW() {
    }

//    public CameraHelperHW(Activity mainActivity, SurfaceView surface) {
//        this.surface = surface;
//        this.context = mainActivity;
//
//        initData();
//    }

    public CameraHelperHW(Activity mainActivity, TextureView textureView,Point max,Point min,int rotation){
        this.context = mainActivity;
        this.textureView = textureView;
        this.maxPreviewSize = max;
        this.minPreviewSize = min;
        this.rotation = rotation;
        initData();
    }

    //为摄像头开一个线程
    private void startHandler(){
        handlerThread = new HandlerThread("camera");
        handlerThread.start();
        cameraHandler = new Handler(handlerThread.getLooper());//handler与线程进行绑定
    }

    //关闭线程7
    private void destoryHandler(){
        if (handlerThread == null) {
            return;
        }
        handlerThread.quitSafely();
        try {
            handlerThread.join(1);
        } catch (InterruptedException e) {
            e.printStackTrace();
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

    private void get_camera_all_resloutions(CameraManager cameraManager) {
        cameraManager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
        try {
            for (final String cameraId : cameraManager.getCameraIdList()) {
                CameraCharacteristics cameraCharacteristics = cameraManager.getCameraCharacteristics(cameraId);
                StreamConfigurationMap streamConfigurationMap = cameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                Size[] sizes = streamConfigurationMap.getOutputSizes(ImageFormat.YUV_420_888); //获取拍照尺寸
                //Size[] sizes = streamConfigurationMap.getOutputSizes(SurfaceTexture.class); //获取预览尺寸

                /**
                 * 循环40次,让宽度范围从最小逐步增加,找到最符合屏幕宽度的分辨率,
                 * 你要是不放心那就增加循环,肯定会找到一个分辨率,不会出现此方法返回一个null的Size的情况
                 * ,但是循环越大后获取的分辨率就越不匹配
                 */
//				for (int j = 1; j < 41; j++) {
                for (int i = 0; i < sizes.length; i++) { //遍历所有Size
                    Size itemSize = sizes[i];
                    LogUtils.e( "当前itemSize 宽=" + itemSize.getWidth() + "高=" + itemSize.getHeight());
                }
//				}
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
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

    private final TextureView.SurfaceTextureListener mSurfaceTextureListener = new TextureView.SurfaceTextureListener() {

        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture texture, int width, int height) {
            LogUtils.i( "onSurfaceTextureAvailable: ");
            initCamera();
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture texture, int width, int height) {
            LogUtils.i( "onSurfaceTextureSizeChanged: ");
//            configureTransform(width, height);
        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture texture) {
            LogUtils.i( "onSurfaceTextureDestroyed: ");
            closeCamera();
            return true;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture texture) {
        }

    };


    public void initData() {
        cameraManager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);

        get_camera_all_resloutions(cameraManager);

        startHandler();

        textureView.setSurfaceTextureListener(mSurfaceTextureListener);

        //imageReader通过将得到的图片存放在队列中，再取出来进行操作
        //队列满了就不再放入新的图片，设置图片队列大小为10
        imageReader = ImageReader.newInstance(MAX_PREVIEW_WIDTH,MAX_PREVIEW_HEIGHT, ImageFormat.YUV_420_888,10);

        ImageReader.OnImageAvailableListener readerListener = new ImageReader.OnImageAvailableListener() {
            @Override
            public void onImageAvailable(ImageReader reader) {
                Log.d(TAG, ">>>>>>>>>>>> IMAGE decode tid: " + android.os.Process.myTid() + " " + Thread.currentThread().getId() + " " + Thread.currentThread().getName() + " <<<<<<<<<<");
                Image image = null;
                image = reader.acquireLatestImage();
                if(image == null){
                    return;
                }

                if(!isDecodeSuccess){
                    YuvImage yuv = new YuvImage(YUV_420_888toNV21_Y(image), ImageFormat.NV21, MAX_PREVIEW_WIDTH,MAX_PREVIEW_HEIGHT, null);
                    image.close();
                    decode_huawei(yuv);
                }else{
                    image.close();
                }
            }
        };
        imageReader.setOnImageAvailableListener(readerListener, cameraHandler);

        deviceCallback = new CameraDevice.StateCallback() {
            @Override
            public void onOpened(@NonNull CameraDevice camera) {
                cameraDevice = camera;
                takePreview();
            }

            @Override
            public void onDisconnected(@NonNull CameraDevice camera) {
                closeCamera();
            }

            @Override
            public void onError(@NonNull CameraDevice camera, int error) {
                closeCamera();
                Toast.makeText(context,"摄像头打开错误",Toast.LENGTH_SHORT).show();
            }
        };

        captureCallback = new CameraCaptureSession.CaptureCallback() {
            @Override
            public void onCaptureStarted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, long timestamp, long frameNumber) {
                super.onCaptureStarted(session, request, timestamp, frameNumber);
            }
        };

//        surfaceHolder = surface.getHolder();
//        surfaceHolder.addCallback(new SurfaceHolder.Callback() {
//            @Override
//            public void surfaceCreated(@NonNull SurfaceHolder holder) {
//                initCamera();
//            }
//
//            @Override
//            public void surfaceChanged(@NonNull SurfaceHolder holder, int format, int width, int height) {
//            }
//
//            @Override
//            public void surfaceDestroyed(@NonNull SurfaceHolder holder) {
//                closeCamera();
//            }
//        });
    }


    //使用后置摄像头
    public void initCamera() {
        String cameraId = "" + CameraCharacteristics.LENS_FACING_FRONT ;//得到后摄像头编号

        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        try {
//            setUpCameraOutputs(cameraManager,cameraId);
//            configureTransform(textureView.getWidth(),textureView.getHeight());
            cameraManager.openCamera(cameraId, deviceCallback, cameraHandler);
        } catch (CameraAccessException e) {
            Toast.makeText(context,"cameraManager访问摄像头失败",Toast.LENGTH_SHORT).show();
            LogUtils.e("cameraManager访问摄像头失败");
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
        if (null == textureView || null == mPreviewSize) {
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
//        LogUtils.i( "configureTransform: " + getCameraOri(rotation, mCameraId) + "  " + rotation * 90);
        textureView.setTransform(matrix);
    }


    private void setUpCameraOutputs(CameraManager cameraManager,String specificCameraId) {
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
        }
    }

    private boolean configCameraParams(CameraManager manager, String cameraId) throws CameraAccessException {
        CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);

        StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        if (map == null) {
            return false;
        }
        mPreviewSize = getBestSupportedSize(new ArrayList<Size>(Arrays.asList(map.getOutputSizes(SurfaceTexture.class))));
        return true;
    }

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
//                LogUtils.e( msg);
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



    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public String[] getCameras() throws CameraAccessException {
        String[] cameras = new String[0];
        cameras = cameraManager.getCameraIdList();
        return cameras;
    }

    //开启预览
    public void takePreview() {
        try {
            SurfaceTexture texture = textureView.getSurfaceTexture();
            assert texture != null;

//            texture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());
            texture.setDefaultBufferSize(1920, 1080);
            // This is the output Surface we need to start preview.
            Surface surface = new Surface(texture);

            // 创建一个新的capture
            CaptureRequest.Builder previewRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            //将预览数据传递
            previewRequestBuilder.addTarget(imageReader.getSurface());
            previewRequestBuilder.addTarget(surface);
            // 自动对焦
            //previewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            previewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF); // 定焦
            previewRequestBuilder.set(CaptureRequest.LENS_FOCUS_DISTANCE, 4f); // 焦距，屈光度 1/N Meter
            // 打开闪光灯
            previewRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);

            cameraDevice.createCaptureSession(Arrays.asList(surface, imageReader.getSurface()), new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession session) {
                    cameraCaptureSession = session;

                    CaptureRequest captureRequest = previewRequestBuilder.build();
                    try {
                        cameraCaptureSession.setRepeatingRequest(captureRequest, captureCallback, cameraHandler);
                    } catch (CameraAccessException e) {
                        Toast.makeText(context,"cameraCaptureSession访问摄像头失败",Toast.LENGTH_SHORT).show();
                        LogUtils.e("cameraCaptureSession访问摄像头失败");
                    }
                }
                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                }
            },null);
        } catch (CameraAccessException e) {
            Toast.makeText(context,"CaptureRequest.Builder访问摄像头失败",Toast.LENGTH_SHORT).show();
            LogUtils.e("CaptureRequest.Builder访问摄像头失败");
        }
    }

    //关闭摄像头
    public void closeCamera(){
        if (cameraDevice==null) {
            return;
        }
        cameraDevice.close();
        cameraDevice = null;
    }

    public void destory(){
        closeCamera();
        destoryHandler();
    }
}

