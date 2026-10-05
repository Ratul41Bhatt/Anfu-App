package com.hjq.demo.utils;


import java.text.SimpleDateFormat;
import java.util.Date;

public class DateUtil {
    /**
     * 日期格式化
     */
    public static final String DATE_TIME_FORMAT = "yyyy-MM-dd HH:mm:ss";
    //202303290015
    public static final String DATE_TIME_FORMAT_NO = "yyyyMMdd";
    /**
     * 根据时间转换为时间戳
     * @param date
     * @param timestampType 转换类型 0毫秒 1秒
     * @return
     */
    public static long getTimeStamp(Date date,int timestampType)
    {
        long times = date.getTime();
        if (timestampType == 1)
        {
            times = times/1000L;
        }
        return  times;
    }

    /**
     * 时间戳转时间
     * @param timestamp
     * @param timestampType 时间戳格式 0毫秒 1秒
     * @return
     */
    public Date getDateTime(long timestamp,int timestampType)
    {
        if (timestampType == 1)
        {
            //如果时间戳格式是秒，需要江时间戳变为毫秒
            timestamp = timestamp * 1000L;
        }
        Date dateTime = new Date(timestamp);
        return  dateTime;

    }

    /**
     * 格式化传入的时间，将时间转化为指定格式字符串
     * @param date
     * @param format 时间格式，如：yyyy-MM-dd HH:mm:ss SSS 或 yyyy年MM月dd日 HH:mm:ss
     * @return
     */
    public String getDateTimeString(Date date,String format )
    {
        if (format == null || format.length() <=0)
        {
            return  null;
        }
        // 格式化日期
        SimpleDateFormat sdf = new SimpleDateFormat(format);
        String timeString = sdf.format(date);
        return  timeString;
    }

    /**
     * 格式化传入的时间戳，将时间戳转化为指定格式字符串
     * @param timestamp
     * @param format 时间格式，如：yyyy-MM-dd HH:mm:ss SSS 或 yyyy年MM月dd日 HH:mm:ss     *
     * @param timestampType 时间戳格式 0毫秒 1秒
     * @return
     */
    public static String getTimeStampString(long timestamp,String format ,int timestampType)
    {
        if (format == null || format.length() <=0)
        {
            return  null;
        }
        if (timestampType == 1)
        {
            //如果时间戳格式是秒，需要将时间戳变为毫秒
            timestamp = timestamp * 1000L;
        }
        Date dateTime = new Date(timestamp);
        // 格式化日期
        SimpleDateFormat sdf = new SimpleDateFormat(format);
        String timeString = sdf.format(dateTime);
        return  timeString;
    }

    public static void main(String[] args) {
       /* DateUtil dateUtil = new DateUtil();

        //时间生成时间戳
        long currentDate = dateUtil.getTimeStamp(new Date(),0);

        System.out.println("currentDate:"+currentDate);
        //1677137520000
        long timestamp = 1676865429000L;
        String format = "yyyy-MM-dd HH:mm:ss";
        int timestampType =0;
        //时间戳转换时间
        String format_date = dateUtil.getTimeStampString(timestamp,format,timestampType);
        String current_format_date = dateUtil.getTimeStampString(currentDate,format,timestampType);

        System.out.println("format_date:"+format_date);
        System.out.println("current_format_date:"+current_format_date);*/

    }

    /**
     * 获取当天时间戳-毫秒级
     * @return 开始时间，结束时间
     */
    /*public static Map<String,Long> getLocalDateTimeMil(){
        Map<String,Long> currentDateTime = new HashMap<>();
        currentDateTime.clear();
        //获取当天开始及结束时间的时间戳10位秒级、13位毫秒
        //获取当天开始时间
        LocalDateTime today_start = LocalDateTime.of(LocalDate.now(), LocalTime.MIN);//当天零点
        //获取当天结束时间
        LocalDateTime today_end = LocalDateTime.of(LocalDate.now(), LocalTime.MAX);//当天24点

       *//* String td_st_str = today_start.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        String td_ed_str = today_end.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

        //获取秒数  10位
        long start_time = today_start.toInstant(ZoneOffset.ofHours(8)).getEpochSecond();
        long end_time = today_end.toInstant(ZoneOffset.ofHours(8)).getEpochSecond();*//*

        //获取毫秒数 13位
        long start_time_mil = today_start.toInstant(ZoneOffset.of("+8")).toEpochMilli();
        long end_time_mil = today_end.toInstant(ZoneOffset.of("+8")).toEpochMilli();
        currentDateTime.put(ApiKey.START_TIME_MIL,start_time_mil);
        currentDateTime.put(ApiKey.END_TIME_MIL,end_time_mil);
        return  currentDateTime;
    }*/

}