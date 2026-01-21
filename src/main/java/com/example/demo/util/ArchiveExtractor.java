package com.example.demo.util;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ar.ArArchiveInputStream;
import org.apache.commons.compress.archivers.cpio.CpioArchiveInputStream;
import org.apache.commons.compress.archivers.jar.JarArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * 压缩文件解析工具类
 * 支持 zip, 7z, tar, gz, bz2, xz, jar, ar, cpio 等格式
 * 在内存中完成解析，不创建本地临时文件
 * 支持中文文件名
 *
 * @author zxd
 * @since 2026-01-20
 */
@Slf4j
public class ArchiveExtractor {

    /**
     * 从压缩文件中提取文件信息
     *
     * @param file 上传的压缩文件
     * @return 提取的文件信息列表
     */
    public static List<ArchiveFileInfo> extract(MultipartFile file) {
        log.info("开始解析压缩文件：{}", file.getOriginalFilename());

        List<ArchiveFileInfo> result = new ArrayList<>();

        try {
            String filename = file.getOriginalFilename();
            if (filename == null) {
                log.warn("文件名为空");
                return result;
            }

            String lowerName = filename.toLowerCase();

            // 根据文件扩展名选择合适的解析方式
            if (lowerName.endsWith(".zip")) {
                result = extractZip(file);
            } else if (lowerName.endsWith(".tar")) {
                result = extractTar(file);
            } else if (lowerName.endsWith(".gz") || lowerName.endsWith(".tgz")) {
                result = extractGzip(file);
            } else if (lowerName.endsWith(".bz2")) {
                result = extractBzip2(file);
            } else if (lowerName.endsWith(".xz")) {
                result = extractXz(file);
            } else if (lowerName.endsWith(".jar")) {
                result = extractJar(file);
            } else if (lowerName.endsWith(".ar")) {
                result = extractAr(file);
            } else if (lowerName.endsWith(".cpio")) {
                result = extractCpio(file);
            } else if (lowerName.endsWith(".7z")) {
                result = extract7z(file);
            } else if (lowerName.endsWith(".rar")) {
                result = extractRar(file);
            } else {
                log.warn("不支持的压缩格式：{}", filename);
            }

            log.info("压缩文件解析完成，共提取 {} 个文件", result.size());
            return result;

        } catch (Exception e) {
            log.error("解析压缩文件失败：{}", e.getMessage(), e);
            return result;
        }
    }

    /**
     * 从压缩文件字节数组中提取文件信息
     *
     * @param fileData 压缩文件的字节数组
     * @return 提取的文件信息列表
     */
    public static List<ArchiveFileInfo> extract(byte[] fileData) {
        log.info("开始解析压缩文件（字节数组），大小：{} 字节", fileData.length);

        List<ArchiveFileInfo> result = new ArrayList<>();

        try {
            // 默认按ZIP格式解析
            result = extractZip(fileData);

            log.info("压缩文件解析完成，共提取 {} 个文件", result.size());
            return result;

        } catch (Exception e) {
            log.error("解析压缩文件失败：{}", e.getMessage(), e);
            return result;
        }
    }

    /**
     * 解析 ZIP 文件（使用 Java 内置的 ZipFile，更好的兼容性）
     */
    private static List<ArchiveFileInfo> extractZip(MultipartFile file) {
        List<ArchiveFileInfo> result = new ArrayList<>();
        java.io.File tempFile = null;

        try {
            // 将 MultipartFile 转换为临时文件
            tempFile = java.io.File.createTempFile("zip", ".zip");
            file.transferTo(tempFile);

            // 尝试多种编码，选择最合适的一个
            // 优先尝试GBK（Windows标准），然后UTF-8（Linux标准），最后GB2312（老版本Windows）
            String[] encodings = {"GBK", "UTF-8", "GB2312"};
            List<List<ArchiveFileInfo>> allResults = new ArrayList<>();

            for (String encoding : encodings) {
                List<ArchiveFileInfo> files = tryExtractZipWithEncoding(tempFile, encoding);
                if (!files.isEmpty()) {
                    allResults.add(files);
                    log.info("使用 {} 编码成功解析 ZIP 文件，共提取 {} 个文件", encoding, files.size());
                    // 打印前3个文件名用于调试
                    for (int i = 0; i < Math.min(3, files.size()); i++) {
                        log.info("  - 示例文件名[{}]：'{}'", i, files.get(i).getName());
                    }
                }
            }

            if (allResults.isEmpty()) {
                log.error("所有编码尝试均失败，无法解析 ZIP 文件");
                return result;
            }

            // 选择最可能正确的编码（优先选择包含中文的结果）
            result = selectBestResult(allResults, encodings);

            log.info("最终选择解析结果，共提取 {} 个文件", result.size());
            for (ArchiveFileInfo info : result) {
                log.info("  - 文件名：'{}'，大小：{} 字节", info.getName(), info.getSize());
            }

        } catch (Exception e) {
            log.error("解析 ZIP 文件时发生异常：{}", e.getMessage(), e);
        } finally {
            // 删除临时文件
            if (tempFile != null && tempFile.exists()) {
                tempFile.delete();
            }
        }

        return result;
    }

    /**
     * 解析 ZIP 文件（从字节数组）
     */
    private static List<ArchiveFileInfo> extractZip(byte[] fileData) {
        List<ArchiveFileInfo> result = new ArrayList<>();
        java.io.File tempFile = null;

        try {
            // 将字节数组写入临时文件
            tempFile = java.io.File.createTempFile("zip", ".zip");
            java.io.FileOutputStream fos = new java.io.FileOutputStream(tempFile);
            fos.write(fileData);
            fos.close();

            // 尝试多种编码，选择最合适的一个
            // 优先尝试GBK（Windows标准），然后UTF-8（Linux标准），最后GB2312（老版本Windows）
            String[] encodings = {"GBK", "UTF-8", "GB2312"};
            List<List<ArchiveFileInfo>> allResults = new ArrayList<>();

            for (String encoding : encodings) {
                List<ArchiveFileInfo> files = tryExtractZipWithEncoding(tempFile, encoding);
                if (!files.isEmpty()) {
                    allResults.add(files);
                    log.info("使用 {} 编码成功解析 ZIP 文件，共提取 {} 个文件", encoding, files.size());
                    // 打印前3个文件名用于调试
                    for (int i = 0; i < Math.min(3, files.size()); i++) {
                        log.info("  - 示例文件名[{}]：'{}'", i, files.get(i).getName());
                    }
                }
            }

            if (allResults.isEmpty()) {
                log.error("所有编码尝试均失败，无法解析 ZIP 文件");
                return result;
            }

            // 选择最可能正确的编码（优先选择包含中文的结果）
            result = selectBestResult(allResults, encodings);

            log.info("最终选择解析结果，共提取 {} 个文件", result.size());
            for (ArchiveFileInfo info : result) {
                log.info("  - 文件名：'{}'，大小：{} 字节", info.getName(), info.getSize());
            }

        } catch (Exception e) {
            log.error("解析 ZIP 文件时发生异常：{}", e.getMessage(), e);
        } finally {
            // 删除临时文件
            if (tempFile != null && tempFile.exists()) {
                tempFile.delete();
            }
        }

        return result;
    }

    /**
     * 从多个解析结果中选择最合适的一个
     */
    private static List<ArchiveFileInfo> selectBestResult(List<List<ArchiveFileInfo>> allResults, String[] encodings) {
        // 优先选择包含中文文件名的结果
        for (int i = 0; i < allResults.size(); i++) {
            List<ArchiveFileInfo> files = allResults.get(i);
            if (containsChinese(files) && isValidFilenames(files)) {
                log.info("选择 {} 编码的解析结果（包含中文文件名）", encodings[i]);
                return files;
            }
        }

        // 如果没有中文，选择文件名最合理的结果
        for (int i = 0; i < allResults.size(); i++) {
            List<ArchiveFileInfo> files = allResults.get(i);
            if (isValidFilenames(files)) {
                log.info("选择 {} 编码的解析结果（文件名有效）", encodings[i]);
                return files;
            }
        }

        // 如果都不可用，返回第一个非空结果
        for (List<ArchiveFileInfo> files : allResults) {
            if (!files.isEmpty()) {
                return files;
            }
        }

        return new ArrayList<>();
    }

    /**
     * 检查文件列表中是否包含中文文件名
     */
    private static boolean containsChinese(List<ArchiveFileInfo> files) {
        for (ArchiveFileInfo info : files) {
            String name = info.getName();
            for (char c : name.toCharArray()) {
                if (isChinese(c)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 判断字符是否为中文
     */
    private static boolean isChinese(char c) {
        // 中文Unicode范围：\u4e00-\u9fff（基本汉字）
        return c >= '\u4e00' && c <= '\u9fff';
    }

    /**
     * 检查文件名是否有效（不包含乱码字符）
     */
    private static boolean isValidFilenames(List<ArchiveFileInfo> files) {
        for (ArchiveFileInfo info : files) {
            String name = info.getName();
            // 检查是否包含明显的乱码字符（如连续的替换字符）
            if (name.contains("\uFFFD") || name.contains("ï¿½")) {
                return false;
            }
            // 检查是否包含过多的控制字符
            int controlCharCount = 0;
            for (char c : name.toCharArray()) {
                if (Character.isISOControl(c) && c != '\n' && c != '\r' && c != '\t') {
                    controlCharCount++;
                }
            }
            if (controlCharCount > name.length() / 2) {
                return false;
            }
        }
        return true;
    }

    /**
     * 使用指定编码解析 ZIP 文件
     */
    private static List<ArchiveFileInfo> tryExtractZipWithEncoding(java.io.File zipFile, String encoding) {
        List<ArchiveFileInfo> result = new ArrayList<>();

        try {
            java.nio.charset.Charset charset = java.nio.charset.Charset.forName(encoding);
            ZipFile zip = new ZipFile(zipFile, charset);

            try {
                Enumeration<? extends ZipEntry> entries = zip.entries();

                while (entries.hasMoreElements()) {
                    ZipEntry entry = entries.nextElement();

                    if (!entry.isDirectory()) {
                        // 读取文件内容
                        java.io.InputStream is = zip.getInputStream(entry);
                        ByteArrayOutputStream baos = new ByteArrayOutputStream();
                        byte[] buffer = new byte[8192];
                        int len;
                        while ((len = is.read(buffer)) > 0) {
                            baos.write(buffer, 0, len);
                        }
                        is.close();

                        byte[] fileData = baos.toByteArray();
                        log.info("[{}] 读取文件：'{}'，声明大小：{} 字节，实际读取：{} 字节",
                                encoding, entry.getName(), entry.getSize(), fileData.length);

                        ArchiveFileInfo info = new ArchiveFileInfo();
                        info.setName(entry.getName());
                        info.setSize(entry.getSize());
                        info.setData(fileData);
                        result.add(info);
                    }
                }
            } finally {
                zip.close();
            }
        } catch (IllegalArgumentException e) {
            // 捕获MALFORMED错误，说明该编码不适用
            if (e.getMessage() != null && e.getMessage().contains("MALFORMED")) {
                log.debug("使用 {} 编码读取ZIP文件失败：文件名编码不匹配", encoding);
            } else {
                log.error("使用 {} 编码读取ZIP文件时发生异常：{}", encoding, e.getMessage());
            }
        } catch (Exception e) {
            log.error("使用 {} 编码读取ZIP文件时发生异常：{}", encoding, e.getMessage(), e);
        }

        return result;
    }

    /**
     * 解析 TAR 文件
     */
    private static List<ArchiveFileInfo> extractTar(MultipartFile file) {
        List<ArchiveFileInfo> result = new ArrayList<>();

        try {
            ByteArrayInputStream bais = new ByteArrayInputStream(file.getBytes());
            TarArchiveInputStream tis = new TarArchiveInputStream(bais);

            ArchiveEntry entry;
            while ((entry = tis.getNextEntry()) != null) {
                if (!entry.isDirectory()) {
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    byte[] buffer = new byte[8192];
                    int len;
                    while ((len = tis.read(buffer)) > 0) {
                        baos.write(buffer, 0, len);
                    }

                    ArchiveFileInfo info = new ArchiveFileInfo();
                    info.setName(entry.getName());
                    info.setSize(entry.getSize());
                    info.setData(baos.toByteArray());
                    result.add(info);
                }
            }
        } catch (IOException e) {
            log.error("解析 TAR 文件失败：{}", e.getMessage(), e);
        }

        return result;
    }

    /**
     * 解析 GZIP 文件
     */
    private static List<ArchiveFileInfo> extractGzip(MultipartFile file) {
        List<ArchiveFileInfo> result = new ArrayList<>();

        try {
            ByteArrayInputStream bais = new ByteArrayInputStream(file.getBytes());
            GzipCompressorInputStream gis = new GzipCompressorInputStream(bais);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int len;
            while ((len = gis.read(buffer)) > 0) {
                baos.write(buffer, 0, len);
            }

            ArchiveFileInfo info = new ArchiveFileInfo();
            info.setName(file.getOriginalFilename().replace(".gz", "").replace(".tgz", ".tar"));
            info.setSize(baos.size());
            info.setData(baos.toByteArray());
            result.add(info);

        } catch (IOException e) {
            log.error("解析 GZIP 文件失败：{}", e.getMessage(), e);
        }

        return result;
    }

    /**
     * 解析 BZIP2 文件
     */
    private static List<ArchiveFileInfo> extractBzip2(MultipartFile file) {
        List<ArchiveFileInfo> result = new ArrayList<>();

        try {
            ByteArrayInputStream bais = new ByteArrayInputStream(file.getBytes());
            BZip2CompressorInputStream bis = new BZip2CompressorInputStream(bais);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int len;
            while ((len = bis.read(buffer)) > 0) {
                baos.write(buffer, 0, len);
            }

            ArchiveFileInfo info = new ArchiveFileInfo();
            info.setName(file.getOriginalFilename().replace(".bz2", ""));
            info.setSize(baos.size());
            info.setData(baos.toByteArray());
            result.add(info);

        } catch (IOException e) {
            log.error("解析 BZIP2 文件失败：{}", e.getMessage(), e);
        }

        return result;
    }

    /**
     * 解析 XZ 文件
     */
    private static List<ArchiveFileInfo> extractXz(MultipartFile file) {
        List<ArchiveFileInfo> result = new ArrayList<>();

        try {
            ByteArrayInputStream bais = new ByteArrayInputStream(file.getBytes());
            XZCompressorInputStream xzis = new XZCompressorInputStream(bais);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int len;
            while ((len = xzis.read(buffer)) > 0) {
                baos.write(buffer, 0, len);
            }

            ArchiveFileInfo info = new ArchiveFileInfo();
            info.setName(file.getOriginalFilename().replace(".xz", ""));
            info.setSize(baos.size());
            info.setData(baos.toByteArray());
            result.add(info);

        } catch (IOException e) {
            log.error("解析 XZ 文件失败：{}", e.getMessage(), e);
        }

        return result;
    }

    /**
     * 解析 JAR 文件
     */
    private static List<ArchiveFileInfo> extractJar(MultipartFile file) {
        List<ArchiveFileInfo> result = new ArrayList<>();

        try {
            ByteArrayInputStream bais = new ByteArrayInputStream(file.getBytes());
            JarArchiveInputStream jis = new JarArchiveInputStream(bais);

            ArchiveEntry entry;
            while ((entry = jis.getNextEntry()) != null) {
                if (!entry.isDirectory()) {
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    byte[] buffer = new byte[8192];
                    int len;
                    while ((len = jis.read(buffer)) > 0) {
                        baos.write(buffer, 0, len);
                    }

                    ArchiveFileInfo info = new ArchiveFileInfo();
                    info.setName(entry.getName());
                    info.setSize(entry.getSize());
                    info.setData(baos.toByteArray());
                    result.add(info);
                }
            }
        } catch (IOException e) {
            log.error("解析 JAR 文件失败：{}", e.getMessage(), e);
        }

        return result;
    }

    /**
     * 解析 AR 文件
     */
    private static List<ArchiveFileInfo> extractAr(MultipartFile file) {
        List<ArchiveFileInfo> result = new ArrayList<>();

        try {
            ByteArrayInputStream bais = new ByteArrayInputStream(file.getBytes());
            ArArchiveInputStream ais = new ArArchiveInputStream(bais);

            ArchiveEntry entry;
            while ((entry = ais.getNextEntry()) != null) {
                if (!entry.isDirectory()) {
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    byte[] buffer = new byte[8192];
                    int len;
                    while ((len = ais.read(buffer)) > 0) {
                        baos.write(buffer, 0, len);
                    }

                    ArchiveFileInfo info = new ArchiveFileInfo();
                    info.setName(entry.getName());
                    info.setSize(entry.getSize());
                    info.setData(baos.toByteArray());
                    result.add(info);
                }
            }
        } catch (IOException e) {
            log.error("解析 AR 文件失败：{}", e.getMessage(), e);
        }

        return result;
    }

    /**
     * 解析 CPIO 文件
     */
    private static List<ArchiveFileInfo> extractCpio(MultipartFile file) {
        List<ArchiveFileInfo> result = new ArrayList<>();

        try {
            ByteArrayInputStream bais = new ByteArrayInputStream(file.getBytes());
            CpioArchiveInputStream cis = new CpioArchiveInputStream(bais);

            ArchiveEntry entry;
            while ((entry = cis.getNextEntry()) != null) {
                if (!entry.isDirectory()) {
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    byte[] buffer = new byte[8192];
                    int len;
                    while ((len = cis.read(buffer)) > 0) {
                        baos.write(buffer, 0, len);
                    }

                    ArchiveFileInfo info = new ArchiveFileInfo();
                    info.setName(entry.getName());
                    info.setSize(entry.getSize());
                    info.setData(baos.toByteArray());
                    result.add(info);
                }
            }
        } catch (IOException e) {
            log.error("解析 CPIO 文件失败：{}", e.getMessage(), e);
        }

        return result;
    }

    /**
     * 解析 7z 文件
     * 使用 Seven-Zip-JBinding 库支持
     * 注意：Seven-Zip-JBinding 需要本地文件，无法直接从内存解析
     */
    private static List<ArchiveFileInfo> extract7z(MultipartFile file) {
        List<ArchiveFileInfo> result = new ArrayList<>();

        java.io.File tempFile = null;
        java.io.RandomAccessFile randomAccessFile = null;
        try {
            // 创建临时文件（Seven-Zip-JBinding 需要本地文件）
            tempFile = java.io.File.createTempFile("sevenzip", ".7z");
            file.transferTo(tempFile);

            // 使用 RandomAccessFile 包装
            randomAccessFile = new java.io.RandomAccessFile(tempFile, "r");

            // 使用 Seven-Zip-JBinding 解析 7z 文件
            net.sf.sevenzipjbinding.IInArchive inArchive = net.sf.sevenzipjbinding.SevenZip.openInArchive(
                null, new net.sf.sevenzipjbinding.impl.RandomAccessFileInStream(randomAccessFile));

            // 获取文件数量
            int itemCount = inArchive.getNumberOfItems();

            for (int i = 0; i < itemCount; i++) {
                // 获取文件属性
                boolean isFolder = inArchive.getProperty(i, net.sf.sevenzipjbinding.PropID.IS_FOLDER).equals(Boolean.TRUE);

                if (!isFolder) {
                    // 获取文件名
                    String fileName = (String) inArchive.getProperty(i, net.sf.sevenzipjbinding.PropID.PATH);
                    long fileSize = (Long) inArchive.getProperty(i, net.sf.sevenzipjbinding.PropID.SIZE);

                    // 提取文件内容
                    final java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
                    net.sf.sevenzipjbinding.IArchiveExtractCallback callback = new net.sf.sevenzipjbinding.IArchiveExtractCallback() {
                        @Override
                        public void setCompleted(long completeValue) {
                        }

                        @Override
                        public void setTotal(long total) {
                        }

                        @Override
                        public net.sf.sevenzipjbinding.ISequentialOutStream getStream(int index, net.sf.sevenzipjbinding.ExtractAskMode extractAskMode) {
                            return data1 -> {
                                try {
                                    baos.write(data1);
                                } catch (Exception e) {
                                    log.error("写入数据失败：{}", e.getMessage());
                                }
                                return data1.length;
                            };
                        }

                        @Override
                        public void prepareOperation(net.sf.sevenzipjbinding.ExtractAskMode extractAskMode) {
                        }

                        @Override
                        public void setOperationResult(net.sf.sevenzipjbinding.ExtractOperationResult extractOperationResult) {
                            if (extractOperationResult != net.sf.sevenzipjbinding.ExtractOperationResult.OK) {
                                log.error("提取文件失败：{}", extractOperationResult);
                            }
                        }
                    };

                    inArchive.extract(new int[]{i}, false, callback);

                    ArchiveFileInfo info = new ArchiveFileInfo();
                    info.setName(fileName);
                    info.setSize(fileSize);
                    info.setData(baos.toByteArray());
                    result.add(info);
                }
            }

            inArchive.close();
            log.info("7z 文件解析完成，共 {} 个文件", result.size());

        } catch (Exception e) {
            log.error("解析 7z 文件失败：{}", e.getMessage(), e);
        } finally {
            // 关闭 RandomAccessFile
            if (randomAccessFile != null) {
                try {
                    randomAccessFile.close();
                } catch (Exception e) {
                    log.error("关闭 RandomAccessFile 失败：{}", e.getMessage());
                }
            }
            // 删除临时文件
            if (tempFile != null && tempFile.exists()) {
                tempFile.delete();
            }
        }

        return result;
    }

    /**
     * 解析 RAR 文件
     * 使用 JunRAR 库支持
     */
    private static List<ArchiveFileInfo> extractRar(MultipartFile file) {
        List<ArchiveFileInfo> result = new ArrayList<>();

        java.io.File tempFile = null;
        try {
            // 将 MultipartFile 转换为临时 File（JunRAR 需要 File 对象）
            tempFile = java.io.File.createTempFile("rar", ".rar");
            file.transferTo(tempFile);

            // 使用 JunRAR 解析 RAR 文件
            com.github.junrar.Archive archive = new com.github.junrar.Archive(tempFile);

            if (archive != null) {
                com.github.junrar.rarfile.FileHeader fileHeader = archive.nextFileHeader();

                while (fileHeader != null) {
                    if (!fileHeader.isDirectory()) {
                        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
                        archive.extractFile(fileHeader, baos);

                        ArchiveFileInfo info = new ArchiveFileInfo();
                        info.setName(fileHeader.getFileNameString());
                        info.setSize(baos.size());
                        info.setData(baos.toByteArray());
                        result.add(info);
                    }

                    fileHeader = archive.nextFileHeader();
                }
            }

            archive.close();

            log.info("RAR 文件解析完成，共 {} 个文件", result.size());

        } catch (Exception e) {
            log.error("解析 RAR 文件失败：{}", e.getMessage(), e);
        } finally {
            // 删除临时文件
            if (tempFile != null && tempFile.exists()) {
                tempFile.delete();
            }
        }

        return result;
    }

    /**
     * 压缩文件信息类
     */
    public static class ArchiveFileInfo {
        private String name;
        private long size;
        private byte[] data;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public long getSize() {
            return size;
        }

        public void setSize(long size) {
            this.size = size;
        }

        public byte[] getData() {
            return data;
        }

        public void setData(byte[] data) {
            this.data = data;
        }

        /**
         * 获取文件扩展名
         */
        public String getExtension() {
            if (name != null && name.contains(".")) {
                return name.substring(name.lastIndexOf(".") + 1).toLowerCase();
            }
            return "";
        }

        /**
         * 检查文件名是否包含指定关键字（模糊匹配）
         * 支持去除空格、下划线、连字符等特殊字符后进行匹配
         */
        public boolean containsKeyword(String keyword) {
            if (name == null || keyword == null) {
                return false;
            }

            // 规范化文件名：去除空格、下划线、连字符，转换为小写（保留点号）
            String normalizedName = name.toLowerCase()
                    .replaceAll("[\\s\\-_]", "");

            // 规范化关键字：转换为小写
            String normalizedKeyword = keyword.toLowerCase()
                    .replaceAll("[\\s\\-_]", "");

            boolean contains = normalizedName.contains(normalizedKeyword);

            if (contains) {
                log.debug("文件名 '{}' 匹配关键字 '{}' (规范化后: '{}' 包含 '{}')",
                        name, keyword, normalizedName, normalizedKeyword);
            } else {
                log.debug("文件名 '{}' 不匹配关键字 '{}' (规范化后: '{}' 不包含 '{}')",
                        name, keyword, normalizedName, normalizedKeyword);
            }

            return contains;
        }

        @Override
        public String toString() {
            return "ArchiveFileInfo{" +
                    "name='" + name + '\'' +
                    ", size=" + size +
                    ", extension=" + getExtension() +
                    '}';
        }
    }
}
