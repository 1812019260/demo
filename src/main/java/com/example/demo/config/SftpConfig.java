package com.example.demo.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * SFTP配置类
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "sign.sftp")
public class SftpConfig {
    /* 服务器地址 */
    private String host;

    /* 端口 */
    private Integer port;

    /* 用户名 */
    private String username;

    /* 密码 */
    private String password;

    /* 接收目录 (本地路径) */
    private String receivePath;

    /* 返回目录 (本地路径) */
    private String returnPath;

    /* 通知接口URL */
    private String noticeUrl;

    /**
     * 获取SFTP服务器上的接收目录路径
     * 将Windows路径转换为Linux路径格式
     * @return SFTP服务器路径
     */
    public String getSftpReceivePath() {
        return convertToSftpPath(receivePath);
    }

    /**
     * 获取SFTP服务器上的返回目录路径
     * 将Windows路径转换为Linux路径格式
     * @return SFTP服务器路径
     */
    public String getSftpReturnPath() {
        return convertToSftpPath(returnPath);
    }

    /**
     * 将路径转换为SFTP服务器路径
     * 支持Windows和Linux两种路径格式
     * Windows示例: D:/ce_shi/ftp_server_data/sign_upload/toBeSignFile -> /D:/ce_shi/ftp_server_data/sign_upload/toBeSignFile
     * Linux示例: /home/ftp/sign_upload/toBeSignFile -> /home/ftp/sign_upload/toBeSignFile
     * @param path 路径
     * @return SFTP服务器路径
     */
    private String convertToSftpPath(String path) {
        if (path == null || path.isEmpty()) {
            return path;
        }

        // 统一使用正斜杠
        String result = path.replace("\\", "/");

        // 如果路径包含盘符（Windows格式），转换为 /盘符:/ 格式
        if (result.matches("^[A-Za-z]:/.*")) {
            // Windows路径：D:/path -> /D:/path
            result = "/" + result;
        } else if (!result.startsWith("/")) {
            // Linux路径：home/ftp -> /home/ftp
            result = "/" + result;
        }

        return result;
    }
}
