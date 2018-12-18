package wninja.switchusbtool.broadcast;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;

import wninja.switchusbtool.Tool.USBInstaller;
import wninja.switchusbtool.interfaces.nspActivityCallback;
import wninja.switchusbtool.interfaces.SenderCallback;

public class UsbStateReceiver extends BroadcastReceiver{
    private SenderCallback senderCallback;
    private nspActivityCallback nspCallback;

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if(UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action)){
            log("new usb device attached");
            senderCallback.findDevice();
        }else if(UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)){
            log("a usb device detached");
            UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
            if (device != null && USBInstaller.isSwitch(device)) {
                log("switch disconnected");
                senderCallback.releaseDevice();
            }
        }
    }

    public void setSenderCallback(SenderCallback callback) {
        this.senderCallback = callback;
    }

    public void setUICallback(nspActivityCallback receiver){
        nspCallback = receiver;
    }

    private void log(String log){
        nspCallback.showLog(log);
    }
}
