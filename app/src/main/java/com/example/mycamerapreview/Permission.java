package com.example.mycamerapreview;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class Permission {
    public static final int REQUEST_CODE = 5;
    //定义三个权限
    private static final String[] permission = new String[]{
            Manifest.permission.CAMERA
    };
    //每个权限是否已授
    public static boolean isPermissionGranted(Activity activity){
        for (int i = 0; i < permission.length; i++) {
            int checkPermission = ContextCompat.checkSelfPermission(activity, permission[i]);
            /***
             * checkPermission返回两个值
             * 有权限: PackageManager.PERMISSION_GRANTED
             * 无权限: PackageManager.PERMISSION_DENIED
             */
            if (checkPermission != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    public static boolean checkPermission(Activity activity){
        if(isPermissionGranted(activity)) {
            return true;
        } else {
            //如果没有设置过权限许可，则弹出系统的授权窗口
            ActivityCompat.requestPermissions(activity,permission,REQUEST_CODE);
            return false;
        }
    }
}

