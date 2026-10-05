package com.hjq.demo.ui.activity;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.RemoteException;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.common.api.ResolvableApiException;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.gyf.immersionbar.ImmersionBar;
import com.hjq.base.BaseDialog;
import com.hjq.demo.R;
import com.hjq.demo.app.AppActivity;
import com.hjq.demo.ui.dialog.WaitDialog;
import com.hjq.demo.utils.AFUtils;
import com.hjq.demo.utils.GmsUtil;
import com.hjq.demo.utils.LocationHelper;
import com.hjq.demo.utils.LocationUtils;
import com.hjq.demo.utils.LogUtils;
import com.hjq.demo.utils.TouchHelper;
import com.king.location.ILocationClient;
import com.king.location.LocationClient;
import com.king.location.LocationOption;
import com.szanfu.sdk.api.ISdkServiceManager;
import com.szanfu.sdk.api.constant.ServiceID;
import com.szanfu.sdk.api.emv.IEmvBinderService;
import com.szanfu.sdk.api.system.ISystemBinderService;

import java.text.SimpleDateFormat;

import timber.log.Timber;

public final class AboutActivity extends AppActivity implements View.OnClickListener {
    private static final String TAG = "AboutActivity";
    private ISystemBinderService mSystemBinderService;
    private IEmvBinderService mEmvBinderService;
    private TextView txtBootVer;
    private TextView txtFirmwareVer;
    private TextView txtHardwareVer;
    private TextView txtCompDate;

    private Button button_gps;
    private Button button_network;

    private TextView tv_system, tv_app_version, tv_dev_sn;
    private TextView tv_emv, tv_core_server, tv_sdk_service;

    private LocationUtils locationUtils;

//    private FusedLocationProviderClient fusedLocationClient;

    private LocationHelper locationHelper;

    private static boolean checkGms;

    private static final int PLAY_SERVICES_RESOLUTION_REQUEST = 9000;
    private Handler handler = new Handler();


    @Override
    protected int getLayoutId() {
        return R.layout.about_activity;
    }

    @Override
    protected void initView() {
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                int screenWidth = getResources().getDisplayMetrics().widthPixels;
                int screenHeight = getResources().getDisplayMetrics().heightPixels;
                float centerX = screenWidth / 1.2f;
                float centerY = screenHeight / 1.8f;
                // 在屏幕中心位置模拟点击
                TouchHelper.simulateFullTouch(AboutActivity.this, centerX, centerY);
            }
        }, 200);
        txtBootVer = findViewById(R.id.txtBootVer);
        txtFirmwareVer = findViewById(R.id.txtFirmwareVer);
        txtHardwareVer = findViewById(R.id.txtHardwareVer);
        txtCompDate = findViewById(R.id.txtCompileDate);

        tv_system = findViewById(R.id.tv_system);
        tv_app_version = findViewById(R.id.tv_app_version);
        tv_dev_sn = findViewById(R.id.tv_dev_sn);
        tv_emv = findViewById(R.id.tv_emv);
        tv_core_server = findViewById(R.id.tv_core_service);
        tv_sdk_service = findViewById(R.id.tv_sdk_service);

        button_gps = findViewById(R.id.button_gps);
        button_gps.setOnClickListener(this);
        button_network = findViewById(R.id.button_network);
        button_network.setOnClickListener(this);

//        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);


    }

    @Override
    protected void initData() {
        checkGms = GmsUtil.isGmsAvailable(this);
        LogUtils.e("是否支持GMS: " + checkGms);
        // 初始化 LocationHelper
        locationHelper = new LocationHelper(this, AboutActivity.this);
        if (checkGms) {
            checkGooglePlayServices();
            LogUtils.i("initData: 启动位置更新....");
            // 启动位置更新
            locationHelper.startLocationUpdates(1000, 1000, new LocationHelper.LocationUpdateListener() {
                @Override
                public void onLocationUpdated(Location location) {
                    LogUtils.e("locationHelper 经度 Lat: " + location.getLatitude() + ", 纬度 Lon: " + location.getLongitude());
                }
            });
        } else {
            LogUtils.e("不支持支持GMS 网络定位");
            button_network.setVisibility(View.GONE);
        }

/*        Priority.PRIORITY_HIGH_ACCURACY
        描述：使用高精度模式进行定位。通常结合 GPS、Wi-Fi 和移动网络来提供最准确的位置。
        适用场景：适合需要高精度定位的应用，例如导航应用。

        Priority.PRIORITY_BALANCED_POWER_ACCURACY
        描述：平衡电量消耗和定位精度，使用 Wi-Fi 和移动网络进行定位，而不会主动使用 GPS。
        适用场景：适合需要较为准确定位但对电量要求较高的应用，例如社交媒体应用。

        Priority.PRIORITY_LOW_POWER
        描述：优先考虑低功耗，通常只使用移动网络进行粗略定位。
        适用场景：适合对定位精度要求不高但希望最大限度节省电量的应用，例如天气应用。

        Priority.PRIORITY_PASSIVE
        描述：不会主动发起位置更新，而是接收其他应用或系统的定位请求。这种模式几乎不消耗电量。
        适用场景：适合需要后台位置更新但不需要主动定位的应用，例如地理围栏触发器。*/

        //intervalMillis: 定位更新的间隔时间
        //minUpdateIntervalMillis: 最快更新间隔，单位是毫秒
       /* LocationRequest locationRequest = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 60000)
                .setMinUpdateIntervalMillis(50000)
                .build();

        LocationCallback locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                if (locationResult == null) {
                    Log.e("Location", "onLocationResult:  Error.....");
                    return;
                }
                for (Location location : locationResult.getLocations()) {
                    // 处理每个位置更新
                    Log.e("Location", "经度 Lat: " + location.getLatitude() + ", 纬度 Lon: " + location.getLongitude());
                }
            }
        };

        // 开始位置更新
        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper());*/

    }


    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.button_gps:
                LogUtils.e("onClick: button_gps");
                getGPSLocation();
                break;
            case R.id.button_network:
                LogUtils.e("onClick: button_network");
                retryCount = 0;
                getNetworkLocation();
                break;
        }
    }

    @NonNull
    @Override
    protected ImmersionBar createStatusBarConfig() {
        return super.createStatusBarConfig()
                // 默认状态栏字体颜色为亮色
                .statusBarDarkFont(false);
    }

    @Override
    public void onServiceConnected(ISdkServiceManager sdkServiceManager) {
        try {
            mSystemBinderService = ISystemBinderService.Stub.asInterface(sdkServiceManager.getService(ServiceID.SERVICE_ID_SYSTEM));
            mEmvBinderService = IEmvBinderService.Stub.asInterface(sdkServiceManager.getService(ServiceID.SERVICE_ID_EMV));
            readSpVersion();
            getAppVersion();
            tv_system.setText(AFUtils.getSystemVersion());
            tv_app_version.setText(AFUtils.getVersionName(AboutActivity.this));
            tv_dev_sn.setText(mSystemBinderService.getSN());

            String emvMsg = mEmvBinderService.EmvKernelGetVersion();
            LogUtils.e("--------------emvMsg: " + emvMsg);
            if(!TextUtils.isEmpty(emvMsg)) {
                String[] emvData = emvMsg.split(";");
                tv_emv.setText(emvData[0]);
            }

        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    private void readSpVersion() {
        try {
            byte[] spBootVer = mSystemBinderService.readSpVersion(0);
            String bootVer = spBootVer == null ? "null" : new String(spBootVer);
            txtBootVer.setText(bootVer);
            Timber.tag(TAG).d("---------- bootVer is : %s --------------", bootVer);

            byte[] spFirmwareVer = mSystemBinderService.readSpVersion(1);
            String firmwareVer = spFirmwareVer == null ? "null" : new String(spFirmwareVer);
            txtFirmwareVer.setText(firmwareVer);
            Timber.tag(TAG).d("-------------- firmwareVer is : %s ----------", firmwareVer);

            byte[] spHardwareVer = mSystemBinderService.readSpVersion(2);
            String hardwareVer = spHardwareVer == null ? "null" : new String(spHardwareVer);
            txtHardwareVer.setText(hardwareVer);
            Timber.tag(TAG).d("------------- hardwareVer is : %s -----------", hardwareVer);

            byte[] spCompDate = mSystemBinderService.readSpVersion(3);
            String compDate = spCompDate == null ? "null" : new String(spCompDate);
            txtCompDate.setText(compDate);
            Timber.tag(TAG).d("------------ compDate is : %s -------------", compDate);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    private void getAppVersion() {
        try {
            PackageManager packageManager = getPackageManager();
            String corePackName = "com.szanfu.core.server";
            String sdkPackName = "com.szanfu.sdk.service";

            PackageInfo info = packageManager.getPackageInfo(corePackName, 0);
            String coreName = info.versionName;
            tv_core_server.setText(coreName);
            LogUtils.e("----------coreName: " + coreName);

            info = packageManager.getPackageInfo(sdkPackName, 0);
            String sdkName = info.versionName;
            tv_sdk_service.setText(sdkName);
            LogUtils.e("----------sdkName: " + sdkName);

        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }

    }

    /**
     * 通过GPS获取定位信息
     */
    public void getGPSLocation() {
        Location gps = LocationUtils.getGPSLocation(this);
        if (gps == null) {
            //设置定位监听，因为GPS定位，第一次进来可能获取不到，通过设置监听，可以在有效的时间范围内获取定位信息
            LocationUtils.addLocationListener(this, LocationManager.GPS_PROVIDER, new LocationUtils.ILocationListener() {
                @Override
                public void onSuccessLocation(Location location) {
                    if (location != null) {
                        Toast.makeText(AboutActivity.this, "gps onSuccessLocation location:  lat==" + location.getLatitude() + "     lng==" + location.getLongitude(), Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(AboutActivity.this, "gps location is null", Toast.LENGTH_SHORT).show();
                    }
                }
            });
        } else {
            Toast.makeText(this, "gps location: lat==" + gps.getLatitude() + "  lng==" + gps.getLongitude(), Toast.LENGTH_SHORT).show();
        }
    }


    private int retryCount = 0;
    // 尝试获取5次
    private static final int MAX_RETRY_COUNT = 5;
    // 每次延迟2秒
    private static final int RETRY_DELAY = 2000;

    private BaseDialog mWaitDialog;

    /**
     * 通过网络等获取定位信息
     */
    private void getNetworkLocation() {
        if (mWaitDialog != null) {
            mWaitDialog.dismiss();
        }
        mWaitDialog = new WaitDialog.Builder(AboutActivity.this)
                .setMessage(getString(R.string.getting_location))
                .setCancelable(false)
                .create();
        mWaitDialog.show();

        if (checkGms) {
            Location net = LocationUtils.getNetWorkLocation(this);
            if (net == null) {
                if (retryCount < MAX_RETRY_COUNT) {
                    retryCount++;
                    //Toast.makeText(this, "net location is null, retrying...", Toast.LENGTH_SHORT).show();
                    handler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            getNetworkLocation();
                        }
                    }, RETRY_DELAY);
                } else {
                    // 达到重试次数上限
                    LogUtils.e("Failed to get net location after 5 attempts");
                    Toast.makeText(this, getString(R.string.unable_locate), Toast.LENGTH_SHORT).show();
                    mWaitDialog.dismiss();


                    Intent launchIntent = getContext().getPackageManager().getLaunchIntentForPackage("com.google.android.apps.maps");
                    if (launchIntent != null) {
                        getContext().startActivity(launchIntent);
                    } else {
                        // 如果目标应用未安装，可以处理逻辑或提示用户安装
                        Toast.makeText(getContext(), "目标应用未安装", Toast.LENGTH_LONG).show();
                        //onDetectFailed(getString(R.string.open_finger_test));
                    }
                }
            } else {
                mWaitDialog.dismiss();
                LogUtils.e("getNetworkLocation:  " + "Latitude:" + net.getLatitude() + "  Longitude:" + net.getLongitude());
                Toast.makeText(this, "Latitude:" + net.getLatitude() + "  Longitude:" + net.getLongitude(), Toast.LENGTH_SHORT).show();
                retryCount = 0;
            }
        }

    }

    @Override
    protected void onStop() {
        super.onStop();
        // 停止位置更新
        locationHelper.stopLocationUpdates();
        if (mWaitDialog != null) {
            mWaitDialog.dismiss();
        }
    }


    /**
     * 检查 Google Play 服务
     *
     * @return true 服务已启用，服务未启用
     */
    private boolean checkGooglePlayServices() {
        LogUtils.i("checkGooglePlayServices:检查 Google Play 服务 ");
        GoogleApiAvailability googleApiAvailability = GoogleApiAvailability.getInstance();
        int resultCode = googleApiAvailability.isGooglePlayServicesAvailable(this);

        if (resultCode != ConnectionResult.SUCCESS) {
            if (googleApiAvailability.isUserResolvableError(resultCode)) {
                // Google Play 服务不可用，但可以修复，提示用户更新
                googleApiAvailability.getErrorDialog(AboutActivity.this, resultCode, PLAY_SERVICES_RESOLUTION_REQUEST).show();
                LogUtils.e("Google Play 服务不可用，但可以修复，提示用户更新.");
            } else {
                // Google Play 服务不可用，且不可修复
                LogUtils.e("Google Play 服务不可用，且不可修复.");
                finish();
            }
            return false;
        }
        LogUtils.i("checkGooglePlayServices: 服务启用成功!");
        return true;
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == PLAY_SERVICES_RESOLUTION_REQUEST) {
            // 再次检查 Google Play 服务是否可用
            if (resultCode == RESULT_OK) {
                checkGooglePlayServices();
            }
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

}