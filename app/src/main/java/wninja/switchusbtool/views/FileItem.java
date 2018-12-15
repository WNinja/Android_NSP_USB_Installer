package wninja.switchusbtool.views;

import android.content.Context;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.widget.LinearLayout;

public class FileItem extends LinearLayout {
    private boolean bIsFile;
    private String sFileName;
    private boolean bChecked = false;

    public FileItem(Context context) {
        super(context);
    }

    public FileItem(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public FileItem(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public FileItem(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    public void setIsFile(boolean bIsFile) {
        this.bIsFile = bIsFile;
    }

    public boolean isFile(){
        return bIsFile;
    }

    public void setFileName(String sFileName) {
        this.sFileName = sFileName;
    }

    public String getFileName() {
        return sFileName;
    }

    public void setChecked(boolean bChecked) {
        this.bChecked = bChecked;
    }

    public boolean checked(){
        return bChecked;
    }
}
