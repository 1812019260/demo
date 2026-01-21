package com.example.demo.util;

import com.jcraft.jsch.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Properties;

/**
 * SFTP工具类
 * 用于与SFTP服务器进行文件传输
 */
@Slf4j
public class SftpUtil {

    private ChannelSftp channel;
    private Session session;
    private String host;
    private int port;
    private String username;
    private String password;

    /**
     * 构造函数
     */
    public SftpUtil(String host, int port, String username, String password) {
        this.host = host;
        this.port = port;
        this.username = username;
        this.password = password;
    }

    /**
     * 连接SFTP服务器
     */
    public void connect() throws JSchException {
        JSch jsch = new JSch();
        session = jsch.getSession(username, host, port);
        session.setPassword(password);
        Properties config = new Properties();
        config.put("StrictHostKeyChecking", "no");
        session.setConfig(config);
        session.connect();
        Channel channel = session.openChannel("sftp");
        channel.connect();
        this.channel = (ChannelSftp) channel;
        log.info("SFTP连接成功: {}:{}", host, port);
    }

    /**
     * 连接SFTP服务器（带重试机制）
     *
     * @param maxRetries 最大重试次数（默认3次）
     * @param retryInterval 重试间隔（秒，默认5秒）
     * @throws JSchException 连接失败异常
     */
    public void connectWithRetry(int maxRetries, int retryInterval) throws JSchException {
        int attempt = 0;
        JSchException lastException = null;

        while (attempt < maxRetries) {
            try {
                connect();
                return; // 连接成功，直接返回
            } catch (JSchException e) {
                lastException = e;
                attempt++;
                
                if (attempt < maxRetries) {
                    log.warn("SFTP连接失败（第{}次尝试）：{}，{}秒后重试...", 
                            attempt, e.getMessage(), retryInterval);
                    
                    try {
                        Thread.sleep(retryInterval * 1000L);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new JSchException("连接重试被中断", ie);
                    }
                }
            }
        }

        // 所有重试都失败
        log.error("SFTP连接失败，已重试{}次：{}", maxRetries, lastException.getMessage());
        throw lastException;
    }

    /**
     * 连接SFTP服务器（带重试机制，使用默认参数）
     *
     * @throws JSchException 连接失败异常
     */
    public void connectWithRetry() throws JSchException {
        connectWithRetry(3, 5);
    }

    /**
     * 断开连接
     */
    public void disconnect() {
        if (channel != null) {
            channel.disconnect();
        }
        if (session != null) {
            session.disconnect();
        }
        log.info("SFTP连接已断开");
    }

    /**
     * 上传文件
     *
     * @param directory  目标目录
     * @param fileName   文件名
     * @param file       要上传的文件
     */
    public void upload(String directory, String fileName, File file) throws Exception {
        if (channel == null) {
            throw new IllegalStateException("SFTP未连接");
        }

        try {
            // 切换到目标目录
            channel.cd(directory);
        } catch (SftpException e) {
            if (e.id == ChannelSftp.SSH_FX_NO_SUCH_FILE) {
                // 目录不存在，创建目录
                createDirectory(directory);
                channel.cd(directory);
            } else {
                throw e;
            }
        }

        // 上传文件
        try (FileInputStream fis = new FileInputStream(file)) {
            channel.put(fis, fileName);
            log.info("文件上传成功: {}/{}", directory, fileName);
        }
    }

    /**
     * 上传文件（MultipartFile）
     *
     * @param directory  目标目录
     * @param fileName   文件名
     * @param file       要上传的文件
     */
    public void upload(String directory, String fileName, MultipartFile file) throws Exception {
        if (channel == null) {
            throw new IllegalStateException("SFTP未连接");
        }

        try {
            // 尝试切换到目标目录
            channel.cd(directory);
        } catch (SftpException e) {
            if (e.id == ChannelSftp.SSH_FX_NO_SUCH_FILE) {
                // 目录不存在，创建目录（createDirectory方法会自动切换到该目录）
                createDirectory(directory);
            } else {
                throw e;
            }
        }

        // 上传文件
        try (InputStream is = file.getInputStream()) {
            channel.put(is, fileName);
            log.info("文件上传成功: {}/{}", directory, fileName);
        }
    }

    /**
     * 上传字节数组（用于内存中处理）
     *
     * @param directory 上传目录
     * @param fileName  文件名
     * @param data      文件数据（字节数组）
     * @throws Exception 上传异常
     */
    public void uploadBytes(String directory, String fileName, byte[] data) throws Exception {
        try {
            channel.cd(directory);
            ByteArrayInputStream inputStream = new ByteArrayInputStream(data);
            channel.put(inputStream, fileName);
            inputStream.close();
            log.info("文件上传成功：{}", fileName);
        } catch (Exception e) {
            log.error("文件上传失败：{}", e.getMessage(), e);
            throw e;
        }
    }

    /**
     * 上传字节数组（带重试机制）
     *
     * @param directory 上传目录
     * @param fileName  文件名
     * @param data      文件数据（字节数组）
     * @param maxRetries 最大重试次数（默认3次）
     * @param retryInterval 重试间隔（秒，默认30秒）
     * @throws Exception 上传异常
     */
    public void uploadBytesWithRetry(String directory, String fileName, byte[] data, int maxRetries, int retryInterval) throws Exception {
        int retryCount = 0;
        Exception lastException = null;

        while (retryCount <= maxRetries) {
            try {
                if (retryCount > 0) {
                    log.info("第 {} 次重试上传文件：{}", retryCount, fileName);
                }

                channel.cd(directory);
                ByteArrayInputStream inputStream = new ByteArrayInputStream(data);
                channel.put(inputStream, fileName);
                inputStream.close();

                if (retryCount > 0) {
                    log.info("第 {} 次重试上传成功：{}", retryCount, fileName);
                } else {
                    log.info("文件上传成功：{}", fileName);
                }
                return;

            } catch (Exception e) {
                lastException = e;
                retryCount++;

                if (retryCount <= maxRetries) {
                    log.warn("文件上传失败（第{}次尝试）：{}，{}秒后重试...", retryCount, e.getMessage(), retryInterval);
                    try {
                        Thread.sleep(retryInterval * 1000L);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new Exception("上传被中断", ie);
                    }
                }
            }
        }

        log.error("文件上传失败，已重试{}次：{}", maxRetries, lastException.getMessage());
        throw new Exception(String.format("文件上传失败，已重试%d次：%s", maxRetries, lastException.getMessage()), lastException);
    }

    /**
     * 上传字节数组（带重试机制，使用默认参数）
     *
     * @param directory 上传目录
     * @param fileName  文件名
     * @param data      文件数据（字节数组）
     * @throws Exception 上传异常
     */
    public void uploadBytesWithRetry(String directory, String fileName, byte[] data) throws Exception {
        uploadBytesWithRetry(directory, fileName, data, 3, 5);
    }

    /**
     * 下载文件
     *
     * @param directory  源目录
     * @param fileName   文件名
     * @param localPath  本地保存路径
     */
    public void download(String directory, String fileName, String localPath) throws Exception {
        if (channel == null) {
            throw new IllegalStateException("SFTP未连接");
        }

        File localFile = new File(localPath);
        File parentDir = localFile.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            parentDir.mkdirs();
        }

        // 使用完整路径下载文件，不使用cd命令
        String remoteFilePath = directory + "/" + fileName;

        log.info("准备下载文件，远程路径: {}, 本地路径: {}", remoteFilePath, localPath);

        try {
            // 检查文件是否存在
            try {
                channel.stat(remoteFilePath);
                log.info("文件存在: {}", remoteFilePath);
            } catch (SftpException e) {
                if (e.id == ChannelSftp.SSH_FX_NO_SUCH_FILE) {
                    log.error("文件不存在: {}", remoteFilePath);
                    log.error("当前SFTP工作目录: {}", channel.pwd());
                    // 尝试列出目录内容以帮助调试
                    try {
                        log.info("尝试列出目录 {} 的内容:", directory);
                        @SuppressWarnings("unchecked")
                        java.util.Vector<ChannelSftp.LsEntry> entries = channel.ls(directory);
                        for (ChannelSftp.LsEntry entry : entries) {
                            log.info("  - {}", entry.getFilename());
                        }
                    } catch (SftpException lsException) {
                        log.error("无法列出目录内容: {}", lsException.getMessage());
                    }
                    throw new RuntimeException("文件不存在: " + remoteFilePath, e);
                }
                throw e;
            }

            // 下载文件
            try (InputStream is = channel.get(remoteFilePath)) {
                java.io.FileOutputStream fos = new java.io.FileOutputStream(localFile);
                byte[] buffer = new byte[1024];
                int len;
                while ((len = is.read(buffer)) != -1) {
                    fos.write(buffer, 0, len);
                }
                fos.close();
                log.info("文件下载成功: {} -> {}", remoteFilePath, localPath);
            }
        } catch (SftpException e) {
            log.error("SFTP下载失败，错误码: {}, 错误信息: {}", e.id, e.getMessage());
            throw new RuntimeException("SFTP下载失败: " + e.getMessage(), e);
        }
    }

    /**
     * 删除文件
     *
     * @param directory  目录
     * @param fileName   文件名
     */
    public void delete(String directory, String fileName) throws Exception {
        if (channel == null) {
            throw new IllegalStateException("SFTP未连接");
        }

        channel.cd(directory);
        channel.rm(fileName);
        log.info("文件删除成功: {}/{}", directory, fileName);
    }

    /**
     * 创建目录
     *
     * @param directory  目录路径
     */
    private void createDirectory(String directory) throws Exception {
        if (directory == null || directory.isEmpty()) {
            return;
        }

        // 保存当前工作目录
        String pwd = channel.pwd();

        String[] dirs = directory.split("/");
        String currentPath = "";
        for (String dir : dirs) {
            if (dir.isEmpty()) {
                continue;
            }
            currentPath += "/" + dir;
            try {
                channel.cd(currentPath);
            } catch (SftpException e) {
                if (e.id == ChannelSftp.SSH_FX_NO_SUCH_FILE) {
                    channel.mkdir(currentPath);
                    log.info("创建目录: {}", currentPath);
                } else {
                    throw e;
                }
            }
        }

        // 切换到根目录
        channel.cd("/");
        // 重新切换到目标目录
        channel.cd(directory);
        log.info("目录创建成功并切换到: {}", directory);
    }

    /**
     * 检查文件是否存在
     *
     * @param directory  目录
     * @param fileName   文件名
     * @return  true-存在，false-不存在
     */
    public boolean isFileExist(String directory, String fileName) throws Exception {
        if (channel == null) {
            throw new IllegalStateException("SFTP未连接");
        }

        try {
            channel.cd(directory);
            channel.ls(fileName);
            return true;
        } catch (SftpException e) {
            if (e.id == ChannelSftp.SSH_FX_NO_SUCH_FILE) {
                return false;
            }
            throw e;
        }
    }

    /**
     * 递归搜索文件
     *
     * @param directory  搜索目录
     * @param fileName   文件名
     * @return  文件的完整路径，如果未找到返回null
     */
    public String searchFile(String directory, String fileName) throws Exception {
        if (channel == null) {
            throw new IllegalStateException("SFTP未连接");
        }

        log.info("开始递归搜索文件，目录: {}, 文件名: {}", directory, fileName);

        // 保存当前工作目录
        String currentDir = channel.pwd();

        try {
            // 尝试切换到目标目录
            channel.cd(directory);
        } catch (SftpException e) {
            if (e.id == ChannelSftp.SSH_FX_NO_SUCH_FILE) {
                log.warn("目录不存在: {}", directory);
                return null;
            }
            throw e;
        }

        // 搜索当前目录
        String foundPath = searchFileRecursive(fileName, directory);

        // 恢复工作目录
        channel.cd(currentDir);

        return foundPath;
    }

    /**
     * 递归搜索文件的内部实现
     *
     * @param fileName   文件名
     * @param currentDir 当前目录
     * @return  文件的完整路径，如果未找到返回null
     */
    private String searchFileRecursive(String fileName, String currentDir) throws Exception {
        try {
            @SuppressWarnings("unchecked")
            java.util.Vector<ChannelSftp.LsEntry> entries = channel.ls(currentDir);

            for (ChannelSftp.LsEntry entry : entries) {
                String name = entry.getFilename();
                // 跳过当前目录和上级目录
                if (".".equals(name) || "..".equals(name)) {
                    continue;
                }

                String fullPath = currentDir + "/" + name;

                if (entry.getAttrs().isDir()) {
                    // 如果是目录，递归搜索
                    log.debug("进入子目录搜索: {}", fullPath);
                    String foundPath = searchFileRecursive(fileName, fullPath);
                    if (foundPath != null) {
                        return foundPath;
                    }
                } else {
                    // 如果是文件，检查是否匹配
                    if (name.equals(fileName)) {
                        log.info("找到文件: {}", fullPath);
                        return fullPath;
                    }
                }
            }
        } catch (SftpException e) {
            log.error("搜索目录时出错: {}, 错误: {}", currentDir, e.getMessage());
        }

        return null;
    }

    /**
     * 直接从SFTP服务器获取文件输入流（不保存到本地，不加载到内存）
     *
     * @param directory  源目录
     * @param fileName   文件名
     * @return  文件的输入流
     */
    public InputStream downloadAsStream(String directory, String fileName) throws Exception {
        if (channel == null) {
            throw new IllegalStateException("SFTP未连接");
        }

        // 使用完整路径读取文件，不使用cd命令
        String remoteFilePath = directory + "/" + fileName;

        log.info("准备获取文件流，远程路径: {}", remoteFilePath);

        try {
            // 检查文件是否存在
            try {
                channel.stat(remoteFilePath);
                log.info("文件存在: {}", remoteFilePath);
            } catch (SftpException e) {
                if (e.id == ChannelSftp.SSH_FX_NO_SUCH_FILE) {
                    log.error("文件不存在: {}", remoteFilePath);
                    throw new RuntimeException("文件不存在: " + remoteFilePath, e);
                }
                throw e;
            }

            // 直接返回文件输入流
            InputStream is = channel.get(remoteFilePath);
            log.info("文件流获取成功: {}", remoteFilePath);
            return is;
        } catch (SftpException e) {
            log.error("SFTP获取文件流失败，错误码: {}, 错误信息: {}", e.id, e.getMessage());
            throw new RuntimeException("SFTP获取文件流失败: " + e.getMessage(), e);
        }
    }
}