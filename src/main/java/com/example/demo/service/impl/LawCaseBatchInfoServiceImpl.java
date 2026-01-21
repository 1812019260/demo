package com.example.demo.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.demo.bean.FileDownloadResult;
import com.example.demo.bean.Response;
import com.example.demo.bean.vo.LawCaseBatchInfoStateVo;
import com.example.demo.bean.vo.LawCaseBatchInfoVo;
import com.example.demo.config.EmailConfig;
import com.example.demo.config.SftpConfig;
import com.example.demo.dto.LawCaseBatchInfoRequestDto;
import com.example.demo.entity.LawCaseBatchInfo;
import com.example.demo.mapper.LawCaseBatchInfoMapper;
import com.example.demo.service.ILawCaseBatchInfoService;
import com.example.demo.util.ArchiveExtractor;
import com.example.demo.util.FileUtil;
import com.example.demo.util.PdfConverter;
import com.example.demo.util.SftpUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * <p>
 * 签章文件表 服务实现类
 * </p>
 *
 * @author zxd
 * @since 2026-01-19
 */
@Service
@Slf4j
public class LawCaseBatchInfoServiceImpl extends ServiceImpl<LawCaseBatchInfoMapper, LawCaseBatchInfo> implements ILawCaseBatchInfoService {

    @Autowired
    private LawCaseBatchInfoMapper lawCaseBatchInfoMapper;

    @Autowired
    private SftpConfig sftpConfig;

    @Autowired
    private EmailConfig emailConfig;

    @Autowired
    private JavaMailSender mailSender;

    /**
     * 上传压缩文件
     *
     * @param dto  批次信息
     * @param file 上传的压缩文件
     * @return Response 包含批次信息列表或错误信息
     */
    @Override
    public Response<List<LawCaseBatchInfoVo>> uploadUnstampedFile(LawCaseBatchInfoRequestDto dto, MultipartFile file) {
        log.info("开始上传压缩文件，请求参数：{}，文件名：{}", dto, file != null ? file.getOriginalFilename() : "无文件");

        // 检查文件是否为空
        if (file == null || file.isEmpty()) {
            log.warn("上传文件为空，无法上传压缩文件");
            return Response.fail("上传失败：文件为空");
        }

        try {

            // 1. 解析压缩文件
            List<ArchiveExtractor.ArchiveFileInfo> extractedFiles = ArchiveExtractor.extract(file);

            // 2. 检查文件数量（必须 >= 3）
            if (extractedFiles.size() < 3) {
                log.warn("压缩包内文件数量不足，当前数量：{}，要求：>= 3", extractedFiles.size());
                return Response.fail("上传失败：压缩包内文件数量不足（至少需要3个文件）");
            }

            log.info("压缩包解析成功，共提取 {} 个文件", extractedFiles.size());

            // 3. 检查是否包含必需的文件关键字
            boolean has起诉状 = false;
            boolean has委托书 = false;
            boolean has申请书 = false;

            log.info("开始检查提取到的文件，共 {} 个文件", extractedFiles.size());
            for (ArchiveExtractor.ArchiveFileInfo fileInfo : extractedFiles) {
                String fileName = fileInfo.getName();
                log.info("检查文件名：'{}'", fileName);
                log.info("  - 包含'起诉状'：{}", fileName.contains("起诉状"));
                log.info("  - 包含'委托书'：{}", fileName.contains("委托书"));
                log.info("  - 包含'申请书'：{}", fileName.contains("申请书"));

                if (fileInfo.containsKeyword("起诉状")) {
                    has起诉状 = true;
                    log.info("找到包含'起诉状'的文件：{}", fileInfo.getName());
                }
                if (fileInfo.containsKeyword("委托书")) {
                    has委托书 = true;
                    log.info("找到包含'委托书'的文件：{}", fileInfo.getName());
                }
                if (fileInfo.containsKeyword("申请书")) {
                    has申请书 = true;
                    log.info("找到包含'申请书'的文件：{}", fileInfo.getName());
                }
            }

            if (!has起诉状 || !has委托书 || !has申请书) {
                log.warn("缺少必需文件：起诉状={}, 委托书={}, 申请书={}", has起诉状, has委托书, has申请书);
                return Response.fail("上传失败：压缩包必须包含'起诉状'、'委托书'、'申请书'三个文件");
            }

            log.info("必需文件检查通过");

            // 4. 生成批次ID
            String batchId = dto.getBatchId();
            if (batchId == null || batchId.isEmpty()) {
                batchId = generateBatchId();
            }

            // 5. 处理文件转换
            List<ArchiveExtractor.ArchiveFileInfo> processedFiles = new ArrayList<>();
            for (ArchiveExtractor.ArchiveFileInfo fileInfo : extractedFiles) {
                String fileName = fileInfo.getName();
                byte[] fileData = fileInfo.getData();

                log.info("开始处理文件：'{}'，数据大小：{} 字节", fileName, fileData != null ? fileData.length : 0);

                // 检查并转换文件格式
                byte[] finalData = fileData;
                String finalFileName = fileName;

                if (!PdfConverter.isPdf(fileName)) {
                    // 非 PDF 格式，检查是否需要转换
                    if (PdfConverter.canConvertToPdf(fileName)) {
                        // 可以转换的文件（DOCX、图片等）
                        log.info("尝试转换文件 {} 为 PDF", fileName);
                        byte[] pdfData = PdfConverter.convertToPdf(fileName, fileData);
                        if (pdfData != null) {
                            // 转换成功，使用 PDF 数据
                            finalData = pdfData;
                            // 修改文件扩展名为 .pdf
                            String baseName = fileName.contains(".") ? fileName.substring(0, fileName.lastIndexOf(".")) : fileName;
                            finalFileName = baseName + ".pdf";
                            log.info("文件 {} 转换为 PDF 成功，原始大小：{} 字节，转换后大小：{} 字节", 
                                    fileName, fileData.length, pdfData.length);
                        } else {
                            // 转换失败，保留原文件
                            log.warn("文件 {} 转换为 PDF 失败，保留原格式", fileName);
                            finalData = fileData;
                            finalFileName = fileName;
                        }
                    } else {
                        // 不需要转换的文件（如 TXT），保留原格式
                        log.info("文件 {} 不需要转换，保留原格式", fileName);
                        finalData = fileData;
                        finalFileName = fileName;
                    }
                } else {
                    log.info("文件 {} 已经是 PDF 格式，无需转换，数据大小：{} 字节", fileName, fileData.length);
                }

                // 添加到处理后的文件列表
                ArchiveExtractor.ArchiveFileInfo processedFile = new ArchiveExtractor.ArchiveFileInfo();
                processedFile.setName(finalFileName);
                processedFile.setData(finalData);
                processedFile.setSize(finalData.length);
                processedFiles.add(processedFile);
                log.info("文件处理完成：'{}'，最终大小：{} 字节", finalFileName, finalData.length);
            }

            if (processedFiles.isEmpty()) {
                log.warn("没有成功处理任何文件");
                return Response.fail("上传失败：没有成功处理任何文件");
            }

            // 6. 将所有文件打包成 ZIP
            byte[] zipData = createZipFile(processedFiles);
            log.info("成功创建 ZIP 文件，包含 {} 个文件，大小：{} 字节", processedFiles.size(), zipData.length);

            // 7. 上传 ZIP 文件到 SFTP（带重试机制）
            SftpUtil sftpUtil = new SftpUtil(sftpConfig.getHost(), sftpConfig.getPort(), sftpConfig.getUsername(), sftpConfig.getPassword());
            try {
                sftpUtil.connectWithRetry(); // 使用带重试机制的连接方法

                String sftpDirectory = sftpConfig.getSftpReceivePath();
                String zipFileName = batchId + ".zip";

                log.info("准备上传 ZIP 文件到 SFTP 服务器，目录: {}, 文件名: {}", sftpDirectory, zipFileName);
                sftpUtil.uploadBytesWithRetry(sftpDirectory, zipFileName, zipData);
                log.info("ZIP 文件上传成功：{}", zipFileName);

                // 8. 创建实体对象并保存到数据库
                LawCaseBatchInfo entity = new LawCaseBatchInfo();
                BeanUtils.copyProperties(dto, entity);
                entity.setBatchId(batchId);
                entity.setCreateTime(LocalDateTime.now());
                entity.setUpdateTime(LocalDateTime.now());
                entity.setBatchStatus("待签章文件上传完成");
                entity.setIsSign(1);
                entity.setIsDelete(0);

                // 保存上传地址
                String uploadAddress = sftpDirectory + "/" + zipFileName;
                entity.setUploadAddress(uploadAddress);

                // 保存到数据库
                lawCaseBatchInfoMapper.insert(entity);

                 log.info("数据库记录保存成功，批次ID：{}，SFTP路径：{}", batchId, uploadAddress);
                log.info("共处理 {} 个文件，打包成 ZIP 文件", processedFiles.size());

                // 发送邮件通知
                sendUploadNotification(dto, batchId);

                // 转换为VO并返回
                LawCaseBatchInfoVo vo = convertToVo(entity);
                List<LawCaseBatchInfoVo> result = new ArrayList<>();
                result.add(vo);

                return Response.success(result);

            } catch (com.jcraft.jsch.JSchException e) {
                log.error("SFTP连接失败：{}", e.getMessage(), e);
                return Response.fail("上传失败：SFTP服务器失败，请联系管理员");
            } finally {
                if (sftpUtil != null) {
                    sftpUtil.disconnect();
                }
            }

        } catch (Exception e) {
            log.error("上传压缩文件失败：{}", e.getMessage(), e);
            return Response.fail("上传失败：" + e.getMessage());
        }

    }

    /**
     * 创建 ZIP 文件（在内存中）
     *
     * @param files 文件列表
     * @return ZIP 文件的字节数组
     */
    private byte[] createZipFile(List<ArchiveExtractor.ArchiveFileInfo> files) throws java.io.IOException {
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream zos =
            new org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream(baos);

        zos.setEncoding("UTF-8");

        zos.setUseLanguageEncodingFlag(true);

        try {
            for (ArchiveExtractor.ArchiveFileInfo file : files) {
                String fileName = file.getName();
                // 确保文件名使用正确的编码
                org.apache.commons.compress.archivers.zip.ZipArchiveEntry entry =
                    new org.apache.commons.compress.archivers.zip.ZipArchiveEntry(fileName);
                entry.setSize(file.getData().length);
                zos.putArchiveEntry(entry);
                zos.write(file.getData());
                zos.closeArchiveEntry();
                log.info("已添加文件到 ZIP：{}，大小：{} 字节", fileName, file.getSize());
            }
            zos.finish();
            byte[] zipData = baos.toByteArray();
            log.info("ZIP 文件创建完成，总大小：{} 字节", zipData.length);
            return zipData;
        } finally {
            zos.close();
            baos.close();
        }
    }

    /**
     * 生成批次ID
     *
     * @return 生成的批次ID
     */
    private String generateBatchId() {
        // 使用UUID生成唯一标识，并加上首尾字母
        String uuid = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        char firstChar = (char) ('A' + (int) (Math.random() * 26)); // 随机大写字母
        char lastChar = (char) ('a' + (int) (Math.random() * 26)); // 随机小写字母
        return firstChar + uuid + lastChar;
    }

    /**
     * 将实体转换为VO
     *
     * @param entity 实体对象
     * @return VO对象
     */
    private LawCaseBatchInfoVo convertToVo(LawCaseBatchInfo entity) {
        LawCaseBatchInfoVo vo = new LawCaseBatchInfoVo();
        vo.setBatchId(entity.getBatchId());
        vo.setBatchName(entity.getBatchName());
        vo.setBatchStatus(entity.getBatchStatus());
        vo.setCreateTime(entity.getCreateTime());
        vo.setCreateUserName(entity.getCreateUserName());
        vo.setSenderEmail(entity.getSenderEmail());
        vo.setRecipientEmail(entity.getRecipientEmail());
        vo.setEmailContentP(entity.getEmailContentP());
        vo.setEmailSendTime(entity.getEmailSendTime());
        vo.setDownloadName(entity.getDownloadName());
        vo.setDownloadCount(entity.getDownloadCount());
        vo.setUpdateTime(entity.getUpdateTime());
        vo.setUpdateUserName(entity.getUpdateUserName());
        vo.setIsSign(entity.getIsSign());
        return vo;
    }

    /**
     * 发送签章文件上传通知邮件
     *
     * @param dto     批次信息
     * @param batchId 批次ID
     */
    private void sendUploadNotification(LawCaseBatchInfoRequestDto dto, String batchId) {
        // 检查是否启用邮件功能
        if (emailConfig.getEnabled() == null || !emailConfig.getEnabled()) {
            log.info("邮件功能未启用，跳过发送邮件");
            return;
        }

        try {
            // 获取收件人邮箱
            String toEmail = dto.getRecipientEmail();
            if (toEmail == null || toEmail.isEmpty()) {
                log.warn("收件人邮箱为空，无法发送邮件");
                return;
            }

            // 创建邮件消息
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(toEmail);
            message.setFrom("z1812019260@163.com");

            // 设置邮件主题
            String subject = emailConfig.getSubject();
            if (subject == null || subject.isEmpty()) {
                subject = "签章文件上传通知";
            }
            message.setSubject(subject);

            // 构建邮件内容
            StringBuilder content = new StringBuilder();
            content.append("尊敬的用户：\n\n");
            content.append("您好！\n\n");
            content.append("签章文件已成功上传，详情如下：\n\n");
            content.append("----------------------------------------\n");
            content.append("批次ID：").append(batchId).append("\n");
            content.append("批次名称：").append(dto.getBatchName() != null ? dto.getBatchName() : "未命名").append("\n");
            content.append("创建人：").append(dto.getCreateUserName() != null ? dto.getCreateUserName() : "未知").append("\n");
            content.append("上传时间：").append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))).append("\n");
            content.append("----------------------------------------\n\n");

            // 添加自定义邮件内容
            if (dto.getEmailContentP() != null && !dto.getEmailContentP().isEmpty()) {
                content.append(dto.getEmailContentP()).append("\n\n");
            }

            content.append("请及时处理签章文件。\n\n");
            content.append("此邮件由系统自动发送，请勿回复。\n");

            message.setText(content.toString());

            // 发送邮件
            mailSender.send(message);

            log.info("邮件发送成功，收件人：{}，批次ID：{}", toEmail, batchId);

            // 更新邮件发送时间
            dto.setEmailSendTime(LocalDateTime.now());
        } catch (Exception e) {
            log.error("邮件发送失败：{}", e.getMessage(), e);
        }
    }

    /**
     * 上传签章文件
     *
     * @param dto  批次信息（必须包含batchId）
     * @param file 签章后的文件
     * @return 批次信息列表
     */
    @Override
    public Response<List<LawCaseBatchInfoVo>> uploadSealFile(LawCaseBatchInfoRequestDto dto, MultipartFile file) {
        log.info("开始上传签章文件，批次ID：{}，文件名：{}", dto.getBatchId(), file.getOriginalFilename());

        // 验证批次ID
        if (dto.getBatchId() == null || dto.getBatchId().isEmpty()) {
            log.warn("批次ID为空，无法上传签章文件");
            return Response.fail("上传失败：批次ID不能为空");
        }

        // 验证文件
        if (file == null || file.isEmpty()) {
            log.warn("上传文件为空，无法上传签章文件");
            return Response.fail("上传失败：文件为空");
        }

        SftpUtil sftpUtil = null;
        try {
            // 从数据库查询批次信息（根据batch_id查询）
            LawCaseBatchInfo entity = lawCaseBatchInfoMapper.selectOne(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<LawCaseBatchInfo>().eq("batch_id", dto.getBatchId()).eq("is_delete", 0));
            if (entity == null) {
                log.warn("批次不存在，批次ID：{}", dto.getBatchId());
                return Response.fail("上传失败：批次不存在");
            }

            // 检查是否已经完成签章
            if (entity.getIsSign() != null && entity.getIsSign() == 0) {
                log.warn("该批次已完成签章，不能重复上传签章文件，批次ID：{}", dto.getBatchId());
                return Response.fail("上传失败：该批次已完成签章，不能重复上传");
            }

            // 连接SFTP服务器
            sftpUtil = new SftpUtil(sftpConfig.getHost(), sftpConfig.getPort(), sftpConfig.getUsername(), sftpConfig.getPassword());
            sftpUtil.connectWithRetry(); // 使用带重试机制的连接方法

            // 步骤1: 先将上传文件读取到内存中（避免临时文件被删除后无法读取）
            byte[] fileData;
            try {
                fileData = file.getBytes();
                log.info("上传文件读取成功，大小：{} 字节", fileData.length);
            } catch (java.io.IOException e) {
                log.error("读取上传文件失败：{}", e.getMessage(), e);
                return Response.fail("上传失败：读取上传文件失败");
            }

            // 步骤2: 获取待签章文件列表
            List<String> unstampedFileNames = getUnstampedFileNames(sftpUtil, entity);
            if (unstampedFileNames == null || unstampedFileNames.isEmpty()) {
                log.error("无法获取待签章文件列表，批次ID：{}", dto.getBatchId());
                return Response.fail("上传失败：无法获取待签章文件列表");
            }

            log.info("待签章文件列表（共{}个）：{}", unstampedFileNames.size(), unstampedFileNames);

            // 步骤3: 解析上传的签章文件，获取文件列表（使用内存中的字节数组）
            List<String> sealedFileNames = getZipFileNames(fileData);
            if (sealedFileNames == null || sealedFileNames.isEmpty()) {
                log.error("无法解析签章文件，批次ID：{}", dto.getBatchId());
                return Response.fail("上传失败：无法解析签章文件，请确保上传的是有效的ZIP压缩包");
            }

            log.info("签章文件列表（共{}个）：{}", sealedFileNames.size(), sealedFileNames);

            // 步骤3: 验证签章文件必须包含所有待签章文件
            List<String> missingFiles = validateFileNames(unstampedFileNames, sealedFileNames);
            if (!missingFiles.isEmpty()) {
                log.warn("签章文件缺少必需文件：{}", missingFiles);
                // 提取文件名（去掉路径和扩展名）用于显示
                List<String> displayNames = new ArrayList<>();
                for (String fileName : missingFiles) {
                    String name = fileName.substring(fileName.lastIndexOf("/") + 1);
                    String baseName = name.contains(".") ? name.substring(0, name.lastIndexOf(".")) : name;
                    displayNames.add(baseName);
                }
                String errorMsg = "上传失败：签章文件缺少";
                return Response.fail(errorMsg);
            }

            log.info("文件验证通过，签章文件包含所有待签章文件");

            // 步骤4: 上传签章文件（带重试机制，使用内存中的字节数组）
            String originalFilename = file.getOriginalFilename();
            String extension = FileUtil.getExtension(originalFilename);
            String fileName = dto.getBatchId() + (extension.isEmpty() ? "" : "." + extension);

            try {
                sftpUtil.uploadBytesWithRetry(sftpConfig.getSftpReturnPath(), fileName, fileData);
                log.info("签章文件上传成功：{}", fileName);
            } catch (Exception e) {
                log.error("上传签章文件失败：{}", e.getMessage(), e);
                return Response.fail("上传失败：" + e.getMessage());
            }
            log.info("签章文件上传成功：{}", fileName);

            // 步骤5: 更新批次信息
            entity.setUploadAddressOver(sftpConfig.getSftpReturnPath() + "/" + fileName);
            entity.setBatchStatus("签章文件上传完成");
            entity.setIsSign(0); // 0-完成签章
            entity.setUpdateTime(LocalDateTime.now());
            lawCaseBatchInfoMapper.updateById(entity);

            log.info("批次信息更新成功，批次ID：{}", dto.getBatchId());

            // 转换为VO并返回
            LawCaseBatchInfoVo vo = convertToVo(entity);
            List<LawCaseBatchInfoVo> result = new ArrayList<>();
            result.add(vo);

            return Response.success(result);
        } catch (com.jcraft.jsch.JSchException e) {
            log.error("SFTP连接失败：{}", e.getMessage(), e);
            return Response.fail("上传失败：SFTP服务器连接失败，请联系管理员");
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            if (sftpUtil != null) {
                sftpUtil.disconnect();
            }
        }
    }

    /**
     * 从SFTP获取待签章文件的文件名列表
     */
    private List<String> getUnstampedFileNames(SftpUtil sftpUtil, LawCaseBatchInfo entity) throws Exception {
        String uploadAddress = entity.getUploadAddress();
        if (uploadAddress == null || uploadAddress.isEmpty()) {
            log.warn("待签章文件地址为空，批次ID：{}", entity.getBatchId());
            return null;
        }

        // 从upload_address中提取文件名
        String fileName = uploadAddress.substring(uploadAddress.lastIndexOf("/") + 1);
        String directory = sftpConfig.getSftpReceivePath();

        log.info("从SFTP下载待签章文件，目录: {}, 文件名: {}", directory, fileName);

        // 下载文件到内存
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        InputStream inputStream = sftpUtil.downloadAsStream(directory, fileName);
        if (inputStream == null) {
            log.warn("无法下载待签章文件，批次ID：{}", entity.getBatchId());
            return null;
        }

        try {
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                baos.write(buffer, 0, bytesRead);
            }
            baos.close();
            inputStream.close();

            // 解析ZIP文件，获取文件名列表
            byte[] zipData = baos.toByteArray();
            java.io.ByteArrayInputStream bais = new java.io.ByteArrayInputStream(zipData);
            java.util.zip.ZipInputStream zis = new java.util.zip.ZipInputStream(bais);

            List<String> fileNames = new ArrayList<>();
            java.util.zip.ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (!entry.isDirectory()) {
                    fileNames.add(entry.getName());
                }
                zis.closeEntry();
            }
            zis.close();

            return fileNames;
        } catch (Exception e) {
            log.error("解析待签章文件失败，批次ID：{}，错误：{}", entity.getBatchId(), e.getMessage(), e);
            return null;
        }
    }

    /**
     * 从上传的ZIP文件字节数组中获取文件名列表
     */
    private List<String> getZipFileNames(byte[] fileData) throws Exception {
        List<String> fileNames = new ArrayList<>();
        
        // 使用ArchiveExtractor解析ZIP文件，支持中文文件名
        List<ArchiveExtractor.ArchiveFileInfo> extractedFiles = ArchiveExtractor.extract(fileData);
        
        if (extractedFiles != null) {
            for (ArchiveExtractor.ArchiveFileInfo fileInfo : extractedFiles) {
                fileNames.add(fileInfo.getName());
            }
        }
        
        return fileNames;
    }

    /**
     * 验证签章文件是否包含所有待签章文件
     * 签章文件可以比待签章文件多，但不能少
     */
    private List<String> validateFileNames(List<String> unstampedFiles, List<String> sealedFiles) {
        List<String> missingFiles = new ArrayList<>();

        for (String unstampedFile : unstampedFiles) {
            boolean found = false;
            for (String sealedFile : sealedFiles) {
                // 去除路径，只比较文件名
                String unstampedName = unstampedFile.substring(unstampedFile.lastIndexOf("/") + 1);
                String sealedName = sealedFile.substring(sealedFile.lastIndexOf("/") + 1);

                // 去除扩展名进行比较
                String unstampedBase = unstampedName.contains(".") ? unstampedName.substring(0, unstampedName.lastIndexOf(".")) : unstampedName;
                String sealedBase = sealedName.contains(".") ? sealedName.substring(0, sealedFile.lastIndexOf(".")) : sealedFile;

                if (unstampedBase.equals(sealedBase)) {
                    found = true;
                    break;
                }
            }

            if (!found) {
                missingFiles.add(unstampedFile);
            }
        }

        return missingFiles;
    }

    /**
     * 撤销待签章文件（逻辑删除，不删除远程仓库数据）
     *
     * @param dto 批次信息（必须包含batchId）
     * @return 操作结果
     */
    @Override
    public Response<?> cancelUnstampedFile(LawCaseBatchInfoRequestDto dto) {
        log.info("开始撤销待签章文件，批次ID：{}", dto.getBatchId());

        // 验证批次ID
        if (dto.getBatchId() == null || dto.getBatchId().isEmpty()) {
            log.warn("批次ID为空，无法撤销");
            return Response.fail("批次ID不能为空");
        }

        // 从数据库查询批次信息
        LawCaseBatchInfo entity = lawCaseBatchInfoMapper.selectOne(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<LawCaseBatchInfo>().eq("batch_id", dto.getBatchId()).eq("is_delete", 0));

        if (entity == null) {
            log.warn("批次不存在，批次ID：{}", dto.getBatchId());
            return Response.fail("批次不存在");
        }

        // 检查是否已经完成签章
        if (entity.getIsSign() != null && entity.getIsSign() == 0) {
            log.warn("该批次已完成签章，不能撤销待签章文件，批次ID：{}", dto.getBatchId());
            return Response.fail("该批次已完成签章，不能撤销待签章文件");
        }

        // 逻辑删除（只修改is_delete字段，不删除远程仓库数据）
        log.info("更新前 - 批次ID：{}，isDelete：{}，isSign：{}", dto.getBatchId(), entity.getIsDelete(), entity.getIsSign());
        entity.setIsDelete(1);
        entity.setUpdateTime(LocalDateTime.now());
        entity.setUpdateUserName(dto.getUpdateUserName());
        int updateResult = lawCaseBatchInfoMapper.updateById(entity);
        log.info("更新结果：{}，更新后 - 批次ID：{}，isDelete：{}，isSign：{}", updateResult, dto.getBatchId(), entity.getIsDelete(), entity.getIsSign());

        log.info("待签章文件撤销成功，批次ID：{}", dto.getBatchId());

        return Response.success("撤销成功");
    }

    /**
     * 撤销签章文件（修改批次状态和完成签章文件保存地址）
     *
     * @param dto 批次信息（必须包含batchId）
     * @return 操作结果，失败返回null
     */
    @Override
    public Response<?> cancelSealFile(LawCaseBatchInfoRequestDto dto) {
        log.info("开始撤销签章文件，批次ID：{}", dto.getBatchId());

        // 验证批次ID
        if (dto.getBatchId() == null || dto.getBatchId().isEmpty()) {
            log.warn("批次ID为空，无法撤销");
            return Response.fail("批次id为空,撤销失败");

        }

        // 从数据库查询批次信息
        LawCaseBatchInfo entity = lawCaseBatchInfoMapper.selectOne(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<LawCaseBatchInfo>().eq("batch_id", dto.getBatchId()).eq("is_delete", 0));

        if (entity == null) {
            log.warn("批次不存在，批次ID：{}", dto.getBatchId());
            return Response.fail("批次不存在,撤销失败");

        }

        // 检查是否已完成签章
        if (entity.getIsSign() == null || entity.getIsSign() == 1) {
            log.warn("该批次尚未完成签章，不能撤销签章文件，批次ID：{}", dto.getBatchId());
            return Response.fail("该批次尚未完成签章，不能撤销签章文件");
        }

        SftpUtil sftpUtil = null;
        try {
            // 删除远程签章文件
            String uploadAddressOver = entity.getUploadAddressOver();
            if (uploadAddressOver != null && !uploadAddressOver.isEmpty()) {
                sftpUtil = new SftpUtil(sftpConfig.getHost(), sftpConfig.getPort(), sftpConfig.getUsername(), sftpConfig.getPassword());
                sftpUtil.connect();

                // 提取文件名
                String fileName = uploadAddressOver.substring(uploadAddressOver.lastIndexOf("/") + 1);
                String directory = sftpConfig.getSftpReturnPath();

                log.info("准备删除远程签章文件，目录: {}, 文件名: {}", directory, fileName);

                // 删除文件
                sftpUtil.delete(directory, fileName);
                log.info("远程签章文件删除成功，批次ID：{}", dto.getBatchId());
            }

            // 修改批次状态和完成签章文件保存地址（不修改逻辑删除字段）
            // 使用 UpdateWrapper 强制更新 uploadAddressOver 为 null
            com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<LawCaseBatchInfo> updateWrapper = new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<>();
            updateWrapper.eq("batch_id", dto.getBatchId()).set("batch_status", "待签章文件上传完成").set("upload_address_over", ' ').set("is_sign", 1).set("update_time", LocalDateTime.now()).set("update_user_name", dto.getUpdateUserName());

            int updateResult = lawCaseBatchInfoMapper.update(null, updateWrapper);
            log.info("数据库更新结果：{}，批次ID：{}，uploadAddressOver已清空", updateResult, dto.getBatchId());

            // 重新查询实体以获取更新后的数据
            entity = lawCaseBatchInfoMapper.selectOne(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<LawCaseBatchInfo>().eq("batch_id", dto.getBatchId()).eq("is_delete", 0));

            log.info("签章文件撤销成功，批次ID：{}", dto.getBatchId());

            return Response.success("撤销成功");
        } catch (Exception e) {
            log.error("撤销签章文件失败，批次ID：{}，错误：{}", dto.getBatchId(), e.getMessage(), e);
            return Response.fail("撤销失败：" + e.getMessage());
        } finally {
            if (sftpUtil != null) {
                sftpUtil.disconnect();
            }
        }
    }

    /**
     * 下载未签章文件
     *
     * @param dto 批次信息（必须包含batchId）
     * @return ResponseEntity 包含二进制文件数据
     */
    @Override
    public ResponseEntity<byte[]> downloadUnstampedFile(LawCaseBatchInfoRequestDto dto) {
        log.info("开始下载未签章文件，批次ID：{}", dto.getBatchId());

        // 验证批次ID
        if (dto.getBatchId() == null || dto.getBatchId().isEmpty()) {
            log.warn("批次ID为空，无法下载");
            return buildErrorResponse(400, "下载失败：批次ID不能为空");
        }

        // 从数据库查询批次信息
        LawCaseBatchInfo entity = lawCaseBatchInfoMapper.selectOne(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<LawCaseBatchInfo>().eq("batch_id", dto.getBatchId()).eq("is_delete", 0));

        if (entity == null) {
            log.warn("批次不存在，批次ID：{}", dto.getBatchId());
            return buildErrorResponse(404, "下载失败：批次不存在");
        }

        // 检查未签章文件保存地址
        if (entity.getUploadAddress() == null || entity.getUploadAddress().isEmpty()) {
            log.warn("未签章文件地址为空，批次ID：{}", dto.getBatchId());
            return buildErrorResponse(404, "下载失败：文件地址为空");
        }

        // 获取文件下载结果
        FileDownloadResult result;
        try {
            result = downloadUnstampedFileBytes(dto);
        } catch (Exception e) {
            log.error("下载失败，批次ID：{}，错误：{}", dto.getBatchId(), e.getMessage(), e);
            return buildErrorResponse(500, "下载失败：" + e.getMessage());
        }

        if (result == null || result.getBytes() == null) {
            log.warn("下载失败，批次ID：{}", dto.getBatchId());
            return buildErrorResponse(404, "下载失败：文件不存在或无法访问");
        }

        // 构建文件名：batchName为空则使用batchId
        String baseFileName = (entity.getBatchName() != null && !entity.getBatchName().isEmpty()) ? entity.getBatchName() : entity.getBatchId();
        String fileName = baseFileName + ".zip";
        String encodedFileName;
        try {
            encodedFileName = java.net.URLEncoder.encode(fileName, java.nio.charset.StandardCharsets.UTF_8.toString()).replaceAll("\\+", "%20");
        } catch (java.io.UnsupportedEncodingException e) {
            log.error("文件名编码失败，批次ID：{}，错误：{}", dto.getBatchId(), e.getMessage(), e);
            encodedFileName = fileName;
        }

        // 设置响应头
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_OCTET_STREAM);
        headers.setContentDispositionFormData("attachment", encodedFileName);
        headers.setContentLength(result.getBytes().length);

        return ResponseEntity.ok().headers(headers).body(result.getBytes());
    }

    /**
     * 构建错误响应
     *
     * @param statusCode HTTP状态码
     * @param message    错误消息
     * @return ResponseEntity
     */
    private ResponseEntity<byte[]> buildErrorResponse(int statusCode, String message) {
        byte[] errorBytes = message.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        return ResponseEntity.status(statusCode).header("Content-Type", "text/plain; charset=UTF-8").body(errorBytes);
    }

    /**
     * 下载签章文件
     *
     * @param dto 批次信息（必须包含batchId）
     * @return ResponseEntity 包含二进制文件数据
     */
    @Override
    public ResponseEntity<byte[]> downloadSealFile(LawCaseBatchInfoRequestDto dto) {
        log.info("开始下载签章文件，批次ID：{}", dto.getBatchId());

        // 验证批次ID
        if (dto.getBatchId() == null || dto.getBatchId().isEmpty()) {
            log.warn("批次ID为空，无法下载");
            return buildErrorResponse(400, "下载失败：批次ID不能为空");
        }

        // 从数据库查询批次信息
        LawCaseBatchInfo entity = lawCaseBatchInfoMapper.selectOne(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<LawCaseBatchInfo>().eq("batch_id", dto.getBatchId()).eq("is_delete", 0));

        if (entity == null) {
            log.warn("批次不存在，批次ID：{}", dto.getBatchId());
            return buildErrorResponse(404, "下载失败：批次不存在");
        }

        // 检查是否已完成签章
        if (entity.getIsSign() == null || entity.getIsSign() == 1) {
            log.warn("该批次尚未完成签章，批次ID：{}", dto.getBatchId());
            return buildErrorResponse(400, "下载失败：该批次尚未完成签章");
        }

        // 检查签章文件保存地址
        if (entity.getUploadAddressOver() == null || entity.getUploadAddressOver().isEmpty()) {
            log.warn("签章文件地址为空，批次ID：{}", dto.getBatchId());
            return buildErrorResponse(404, "下载失败：签章文件地址为空");
        }

        // 获取文件下载结果
        FileDownloadResult result;
        try {
            result = downloadSealFileBytes(dto);
        } catch (Exception e) {
            log.error("下载失败，批次ID：{}，错误：{}", dto.getBatchId(), e.getMessage(), e);
            return buildErrorResponse(500, "下载失败：" + e.getMessage());
        }

        if (result == null || result.getBytes() == null) {
            log.warn("下载失败，批次ID：{}", dto.getBatchId());
            return buildErrorResponse(404, "下载失败：文件不存在或无法访问");
        }

        // 构建文件名：batchName为空则使用batchId
        String baseFileName = (entity.getBatchName() != null && !entity.getBatchName().isEmpty()) ? entity.getBatchName() : entity.getBatchId();
        String fileName = baseFileName + ".zip";
        String encodedFileName;
        try {
            encodedFileName = java.net.URLEncoder.encode(fileName, java.nio.charset.StandardCharsets.UTF_8.toString()).replaceAll("\\+", "%20");
        } catch (java.io.UnsupportedEncodingException e) {
            log.error("文件名编码失败，批次ID：{}，错误：{}", dto.getBatchId(), e.getMessage(), e);
            encodedFileName = fileName;
        }

        // 设置响应头
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_OCTET_STREAM);
        headers.setContentDispositionFormData("attachment", encodedFileName);
        headers.setContentLength(result.getBytes().length);

        return ResponseEntity.ok().headers(headers).body(result.getBytes());
    }

    /**
     * 查询所有批次信息（分页）
     *
     * @param dto 查询条件
     * @return 分页结果
     */
    @Override
    public IPage<LawCaseBatchInfo> getAll(LawCaseBatchInfoRequestDto dto) {
        int pageIndex = dto.getPageIndex() != null ? dto.getPageIndex() : 1;
        int pageSize = dto.getPageSize() != null ? dto.getPageSize() : 10;
        Page<LawCaseBatchInfo> page = new Page<>(pageIndex, pageSize);
        return lawCaseBatchInfoMapper.selectPage(page, null);
    }

    /**
     * 查询批次状态
     *
     * @param dto 查询条件
     * @return 批次状态列表
     */
    @Override
    public List<LawCaseBatchInfoStateVo> getState(LawCaseBatchInfoRequestDto dto) {
        return lawCaseBatchInfoMapper.getState(dto);
    }

    /**
     * 下载未签章文件（返回字节数组）
     *
     * @param dto 批次信息（必须包含batchId）
     * @return 文件下载结果（包含文件名和字节数组）
     */
    public FileDownloadResult downloadUnstampedFileBytes(LawCaseBatchInfoRequestDto dto) throws com.jcraft.jsch.JSchException, java.io.IOException {
        log.info("开始下载未签章文件，批次ID：{}", dto.getBatchId());

        // 验证批次ID
        if (dto.getBatchId() == null || dto.getBatchId().isEmpty()) {
            log.warn("批次ID为空，无法下载");
            return null;
        }

        // 从数据库查询批次信息
        LawCaseBatchInfo entity = lawCaseBatchInfoMapper.selectOne(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<LawCaseBatchInfo>().eq("batch_id", dto.getBatchId()).eq("is_delete", 0));

        if (entity == null) {
            log.warn("批次不存在，批次ID：{}", dto.getBatchId());
            return null;
        }

        // 检查未签章文件保存地址
        if (entity.getUploadAddress() == null || entity.getUploadAddress().isEmpty()) {
            log.warn("未签章文件地址为空，批次ID：{}", dto.getBatchId());
            return null;
        }

        // 构建文件名
        String fileName = entity.getBatchId();
        String uploadAddress = entity.getUploadAddress();
        if (uploadAddress != null && uploadAddress.contains(".")) {
            String extension = uploadAddress.substring(uploadAddress.lastIndexOf("."));
            fileName += extension;
        }

        // 连接SFTP服务器下载文件
        SftpUtil sftpUtil = new SftpUtil(sftpConfig.getHost(), sftpConfig.getPort(), sftpConfig.getUsername(), sftpConfig.getPassword());
        try {
            sftpUtil.connect();

            // 根据数据库中的upload_address路径下载文件
            String uploadAddressPath = entity.getUploadAddress();
            String fileOnSftp = uploadAddressPath.substring(uploadAddressPath.lastIndexOf("/") + 1);

            // 使用配置的receive-path作为SFTP目录
            String directory = sftpConfig.getSftpReceivePath();

            log.info("从SFTP服务器下载文件，目录: {}, 文件名: {}", directory, fileOnSftp);

            try {
                // 下载文件到字节数组
                java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
                InputStream inputStream = sftpUtil.downloadAsStream(directory, fileOnSftp);
                if (inputStream == null) {
                    log.warn("下载失败，批次ID：{}", dto.getBatchId());
                    return null;
                }

                try {
                    byte[] buffer = new byte[8192];
                    int bytesRead;
                    while ((bytesRead = inputStream.read(buffer)) != -1) {
                        baos.write(buffer, 0, bytesRead);
                    }
                    baos.close();
                    inputStream.close();

                    // 更新下载信息
                    entity.setDownloadCount((entity.getDownloadCount() == null ? 0 : entity.getDownloadCount()) + 1);
                    entity.setDownloadName(dto.getDownloadName());
                    lawCaseBatchInfoMapper.updateById(entity);

                    log.info("未签章文件下载成功，批次ID：{}", dto.getBatchId());

                    return new FileDownloadResult(fileName, baos.toByteArray());
                } catch (java.io.IOException e) {
                    log.error("读取文件流失败，批次ID：{}，错误：{}", dto.getBatchId(), e.getMessage(), e);
                    return null;
                }
            } catch (Exception e) {
                log.error("下载文件失败，批次ID：{}，错误：{}", dto.getBatchId(), e.getMessage(), e);
                return null;
            }
        } finally {
            sftpUtil.disconnect();
        }
    }

    /**
     * 下载签章文件（返回字节数组）
     *
     * @param dto 批次信息（必须包含batchId）
     * @return 文件下载结果（包含文件名和字节数组）
     */
    public FileDownloadResult downloadSealFileBytes(LawCaseBatchInfoRequestDto dto) throws com.jcraft.jsch.JSchException, java.io.IOException {
        log.info("开始下载签章文件，批次ID：{}", dto.getBatchId());

        // 验证批次ID
        if (dto.getBatchId() == null || dto.getBatchId().isEmpty()) {
            log.warn("批次ID为空，无法下载");
            return null;
        }

        // 从数据库查询批次信息
        LawCaseBatchInfo entity = lawCaseBatchInfoMapper.selectOne(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<LawCaseBatchInfo>().eq("batch_id", dto.getBatchId()).eq("is_delete", 0));

        if (entity == null) {
            log.warn("批次不存在，批次ID：{}", dto.getBatchId());
            return null;
        }

        // 检查是否已完成签章
        if (entity.getIsSign() == null || entity.getIsSign() == 1) {
            log.warn("该批次尚未完成签章，批次ID：{}", dto.getBatchId());
            return null;
        }

        // 检查签章文件保存地址
        if (entity.getUploadAddressOver() == null || entity.getUploadAddressOver().isEmpty()) {
            log.warn("签章文件地址为空，批次ID：{}", dto.getBatchId());
            return null;
        }

        // 构建文件名
        String fileName = entity.getBatchId();
        String uploadAddressOver = entity.getUploadAddressOver();
        if (uploadAddressOver != null && uploadAddressOver.contains(".")) {
            String extension = uploadAddressOver.substring(uploadAddressOver.lastIndexOf("."));
            fileName += extension;
        }

        // 连接SFTP服务器下载文件
        SftpUtil sftpUtil = new SftpUtil(sftpConfig.getHost(), sftpConfig.getPort(), sftpConfig.getUsername(), sftpConfig.getPassword());
        try {
            sftpUtil.connect();

            // 根据数据库中的upload_address_over路径下载文件
            String uploadAddressOverPath = entity.getUploadAddressOver();
            String fileOnSftp = uploadAddressOverPath.substring(uploadAddressOverPath.lastIndexOf("/") + 1);

            // 使用配置的return-path作为SFTP目录
            String directory = sftpConfig.getSftpReturnPath();

            log.info("从SFTP服务器下载文件，目录: {}, 文件名: {}", directory, fileOnSftp);

            try {
                // 下载文件到字节数组
                java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
                InputStream inputStream = sftpUtil.downloadAsStream(directory, fileOnSftp);
                if (inputStream == null) {
                    log.warn("下载失败，批次ID：{}", dto.getBatchId());
                    return null;
                }

                try {
                    byte[] buffer = new byte[8192];
                    int bytesRead;
                    while ((bytesRead = inputStream.read(buffer)) != -1) {
                        baos.write(buffer, 0, bytesRead);
                    }
                    baos.close();
                    inputStream.close();

                    // 更新下载信息
                    entity.setDownloadCount((entity.getDownloadCount() == null ? 0 : entity.getDownloadCount()) + 1);
                    entity.setDownloadName(dto.getDownloadName());
                    lawCaseBatchInfoMapper.updateById(entity);

                    log.info("签章文件下载成功，批次ID：{}，文件路径：{}", dto.getBatchId(), uploadAddressOver);

                    return new FileDownloadResult(fileName, baos.toByteArray());
                } catch (java.io.IOException e) {
                    log.error("读取文件流失败，批次ID：{}，错误：{}", dto.getBatchId(), e.getMessage(), e);
                    return null;
                }
            } catch (Exception e) {
                log.error("下载文件失败，批次ID：{}，错误：{}", dto.getBatchId(), e.getMessage(), e);
                return null;
            }
        } finally {
            sftpUtil.disconnect();
        }
    }
}

