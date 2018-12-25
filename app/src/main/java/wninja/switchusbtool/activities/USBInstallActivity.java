package wninja.switchusbtool.activities;

import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.text.method.ScrollingMovementMethod;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.io.File;
import java.io.FileOutputStream;
import java.lang.ref.WeakReference;

import wninja.switchusbtool.R;
import wninja.switchusbtool.Tool.USBInstaller;
import wninja.switchusbtool.broadcast.SwitchPermissionReceiver;
import wninja.switchusbtool.broadcast.UsbStateReceiver;
import wninja.switchusbtool.interfaces.nspActivityCallback;
import wninja.switchusbtool.utils.TimeUtils;
import wninja.switchusbtool.utils.ToastUtil;

public class USBInstallActivity extends AppCompatActivity implements View.OnClickListener,nspActivityCallback {
    private static final String TAG = "USBInstallActivity";

    private USBInstaller nspUsbInstaller;

    private TextView tv;
    private ProgressBar percentBar;
    private File logFile;

    private final SwitchPermissionReceiver permissionReceiver = new SwitchPermissionReceiver();
    private final UsbStateReceiver usbStateReceiver = new UsbStateReceiver();

    private mHandler handler;

    private static class mHandler extends Handler{
        private WeakReference<USBInstallActivity> reference;
        static final int MSG_LOG = 1;
        static final int MSG_PERCENT = 2;

        mHandler(USBInstallActivity activity){
            reference = new WeakReference<>(activity);
        }

        @Override
        public void handleMessage(Message msg){
            USBInstallActivity activity = reference.get();
            if(activity == null){
                return;
            }
            switch (msg.what){
                case MSG_LOG:
                    activity.showLog((String)msg.obj);
                    break;
                case MSG_PERCENT:
                    activity.setPercent((int)msg.obj);
                    break;
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_nspinstall);

        handler = new mHandler(this);

        createFolder();
        initView();
        initLog();
        initUsbSender();
        initBroadCastReceiver();
    }

    private void initUsbSender(){
        nspUsbInstaller = new USBInstaller(this);
        //if started by notification when switch attached，get device from intent extra parcel
        Intent intent = getIntent();
        try{
            UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
            if (device != null && USBInstaller.isSwitch(device)){
                nspUsbInstaller.setDevice(device);
                nspUsbInstaller.updateState(true);
            }
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    private void initBroadCastReceiver(){
        IntentFilter filter = new IntentFilter(USBInstaller.ACTION_USB_PERMISSION);
        registerReceiver(permissionReceiver, filter);
        permissionReceiver.setCallback(nspUsbInstaller);
        permissionReceiver.setUICallback(this);

        IntentFilter filter2 = new IntentFilter(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        filter2.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        registerReceiver(usbStateReceiver,filter2);
        usbStateReceiver.setSenderCallback(nspUsbInstaller);
        usbStateReceiver.setUICallback(this);
    }

    private void initView(){
        tv = findViewById(R.id.loglist);
        percentBar = findViewById(R.id.progress);
        tv.setMovementMethod(ScrollingMovementMethod.getInstance());
        findViewById(R.id.find).setOnClickListener(this);
        findViewById(R.id.send).setOnClickListener(this);
    }

    private void createFolder(){
        String path = "";
        try{
            path = Environment.getExternalStorageDirectory().getCanonicalPath() + "/nsp";
        }catch (Exception e){
            e.printStackTrace();
        }
        File file = new File(path);
        if(!file.exists()){
            boolean created = false;
            try {
                created = file.mkdir();
            }catch (Exception e){
                e.printStackTrace();
            }
            if(!created){
                fileLog("create folder failed");
            }else {
                fileLog("create folder success");
            }
        }
    }

    @Override
    public void onClick(View v) {
        switch(v.getId()){
            case R.id.find:
                nspUsbInstaller.findDevice();
                break;
            case R.id.send:
                nspUsbInstaller.sendFile();
                break;
            default :
                break;
        }
    }

    private boolean initLog(){
        String path = "";
        try{
            path = Environment.getExternalStorageDirectory().getCanonicalPath() + "/nsp/log.txt";
        }catch (Exception e){
            e.printStackTrace();
        }
        File file = new File(path);
        if(!file.exists()){
            boolean created = false;
            try {
                created = file.createNewFile();
            }catch (Exception e){
                e.printStackTrace();
            }
            if(!created){
                fileLog("create log file failed");
                return false;
            }else {
                fileLog("create log file success");
            }
        }
        logFile = file;
        return true;
    }

    @Override
    protected void onDestroy(){
        super.onDestroy();
        nspUsbInstaller.onDestroy();
        unregisterReceiver(permissionReceiver);
        unregisterReceiver(usbStateReceiver);
    }

    public void clearLog(){
        tv.setText(getResources().getString(R.string.log));
    }

    @Override
    public void showLog(String log) {
        if(ToastUtil.isOnMainThread()){
            if(tv != null){
                String text = tv.getText().toString()+"\n"+log;
                tv.setText(text);
            }
            fileLog(log);
        }else {
            handler.obtainMessage(mHandler.MSG_PERCENT,log);
        }
    }

    @Override
    public void fileLog(String log){
        String time = TimeUtils.getTimeString();
        if(logFile != null){
            if(!logFile.exists() && !initLog()){
                return;
            }
            try{
                FileOutputStream logStream = new FileOutputStream(logFile,true);
                logStream.write(("["+time+"]"+log+"\n").getBytes());
                logStream.close();
            }catch (Exception e){
                e.printStackTrace();
            }
        }
    }

    @Override
    public void setPercent(int percent) {
        if(ToastUtil.isOnMainThread()){
            percentBar.setProgress(percent);
        }else {
            handler.obtainMessage(mHandler.MSG_PERCENT,percent);
        }
    }

    /*private void setPercentBarVisibility(boolean visible){
        if (percentBar == null ){
            return;
        }
        if(visible){
            percentBar.setVisibility(View.VISIBLE);
        }else {
            percentBar.setVisibility(View.INVISIBLE);
        }
    }*/
}
