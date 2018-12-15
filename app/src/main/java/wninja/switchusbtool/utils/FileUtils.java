package wninja.switchusbtool.utils;

import java.io.File;

public class FileUtils {
    public static String getFileSuffix(File f){
        String name = f.getName();
        int index = name.lastIndexOf(".");
        if( index != -1 ){
            return name.substring(index+1);
        }
        return "";
    }
}
