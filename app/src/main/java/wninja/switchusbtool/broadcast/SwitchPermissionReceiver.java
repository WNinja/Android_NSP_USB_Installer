package wninja.switchusbtool.broadcast;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;

import wninja.switchusbtool.Tool.USBInstaller;
import wninja.switchusbtool.interfaces.nspActivityCallback;
import wninja.switchusbtool.interfaces.SenderCallback;

public class SwitchPermissionReceiver extends BroadcastReceiver{
    private SenderCallback senderCallback;
    private nspActivityCallback nspCallback;

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        log( "onReceive: the action is "+action);
        if (USBInstaller.ACTION_USB_PERMISSION.equals(action)) {
            synchronized (this) {
                UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                    log("get permission");
                    if(device != null && USBInstaller.isSwitch(device)){
                        log("get switch after permission");
                        senderCallback.setDevice(device);
                        senderCallback.updateState(true);
                    }
                }
                else {
                    log("permission denied for device " + device);
                }
            }
        }
    }

    public void setCallback(SenderCallback senderCallback) {
        this.senderCallback = senderCallback;
    }

    public void setUICallback(nspActivityCallback receiver){
        nspCallback = receiver;
    }

    private void log(String log){
        nspCallback.showLog(log);
    }
}
