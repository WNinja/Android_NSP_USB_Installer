package wninja.switchusbtool.Tool;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.os.Build;
import android.os.Environment;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.Arrays;

import wninja.switchusbtool.R;
import wninja.switchusbtool.activities.USBInstallActivity;
import wninja.switchusbtool.interfaces.SenderCallback;
import wninja.switchusbtool.utils.ByteUtils;
import wninja.switchusbtool.utils.FileUtils;

public class USBInstaller implements SenderCallback {
    public static final String ACTION_USB_PERMISSION = "wninja.switchusbtool.USB_PERMISSION";

    private PendingIntent usbPermissionIntent;
    private USBInstallActivity usbActivity;
    private Resources resources;

    private UsbManager usbManager;
    private UsbDevice switchDevice;
    private UsbInterface switchInterface;
    private UsbDeviceConnection switchConnection;
    private UsbEndpoint usbInPoint,usbOutPoint;

    private File nspFolder = null;
    private String sendFileName = "";
    private int percent = 0;

    public USBInstaller(USBInstallActivity activity){
        usbActivity = activity;
        resources = usbActivity.getResources();
        usbManager = (UsbManager)activity.getSystemService(Context.USB_SERVICE);
        usbPermissionIntent =  PendingIntent.getBroadcast(activity, 0,
                new Intent(USBInstaller.ACTION_USB_PERMISSION), 0);
        setNspFolder();
    }

    //set the state if the switch is connecting
    @Override
    public void updateState(boolean connecting){
        if(connecting){
            if(initConnection())
                log(resources.getString(R.string.switch_connect));
        }else {
            log(resources.getString(R.string.switch_disconnect));
            usbActivity.setPercent(0);
        }
    }

    private void setNspFolder(){
        try{
            String nspPath = Environment.getExternalStorageDirectory().getCanonicalPath()+"/nsp";
            nspFolder = new File(nspPath);
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    @Override
    public void findDevice(){
        if(switchDevice == null){
            try{
                for(UsbDevice dev: usbManager.getDeviceList().values()){
                    if (isSwitch(dev)) {
                        switchDevice = dev;
                        log(resources.getString(R.string.find_switch));
                        break;
                    }
                }
            }catch (Exception e){
                log(e.toString());
                e.printStackTrace();
            }
        }

        if (switchDevice != null && !usbManager.hasPermission(switchDevice)) {
            requestPermission(switchDevice);
        }else {
            log(resources.getString(R.string.cant_find));
        }
        /*Random rdm = new Random();
        usbActivity.setPercent(rdm.nextInt(101));*/
    }

    private boolean initConnection(){
        if (switchDevice == null)
            return false;

        switchInterface = switchDevice.getInterface(0);
        //find the in and out USB point
        for (int i = 0; i < switchInterface.getEndpointCount(); i++){
            UsbEndpoint pt = switchInterface.getEndpoint(i);
            if(pt.getDirection() == UsbConstants.USB_DIR_IN){
                usbInPoint = pt;
            }else if(pt.getDirection() == UsbConstants.USB_DIR_OUT){
                usbOutPoint = pt;
            }
        }
        if(usbInPoint == null || usbOutPoint == null){
            log(resources.getString(R.string.connect_fail));
            return false;
        }
        //open USB connection
        UsbDeviceConnection connection = usbManager.openDevice(switchDevice);
        if(connection != null && connection.claimInterface(switchInterface,true)){
            switchConnection = connection;
            return true;
        }
        log(resources.getString(R.string.connect_fail));
        return false;
    }

    public void sendFile(){
        if(switchConnection == null && !initConnection()){
            log(resources.getString(R.string.no_switch));
            return;
        }

        new Thread(){
            @Override
            public void run(){
                if(nspFolder ==null || !nspFolder.exists() || nspFolder.isFile()){
                    log(resources.getString(R.string.no_folder));
                    return;
                }
                sendFileList(nspFolder);
            }
        }.start();
    }

    private void sendFileList(File nspDir){
        ArrayList<String> fileList = new ArrayList<>();
        int fileNameLen = 0;

        for(File f:nspDir.listFiles()){
            if(f.isFile()){
                if("nsp".equals(FileUtils.getFileSuffix(f))){
                    String fName = f.getName()+ '\n';
                    fileList.add(fName);
                    fileNameLen += fName.length();
                    log(resources.getString(R.string.find_nsp)+fName);
                }
            }
        }

        if(fileList.isEmpty()){
            log(resources.getString(R.string.empty_folder));
            return;
        }

        byte[] buffer;
        buffer = "TUL0".getBytes();//header: Tinfoil USB List 0
        switchConnection.bulkTransfer(usbOutPoint,buffer,4,1000);
        buffer = ByteUtils.int2byteLE(fileNameLen);//file name length
        switchConnection.bulkTransfer(usbOutPoint,buffer,4,1000);
        buffer = new byte[8];//Padding
        switchConnection.bulkTransfer(usbOutPoint,buffer,8,1000);
        for(String name:fileList){//send files
            buffer = name.getBytes();
            switchConnection.bulkTransfer(usbOutPoint,buffer,buffer.length,2000);
        }

        pollCommands();
    }

    private void pollCommands(){
        byte[] revBuffer = new byte[32];
        log(resources.getString(R.string.wait_response));
        while (true){
            switchConnection.bulkTransfer(usbInPoint,revBuffer,32,0);
            byte[] header = Arrays.copyOfRange(revBuffer,0,4);
            if ( ! Arrays.equals(header,"TUC0".getBytes())) {//Tinfoil USB Command 0
                log(resources.getString(R.string.invalid_response));
                continue;
            }

            //byte[] cmdType = Arrays.copyOfRange(revBuffer,4,5);
            byte[] cmdID = Arrays.copyOfRange(revBuffer,8,12);
            //byte[] dateSize = Arrays.copyOfRange(revBuffer,12,20);

            int id = ByteUtils.LEByteArrayToInt(cmdID);
            if( id == 0){
                usbActivity.setPercent(0);
                log(resources.getString(R.string.exit));
                break;
            }else if(id == 1){
                fileRangeSender();
            }
        }
    }

    private void fileRangeSender(){
        byte[] buffer = new byte[32];
        switchConnection.bulkTransfer(usbInPoint,buffer,32,0);

        byte[] sizeArray = Arrays.copyOfRange(buffer,0,8);
        long rangeSize = ByteUtils.LEByteArrayToLong(sizeArray);
        long rangeOffset = ByteUtils.LEByteArrayToLong(Arrays.copyOfRange(buffer,8,16));

        int nameLen = ByteUtils.LEByteArrayToInt(Arrays.copyOfRange(buffer,16,24));
        buffer = new byte[nameLen];
        switchConnection.bulkTransfer(usbInPoint,buffer,nameLen,1000);
        String nspName = new String(buffer);
        nspName = nspFolder.getAbsolutePath()+"/"+ nspName;
        if("".equals(nspName)){
            log(resources.getString(R.string.empty_file_name));
            return;
        }else if(!sendFileName.equals(nspName)) {
            sendFileName = nspName;
            log(resources.getString(R.string.new_file)+nspName);
        }

        fileRangeHeader(sizeArray);

        File nsp = new File(nspName);
        if(!nsp.exists()){
            log(resources.getString(R.string.no_such_file));
            return;
        }
        //send file
        FileInputStream nspStream;
        try{
            nspStream = new FileInputStream(nsp);
            nspStream.skip(rangeOffset);

            long curOff = 0x0;
            int readSize = 0x4000;
            /*Before Android P, a value(size of data to be send to usb) larger than
            16384 bytes would be truncated down to 16384.*/
            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
                readSize = 0x800000;
            buffer = new byte[readSize];

            while (curOff < rangeSize){
                if (curOff+readSize >= rangeSize)
                    readSize = (int)(rangeSize - curOff);

                int actualSize = nspStream.read(buffer,0,readSize);
                if(actualSize<=0){
                    log(nspName +resources.getString(R.string.complete_sent));
                    break;
                }
                int sentSize = switchConnection.bulkTransfer(usbOutPoint,buffer,actualSize,1000);
                curOff += sentSize;
            }
            //show percent
            long fileLen = nsp.length();
            int p = (int)(Math.floor((curOff + rangeOffset)/fileLen));
            if(percent != p){
                percent = p;
                usbActivity.setPercent(percent);
                //log(resources.getString(R.string.percent)+percent);
            }
            nspStream.close();
        }catch (Exception e){
            log(resources.getString(R.string.send_error)+e.getMessage());
            e.printStackTrace();
        }
    }

    private void fileRangeHeader(byte[] rangeSize){
        //send response header
        byte[] buffer;
        try{
            buffer = "TUC0".getBytes("US-ASCII");//header
            switchConnection.bulkTransfer(usbOutPoint,buffer,4,1000);
        }catch (Exception e){
            e.printStackTrace();
        }
        buffer = new byte[]{1};//cmd type response
        switchConnection.bulkTransfer(usbOutPoint,buffer,1,1000);
        buffer = new byte[3];//Padding
        switchConnection.bulkTransfer(usbOutPoint,buffer,3,1000);
        buffer = new byte[]{1,0,0,0};//cmd id
        switchConnection.bulkTransfer(usbOutPoint,buffer,4,1000);
        //data size
        switchConnection.bulkTransfer(usbOutPoint,rangeSize,8,1000);
        buffer = new byte[12];//Padding
        switchConnection.bulkTransfer(usbOutPoint,buffer,12,1000);
    }

    private void requestPermission(UsbDevice device){
        if(switchDevice != null){
            //this will show a dialog to user to get the device access permission
            //if user choose yes, our broadcast receiver will receive something
            usbManager.requestPermission(device, usbPermissionIntent);
        }
    }

    public void onDestroy(){
        if(switchConnection != null){
            switchConnection.close();
        }
    }

    @Override
    public void setDevice(UsbDevice device) {
        switchDevice = device;
    }

    @Override
    public void releaseDevice(){
        switchDevice = null;
        switchConnection.releaseInterface(switchInterface);
        switchInterface = null;
        switchConnection.close();
        switchConnection = null;
        usbInPoint = null;
        usbOutPoint = null;
        updateState(false);
    }


    /**
     * @return true if this USB device is switch
     * */
    public static boolean isSwitch(UsbDevice device){
        return (device.getVendorId() == 1406 && device.getProductId() == 12288);
    }

    private void fileLog(String log){
        usbActivity.fileLog("[USBInstaller]:"+log);
    }

    private void log(String log){
        usbActivity.showLog("[USBInstaller]:"+log);
    }
}