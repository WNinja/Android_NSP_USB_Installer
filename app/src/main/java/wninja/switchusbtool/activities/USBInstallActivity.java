package wninja.switchusbtool.activities;

import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.os.Environment;
import android.support.v7.app.AppCompatActivity;
import android.text.method.ScrollingMovementMethod;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.io.File;
import java.io.FileOutputStream;

import wninja.switchusbtool.R;
import wninja.switchusbtool.Tool.NSPUSBSender;
import wninja.switchusbtool.broadcast.SwitchPermissionReceiver;
import wninja.switchusbtool.broadcast.UsbStateReceiver;
import wninja.switchusbtool.interfaces.nspActivityCallback;

public class USBInstallActivity extends AppCompatActivity implements View.OnClickListener,nspActivityCallback {
    private static final String TAG = "USBInstallActivity";

    private NSPUSBSender nspUsbSender;

    private TextView tv;
    private ProgressBar percentBar;
    private File logFile;

    private final SwitchPermissionReceiver permissionReceiver = new SwitchPermissionReceiver();
    private final UsbStateReceiver usbStateReceiver = new UsbStateReceiver();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_nspinstall);

        createFolder();
        initView();
        initLog();
        initUsbSender();
        initBroadCastReceiver();
    }

    private void initUsbSender(){
        nspUsbSender = new NSPUSBSender(this);
        //if started by notification when switch attached，get device from intent extra parcel
        Intent intent = getIntent();
        try{
            UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
            if (device != null && NSPUSBSender.isSwitch(device)){
                nspUsbSender.setDevice(device);
                showLog("on create we get the switch from intent");
                nspUsbSender.updateState(true);
            }
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    private void initBroadCastReceiver(){
        IntentFilter filter = new IntentFilter(NSPUSBSender.ACTION_USB_PERMISSION);
        registerReceiver(permissionReceiver, filter);
        permissionReceiver.setCallback(nspUsbSender);
        permissionReceiver.setUICallback(this);

        IntentFilter filter2 = new IntentFilter(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        filter2.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        registerReceiver(usbStateReceiver,filter2);
        usbStateReceiver.setSenderCallback(nspUsbSender);
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
            showLog(e.toString());
            e.printStackTrace();
        }
        File file = new File(path);
        if(!file.exists()){
            boolean created = false;
            try {
                created = file.mkdir();
            }catch (Exception e){
                showLog(e.toString());
                e.printStackTrace();
            }
            if(!created){
                showLog("create folder failed");
            }else {
                showLog("create folder success");
            }
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        showLog("on new Intent");
        UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
        if (device != null && NSPUSBSender.isSwitch(device)){
            nspUsbSender.setDevice(device);
        }
    }

    @Override
    public void onClick(View v) {
        switch(v.getId()){
            case R.id.find:
                nspUsbSender.findDevice();
                break;
            case R.id.send:
                nspUsbSender.sendFile();
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
            showLog(e.toString());
            e.printStackTrace();
        }
        File file = new File(path);
        if(!file.exists()){
            boolean created = false;
            try {
                created = file.createNewFile();
            }catch (Exception e){
                showLog(e.toString());
                e.printStackTrace();
            }
            if(!created){
                showLog("create log file failed");
                return false;
            }else {
                showLog("create log file success");
            }
        }
        logFile = file;
        return true;
    }

    @Override
    protected void onDestroy(){
        super.onDestroy();
        nspUsbSender.onDestroy();
        unregisterReceiver(permissionReceiver);
        unregisterReceiver(usbStateReceiver);
    }

    @Override
    public void showLog(String log) {
        if(tv != null){
            String text = tv.getText().toString()+"\n"+log;
            tv.setText(text);
        }

        if(logFile != null){
            if(!logFile.exists() && !initLog()){
                return;
            }
            try{
                FileOutputStream logStream = new FileOutputStream(logFile,true);
                logStream.write(("\n"+log).getBytes());
                logStream.close();
            }catch (Exception e){
                e.printStackTrace();
            }
        }
    }

    @Override
    public void setPercent(int percent) {
        percentBar.setProgress(percent);
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
