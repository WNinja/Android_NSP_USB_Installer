package wninja.switchusbtool.utils;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.widget.Toast;

public class ToastUtil {
    private static Toast mToast;
    private static ToastHandler handler;

    private static class ToastHandler extends Handler{
        @Override
        public void handleMessage(Message msg){
            mToast.setText((String)msg.obj);
            mToast.setDuration(msg.what);
            mToast.show();
        }
    }

    public static void init(Context context){
        mToast = Toast.makeText(context,"",Toast.LENGTH_SHORT);
        handler = new ToastHandler();
    }

    public static void show(String msg){
        show(msg,Toast.LENGTH_SHORT);
    }

    public static void showLong(String msg){
        show(msg,Toast.LENGTH_LONG);
    }

    private static void show(String msg, int time_duration){
        if(isOnMainThread()){
            mToast.setText(msg);
            mToast.setDuration(time_duration);
            mToast.show();
        }else {
            handler.sendMessage( handler.obtainMessage(time_duration,msg) );
        }
    }

    private static boolean isOnMainThread(){
        return Thread.currentThread() == Looper.getMainLooper().getThread();
    }
}
