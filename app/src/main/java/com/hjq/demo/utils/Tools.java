package com.hjq.demo.utils;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.BatteryManager;
import android.view.WindowManager;
import android.widget.Toast;

public class Tools {
	public static String MSERVERIP = "111.17.212.35";
	public static int MSERVERPORT = 5000;
	/**
	 * 通用的提示类
	 */
	public static void toastTools(Context context, String msg) {
		Toast.makeText(context, msg, Toast.LENGTH_SHORT).show();
	}

	/**
	 * 缓存屏幕尺寸
	 * 
	 * @param context
	 * @param myShare
	 */
	public static void initScreenSize(Context context, ShareUtil myShare) {
		WindowManager windowManager = (WindowManager) context
				.getSystemService(Context.WINDOW_SERVICE);
		int width = windowManager.getDefaultDisplay().getWidth();
		int height = windowManager.getDefaultDisplay().getHeight();
		myShare.put(Constant.SCREEN_WIDTH, width);
		myShare.put(Constant.SCREEN_HEIGHT, height);
	}

	/**
	 * 根据手机的分辨率从 dp 的单位 转成为 px(像素)
	 */
	public static int dip2px(Context context, float dpValue) {
		final float scale = context.getResources().getDisplayMetrics().density;
		return (int) (dpValue * scale + 0.5f);
	}

	/**
	 * 根据手机的分辨率从 px(像素) 的单位 转成为 dp
	 */
	public static int px2dip(Context context, float pxValue) {
		final float scale = context.getResources().getDisplayMetrics().density;
		return (int) (pxValue / scale + 0.5f);
	}

	/**
	 * 网络检测
	 * 
	 * @param context
	 * @return
	 */
	public static boolean isNetworkConnected(Context context) {
		if (context != null) {
			ConnectivityManager mConnectivityManager = (ConnectivityManager) context
					.getSystemService(Context.CONNECTIVITY_SERVICE);
			NetworkInfo mNetworkInfo = mConnectivityManager
					.getActiveNetworkInfo();
			if (mNetworkInfo != null) {
				return mNetworkInfo.isAvailable();
			}
		}
		return false;
	}

	public static String getSystemTime(String dateStr, String timeStr) {
		SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMddHHmmss",
				Locale.getDefault());
		String formatStr = dateStr + timeStr;
		try {
			Date date = formatter.parse(formatStr);
			formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss",
					Locale.getDefault());
			String systemTime = formatter.format(date);
			return systemTime;
		} catch (ParseException e) {
			return dateStr + timeStr;
		}
	}

	/**
	 * 判断某张表是否存在
	 * 
	 * @param table
	 * @return
	 */
	public static boolean tableIsExist(Context context,String dbName,String table){
    	boolean result = false;
    	SQLiteDatabase db = null;
    	Cursor cursor = null;
    	try {
			db = context.openOrCreateDatabase(dbName, Context.MODE_PRIVATE, null);
			String sql = "select count(*) as c from Sqlite_master where type = 'table' and name = '"+table+"'" ;
			cursor = db.rawQuery(sql, null);
			if(cursor.moveToNext()){
				int count = cursor.getInt(0);
				if (count > 0) {
					result = true;
				}
		
			}
			
					
		} catch (Exception e) {
			// TODO: handle exception
			e.printStackTrace();
		}
    	
    	return result;
    }
	/**
	 * 获取字符串的个数，并以三位数样式返回
	 * @param value
	 * @return
	 */
	public static String getLength(String value){
		String result = "";
		int length = value.length();
		if(length > 99){
			result = length + "";
		}else{
			result = "0" + length;
		}
		return result;
	}
	
	/**
	 * 检测剩余电量
	 * @param context
	 * @return  xx(*100 的整数)
	 */
	public static float getBatteryPct(Context context){
		IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
		Intent batteryStatus = context.registerReceiver(null, ifilter);
		int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
		int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);

		float batteryPct = (level / (float)scale) * 100;
		return batteryPct;


	}
	
	/**
	 * 获取软件版本
	 */
	public static int getAppVersionCode(Context mContext){
		PackageManager packageManager = mContext.getPackageManager();
		try {
			PackageInfo packageInfo = packageManager.getPackageInfo(mContext.getPackageName(), 0);
			int versionCode = packageInfo.versionCode;
			
			
			return versionCode;
		} catch (NameNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		return 0;
	}
	/**
	 * version name
	 * @param context
	 * @return
	 */
	public static String getAppVersionName(Context context){
		PackageManager packageManager = context.getPackageManager();
		try {
			PackageInfo packageInfo = packageManager.getPackageInfo(context.getPackageName(), 0);
			String versionName = packageInfo.versionName;
			
			return versionName;
		} catch (NameNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} 
		
		return null;
		
	}

}
