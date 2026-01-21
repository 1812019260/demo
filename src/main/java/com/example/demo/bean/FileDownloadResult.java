package com.example.demo.bean;

/**
 * 文件下载结果类
 *
 * @author zxd
 * @since 2026-01-20
 */
public class FileDownloadResult {
    private String fileName;
    private byte[] bytes;

    public FileDownloadResult(String fileName, byte[] bytes) {
        this.fileName = fileName;
        this.bytes = bytes;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public byte[] getBytes() {
        return bytes;
    }

    public void setBytes(byte[] bytes) {
        this.bytes = bytes;
    }
}