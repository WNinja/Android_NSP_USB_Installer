package wninja.switchusbtool.utils;

public class ByteUtils {

    public static byte[] int2byte(int res) {
        byte[] targets = new byte[4];

        targets[3] = (byte) (res & 0xff);// 最低位
        targets[2] = (byte) ((res >> 8) & 0xff);// 次低位
        targets[1] = (byte) ((res >> 16) & 0xff);// 次高位
        targets[0] = (byte) (res >>> 24);// 最高位,无符号右移。
        return targets;
    }
    //little endian
    public static byte[] int2byteLE(int res) {
        byte[] targets = new byte[4];

        targets[0] = (byte) (res & 0xff);// 最低位
        targets[1] = (byte) ((res >> 8) & 0xff);// 次低位
        targets[2] = (byte) ((res >> 16) & 0xff);// 次高位
        targets[3] = (byte) (res >>> 24);// 最高位,无符号右移。
        return targets;
    }

    public static int byteArrayToInt(byte[] b){
        byte[] a = new byte[4];
        int i = a.length - 1,j = b.length - 1;
        for (; i >= 0 ; i--,j--) {//从b的尾部(即int值的低位)开始copy数据
            if(j >= 0)
                a[i] = b[j];
            else
                a[i] = 0;//如果b.length不足4,则将高位补0
        }
        int v0 = (a[0] & 0xff) << 24;//&0xff将byte值无差异转成int,避免Java自动类型提升后,会保留高位的符号位
        int v1 = (a[1] & 0xff) << 16;
        int v2 = (a[2] & 0xff) << 8;
        int v3 = (a[3] & 0xff) ;
        return v0 + v1 + v2 + v3;
    }
    //little endian
    public static int LEByteArrayToInt(byte[] b){
        byte[] a = new byte[4];
        int i = 0,j = 0;
        for (; i <4 ; i++,j++) {//从b的头部(即int值的低位)开始copy数据
            if(j <b.length)
                a[i] = b[j];
            else
                a[i] = 0;//如果b.length不足4,则将高位补0
        }
        int v0 = (a[3] & 0xff) << 24;//&0xff将byte值无差异转成int,避免Java自动类型提升后,会保留高位的符号位
        int v1 = (a[2] & 0xff) << 16;//左移变大
        int v2 = (a[1] & 0xff) << 8;
        int v3 = (a[0] & 0xff) ;
        return v0 + v1 + v2 + v3;
    }

    public static byte[] long2byte(long res) {
        byte[] buffer = new byte[8];
        for (int i = 0; i < 8; i++) {
            int offset = 64 - (i + 1) * 8;
            buffer[i] = (byte) ((res >> offset) & 0xff);
        }
        return buffer;
    }

    public static long byteArrayToLong(byte[] b){
        long values = 0;
        for (int i = 0; i < 8; i++) {
            values <<= 8; values|= (b[i] & 0xff);
        }
        return values;
    }
    //little endian
    public static long LEByteArrayToLong(byte[] b){
        long values = 0;
        for (int i = 7; i >= 0; i--) {
            values <<= 8;values|= (b[i] & 0xff);
        }
        return values;
    }

    /*public static String other(byte[] b){
        Charset cs = Charset.forName("US-ASCII");
        ByteBuffer bb = ByteBuffer.allocate(b.length);
        bb.put(b);
        bb.flip();
        CharBuffer cb = cs.decode(bb);
        return cb.toString();
    }*/

    public static String byteArrayToString(byte[] b){
        char[] c = new char[b.length];
        for(int i=0;i<b.length;i++){
            c[i] = (char)(b[i]&0xFF);
        }
        return String.valueOf(c);
    }
}
