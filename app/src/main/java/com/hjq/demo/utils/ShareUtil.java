package com.hjq.demo.utils;


import android.content.Context;
import android.content.SharedPreferences;

public class ShareUtil {
	
	private SharedPreferences sp;
	private SharedPreferences.Editor editor;
	private static String spName = "AF_Share";

	/**
	 * 构造方法
	 * @param context
	 */
	public ShareUtil(Context context) {
		sp = context.getSharedPreferences(spName, context.MODE_PRIVATE);
		editor = sp.edit();
	}
	
	/**
	 * 保存
	 * @param key
	 * @param value
	 * @return
	 */
	public ShareUtil put(String key,Object value){
		if (value instanceof Boolean) {
            editor.putBoolean(key, (Boolean) value).commit();
        } else if (value instanceof String) {
            editor.putString(key, (String) value).commit();
        } else if (value instanceof Integer || value instanceof Byte) {
            editor.putInt(key, (Integer) value).commit();
        } else if (value instanceof Float) {
            editor.putFloat(key, (Float) value).commit();
        } else if (value instanceof Long) {
            editor.putLong(key, (Long) value).commit();
        }
        return this;
	}
	
	/**
	 * 取值
	 * @param key
	 * @param defValue
	 * @return
	 */
	public String getString(String key, String defValue) {
        return sp.getString(key, defValue);
    }

    public int getInt(String key, int defValue) {
        return sp.getInt(key, defValue);
    }

    public boolean getBoolean(String key, boolean defValue) {
        return sp.getBoolean(key, defValue);
    }

    /**
     * 清空所有
     * @return
     */
    public ShareUtil clear(){
    	editor.clear();
    	return this;
    }
    
    /**
     * 移除key-value
     * @param key
     * @return
     */
    public ShareUtil remove(String key){
    	editor.remove(key);
    	return this;
    }

}
