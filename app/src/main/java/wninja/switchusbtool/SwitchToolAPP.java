package wninja.switchusbtool;

import android.app.Application;
import wninja.switchusbtool.utils.ToastUtil;

public class SwitchToolAPP extends Application {

    @Override
    public void onCreate()
    {
        super.onCreate();
        ToastUtil.init(getApplicationContext());
    }

}
