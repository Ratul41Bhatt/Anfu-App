package com.hjq.demo.utils;

import android.app.Activity;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;

import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentSender;
import android.location.Location;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;

import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.common.api.ResolvableApiException;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.LocationSettingsResponse;
import com.google.android.gms.location.Priority;
import com.google.android.gms.location.SettingsClient;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.hjq.demo.ui.activity.AboutActivity;

/**
 * gms网络定位
 */
public class LocationHelper {
    private static final String TAG = "LocationHelper";
    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;
    private LocationUpdateListener listener;
    private Context mContext;
    private Activity mActivity;
    private static final int REQUEST_CHECK_SETTINGS = 1001;


    public LocationHelper(Context context, Activity activity) {
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(context);
        this.mContext = context;
        mActivity = activity;
    }

    /**
     * 启动位置更新
     *
     * @param intervalMillis        定位更新的间隔时间， 单位毫秒
     * @param fastestIntervalMillis 最快更新间隔，单位毫秒
     * @param listener              位置数据监听
     */
    public void startLocationUpdates(long intervalMillis, long fastestIntervalMillis, LocationUpdateListener listener) {
        this.listener = listener;
        Log.i(TAG, "startLocationUpdates----->");
        LocationRequest locationRequest = new LocationRequest.Builder(Priority.PRIORITY_LOW_POWER, intervalMillis)
                .setMinUpdateIntervalMillis(fastestIntervalMillis)
                .build();
        Log.i(TAG, "startLocationUpdates:locationRequest--> " + locationRequest.toString());

        // 创建 LocationSettingsRequest.Builder，检查设备设置
        LocationSettingsRequest.Builder builder = new LocationSettingsRequest.Builder()
                .addLocationRequest(locationRequest);

        // 获取 SettingsClient 对象
        SettingsClient client = LocationServices.getSettingsClient(mContext);
        Task<LocationSettingsResponse> task = client.checkLocationSettings(builder.build());

        // 检查设备的定位设置是否满足应用的要求
        task.addOnCompleteListener(new OnCompleteListener<LocationSettingsResponse>() {
            @Override
            public void onComplete(@NonNull Task<LocationSettingsResponse> task) {
                // 所有设置已经满足要求，继续获取位置信息
                try {
                    LocationSettingsResponse response = task.getResult(ApiException.class);
                    Log.i(TAG, "onComplete: 所有设置已经满足要求，继续获取位置信息 ");
                } catch (ApiException exception) {
                    //e.printStackTrace();
                    Log.e(TAG, "onComplete: ApiException====> ");
                    Log.e(TAG, "onComplete:定位设置不满足，弹出提示框请求用户启用定位 getStatusCode->" + exception.getStatusCode());
                    ResolvableApiException resolvable = (ResolvableApiException) exception;
                    try {
                        resolvable.startResolutionForResult(mActivity, REQUEST_CHECK_SETTINGS);
                    } catch (IntentSender.SendIntentException e) {
                        e.printStackTrace();
                    }

                     /*  new AlertDialog.Builder(mContext)
                            .setMessage("请开启设备位置信息功能")
                            .setPositiveButton("去设置", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                                    mContext.startActivity(intent);
                                }
                            })
                            .setNegativeButton("取消", null)
                            .show();*/

                }
            }
        });

        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                if (locationResult == null) {
                    Log.e(TAG, "onLocationResult: Error.....");
                    return;
                }
                for (Location location : locationResult.getLocations()) {
                    if (listener != null) {
                        Log.e(TAG, "location: Success" + location.toString());
                        listener.onLocationUpdated(location);
                    }
                }
            }
        };
        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper());
    }

    public void stopLocationUpdates() {
        if (locationCallback != null) {
            fusedLocationClient.removeLocationUpdates(locationCallback);
        }
    }


    /**
     * 来获取一次位置更新
     *
     * @param listener 获取结果回调
     */
    public void getLastKnownLocation(OnSuccessListener<Location> listener) {
        fusedLocationClient.getLastLocation()
                .addOnSuccessListener(listener)
                .addOnFailureListener(e -> Log.e("LocationHelper", "Failed to get location", e));
    }

    /**
     * 位置更新回调
     */
    public interface LocationUpdateListener {
        void onLocationUpdated(Location location);
    }

}
