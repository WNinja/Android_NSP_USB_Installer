package wninja.switchusbtool.interfaces;

import android.hardware.usb.UsbDevice;

public interface SenderCallback {
    void setDevice(UsbDevice device);
    void releaseDevice();
    void updateState(boolean state);
}
