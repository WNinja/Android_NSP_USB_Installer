package wninja.switchusbtool.Tool;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.os.Environment;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.Arrays;

import wninja.switchusbtool.activities.USBInstallActivity;
import wninja.switchusbtool.interfaces.nspActivityCallback;
import wninja.switchusbtool.interfaces.SenderCallback;
import wninja.switchusbtool.utils.ByteUtils;
import wninja.switchusbtool.utils.FileUtils;

public class NSPUSBSender implements SenderCallback {
    public static final String ACTION_USB_PERMISSION = "wninja.switchusbtool.USB_PERMISSION";

    private PendingIntent usbPermissionIntent;

    private UsbManager usbManager;
    private UsbDevice switchDevice;
    private UsbInterface switchInterface;
    private UsbDeviceConnection switchConnection;
    private UsbEndpoint usbInPoint,usbOutPoint;

    private nspActivityCallback nspCallback;
    private File nspFolder = null;
    private String sendFileName = "";

    public NSPUSBSender(Activity activity){
        nspCallback = (USBInstallActivity) activity;
        usbManager = (UsbManager)activity.getSystemService(Context.USB_SERVICE);
        usbPermissionIntent =  PendingIntent.getBroadcast(activity, 0,
                new Intent(NSPUSBSender.ACTION_USB_PERMISSION), 0);
        setNspFolder();
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
        switchInterface = null;
        switchConnection = null;
        usbInPoint = null;
        usbOutPoint = null;
        updateState(false);
    }

    //set the state if the switch is connecting
    @Override
    public void updateState(boolean connecting){
        if(connecting){
            if(initConnection()){
                log("now switch is connecting");
            }
        }else {
            log("now switch is disconnected");
            nspCallback.setPercent(0);
        }
    }

    private void setNspFolder(){
        try{
            String nspPath = Environment.getExternalStorageDirectory().getCanonicalPath()+"/nsp";
            log("nsp path is: "+nspPath);
            nspFolder = new File(nspPath);
        }catch (Exception e){
            log(e.toString());
            e.printStackTrace();
        }
    }

    public void findDevice(){
        if(switchDevice == null){
            try{
                for(UsbDevice dev: usbManager.getDeviceList().values()){
                    if (isSwitch(dev)) {
                        switchDevice = dev;
                        log("from device list ,find the connecting switch device");
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
            log("can't find switch device");
        }
    }

    private boolean initConnection(){
        if (switchDevice == null){
            return false;
        }
        switchInterface = switchDevice.getInterface(0);
        log("initConnection: the switch has "+switchDevice.getInterfaceCount()+" interfaces");
        //find the in and out USB point
        for (int i = 0; i < switchInterface.getEndpointCount(); i++){
            UsbEndpoint pt = switchInterface.getEndpoint(i);
            if(pt.getDirection() == UsbConstants.USB_DIR_IN){
                log("initConnection: find one in point");
                usbInPoint = pt;
            }else if(pt.getDirection() == UsbConstants.USB_DIR_OUT){
                log("initConnection: find one out point");
                usbOutPoint = pt;
            }
        }
        if(usbInPoint == null || usbOutPoint == null){
            log("get end point error");
            return false;
        }
        //open USB connection
        UsbDeviceConnection connection = usbManager.openDevice(switchDevice);
        if(connection != null && connection.claimInterface(switchInterface,true)){
            switchConnection = connection;
            return true;
        }
        return false;
    }

    public void sendFile(){
        if(switchConnection == null && !initConnection()){
            log("no switch connecting!");
            return;
        }

        new Thread(){
            @Override
            public void run(){
                if(nspFolder ==null || !nspFolder.exists() || nspFolder.isFile()){
                    log("the nsp folder doesn't exist or may not be a directory. you should create a folder called 'nsp' in root directory.");
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
                    String fName = f.getAbsolutePath()+ '\n';
                    fileList.add(fName);
                    fileNameLen += fName.length();
                    log("find one nsp: "+fName);
                }
            }
        }

        if(fileList.isEmpty()){
            log("nsp folder is empty");
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
        while (true){
            log("waiting for switch to response");
            switchConnection.bulkTransfer(usbInPoint,revBuffer,32,0);
            byte[] header = Arrays.copyOfRange(revBuffer,0,4);
            if ( ! Arrays.equals(header,"TUC0".getBytes())){//Tinfoil USB Command 0
                continue;
            }
            //byte[] cmdType = Arrays.copyOfRange(revBuffer,4,5);
            byte[] cmdID = Arrays.copyOfRange(revBuffer,8,12);
            //byte[] dateSize = Arrays.copyOfRange(revBuffer,12,20);

            int id = ByteUtils.LEByteArrayToInt(cmdID);
            if( id == 0){
                nspCallback.setPercent(0);
                log("the switch send cmd exit so this sending process will exit.");
                break;
            }else if(id == 1){
                fileRangeSender();
            }
        }
    }

    private void fileRangeSender(){
        byte[] fileRangeHeader = new byte[32];
        switchConnection.bulkTransfer(usbInPoint,fileRangeHeader,32,0);

        byte[] rangeSize = Arrays.copyOfRange(fileRangeHeader,0,8);
        byte[] rangeOffset = Arrays.copyOfRange(fileRangeHeader,8,16);
        byte[] nameLen = Arrays.copyOfRange(fileRangeHeader,16,24);

        int len = ByteUtils.LEByteArrayToInt(nameLen);
        byte[] pathBuffer = new byte[len];
        int rtL = switchConnection.bulkTransfer(usbInPoint,pathBuffer,len,1000);
        log("receive file pathBuffer length is "+rtL);

        //send response header
        byte[] buffer;
        try{
            buffer = "TUC0".getBytes("US-ASCII");//header
            switchConnection.bulkTransfer(usbOutPoint,buffer,4,1000);
        }catch (Exception e){
            log(e.toString());
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

        //send file
        long size = ByteUtils.LEByteArrayToLong(rangeSize);//size of file's fragment we need to send to switch
        long offset = ByteUtils.LEByteArrayToLong(rangeOffset);//offset of file(in bytes) that we should skip
        try{
            String path = new String(pathBuffer);
            if(!sendFileName.equals(path)){
                sendFileName = path;
                log("sending file: "+path);
            }
            File nsp = new File(path);
            if(!nsp.exists()){
                log("try to send file to switch which was not exist");
                return;
            }
            FileInputStream nspStream = new FileInputStream(nsp);
            nspStream.skip(offset);
            buffer = new byte[0x800000];
            long curOff = 0x0;
            int readSize = 0x800000;
            double percent = 0;
            while (curOff < size){
                if (curOff+readSize>=size){
                    readSize = (int)(size - curOff);//this num won't be bigger than 0x800000
                }
                int actualSize = nspStream.read(buffer,0,readSize);
                if(actualSize<=0){
                    log("one file has been completely sent");
                    break;
                }
                switchConnection.bulkTransfer(usbOutPoint,buffer,actualSize,2000);
                curOff += readSize;

                long fileLen = nsp.length();
                double p = Math.floor((curOff + offset)/fileLen);
                //show percent
                if(percent != p){
                    percent = p;
                    nspCallback.setPercent((int)percent);
                    log("send percent: "+percent);
                }
            }
            nspStream.close();
        }catch (Exception e){
            log(e.toString());
            e.printStackTrace();
        }
    }

    private void requestPermission(UsbDevice device){
        if(switchDevice != null){
            //this will show a dialog to user to get the device access permission
            //if user choose yes, our broadcast receiver will receive something
            log("try request,you will see the dialog");
            usbManager.requestPermission(device, usbPermissionIntent);
        }
    }

    /**
     * @return true if this USB device is switch
     * */
    public static boolean isSwitch(UsbDevice device){
        return (device.getVendorId() == 1406 && device.getProductId() == 12288);
    }

    private void log(String log){
        nspCallback.showLog(log);
    }
}
