package wninja.switchusbtool.utils;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

public class TimeUtils {
    private static Date date = new Date();
    public static String getTimeString(){
        return getTimeString(System.currentTimeMillis());
    }
    public static String getTimeString(long timeMillis){
        date.setTime(timeMillis);
        DateFormat format = SimpleDateFormat.getInstance();
        return format.format(date);
    }
}
