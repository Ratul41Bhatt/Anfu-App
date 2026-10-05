package com.hjq.demo.utils;

import android.util.Log;

import java.text.SimpleDateFormat;
import java.util.Date;

public class DateDifferentExample {

    private static final String TAG = "DateDifferentExample";

    /**
     * @param dateStart
     * @param dateStop
     * @return
     */
    public static String getTimeSpan(String dateStart, String dateStop) {

        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

        Date d1 = null;
        Date d2 = null;

        try {
            d1 = format.parse(dateStart);
            d2 = format.parse(dateStop);

            //毫秒ms
            long diff = d2.getTime() - d1.getTime();

            long diffSeconds = diff / 1000 % 60;
            long diffMinutes = diff / (60 * 1000) % 60;
            long diffHours = diff / (60 * 60 * 1000) % 24;
            long diffDays = diff / (24 * 60 * 60 * 1000);
            StringBuffer sbTimeDiff = new StringBuffer();
            sbTimeDiff.setLength(0);
            if (diffDays != 0) {
                sbTimeDiff.append(diffDays + " 天,");
            }
            if (diffHours != 0) {
                sbTimeDiff.append(diffHours + " 小时,");
            }
            if (diffMinutes != 0) {
                sbTimeDiff.append(diffMinutes + " 分钟,");
            }
            if (diffSeconds != 0) {
                sbTimeDiff.append(diffSeconds + " 秒.");
            }
           /* Log.i(TAG, "两个时间相差：");
            Log.i(TAG, diffDays + " 天, ");
            Log.i(TAG, diffHours + " 小时, ");
            Log.i(TAG, diffMinutes + " 分钟, ");
            Log.i(TAG, diffSeconds + " 秒.");*/
            return sbTimeDiff.toString();
        } catch (Exception e) {
            e.printStackTrace();
        }

        return "";
    }
}