package com.example.demo.util;

import java.io.File;

public class FileUtil {

    /**
     * 创建目录
     */
    public static boolean mkdirs(String path) {
        File dir = new File(path);
        if (!dir.exists()) {
            return dir.mkdirs();
        }
        return true;
    }

    /**
     * 删除目录
     */
    public static boolean deleteDirectory(File dir) {
        if (dir.isDirectory()) {
            File[] files = dir.listFiles();
            if (files != null) {
                for (File file : files) {
                    deleteDirectory(file);
                }
            }
        }
        return dir.delete();
    }

    /**
     * 获取文件扩展名
     */
    public static String getExtension(String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            return "";
        }
        int lastDotIndex = fileName.lastIndexOf('.');
        if (lastDotIndex == -1) {
            return "";
        }
        return fileName.substring(lastDotIndex + 1);
    }

    /**
     * 获取文件大小(MB)
     */
    public static double getFileSizeMB(File file) {
        if (file == null || !file.exists()) {
            return 0;
        }
        return file.length() / (1024.0 * 1024.0);
    }
}
