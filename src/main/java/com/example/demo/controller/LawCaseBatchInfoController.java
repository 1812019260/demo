package com.example.demo.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.example.demo.bean.Response;
import com.example.demo.bean.vo.LawCaseBatchInfoStateVo;
import com.example.demo.bean.vo.LawCaseBatchInfoVo;
import com.example.demo.dto.LawCaseBatchInfoRequestDto;
import com.example.demo.entity.LawCaseBatchInfo;
import com.example.demo.service.ILawCaseBatchInfoService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import java.util.List;

/**
 * 签章文件表 前端控制器
 *
 * @author zxd
 * @since 2026-01-19
 */
@Slf4j
@RestController
@RequestMapping("/law-case-batch-info")
public class LawCaseBatchInfoController {

    @Resource
    private ILawCaseBatchInfoService lawCaseBatchInfoService;

    @GetMapping("getAll")
    public Response<?> getAll(LawCaseBatchInfoRequestDto dto) {
        log.info("查询批次信息 {}", dto);
        IPage<LawCaseBatchInfo> result = lawCaseBatchInfoService.getAll(dto);
        log.info("查询信息结果 {}", result);
        return Response.success(result);
    }

    @GetMapping("getState")
    public Response<?> getState(LawCaseBatchInfoRequestDto dto) {
        log.info("查询批次状态 {}", dto);
        List<LawCaseBatchInfoStateVo> result = lawCaseBatchInfoService.getState(dto);
        log.info("查询状态结果 {}", result);
        return Response.success(result);
    }

    /**
     * 上传压缩文件
     *
     * @param dto  批次信息
     * @param file 上传的压缩文件
     * @return 上传结果
     */
    @PostMapping("uploadUnstampedFile")
    public Response<List<LawCaseBatchInfoVo>> uploadUnstampedFile(
            @ModelAttribute LawCaseBatchInfoRequestDto dto,
            @RequestParam("file") MultipartFile file) {
        log.info("上传压缩文件，批次信息：{}，文件名：{}", dto, file != null ? file.getOriginalFilename() : "无文件");
        return lawCaseBatchInfoService.uploadUnstampedFile(dto, file);
    }

    /**
     * 下载未签章文件
     *
     * @param dto 批次信息（必须包含batchId）
     * @return 下载的文件（二进制格式）
     */
    @GetMapping("downloadUnstampedFile")
    public ResponseEntity<byte[]> downloadUnstampedFile(LawCaseBatchInfoRequestDto dto) {
        log.info("下载未签章文件，批次ID：{}", dto.getBatchId());
        return lawCaseBatchInfoService.downloadUnstampedFile(dto);
    }

    /**
     * 上传签章文件
     *
     * @param dto  批次信息（必须包含batchId）
     * @param file 签章后的文件
     * @return 上传结果
     */
    @PostMapping("uploadSealFile")
    public Response<List<LawCaseBatchInfoVo>> uploadSealFile(
            @ModelAttribute LawCaseBatchInfoRequestDto dto,
            @RequestParam("file") MultipartFile file) {
        log.info("上传签章文件，批次ID：{}，文件名：{}", dto.getBatchId(), file.getOriginalFilename());
        Response<List<LawCaseBatchInfoVo>> result = lawCaseBatchInfoService.uploadSealFile(dto, file);
        log.info("上传结果 {}", result);
        return result;
    }

    /**
     * 下载签章文件
     *
     * @param dto 批次信息（必须包含batchId）
     * @return 下载的文件（二进制格式）
     */
    @GetMapping("downloadSealFile")
    public ResponseEntity<byte[]> downloadSealFile(LawCaseBatchInfoRequestDto dto) {
        log.info("下载签章文件，批次ID：{}", dto.getBatchId());
        return lawCaseBatchInfoService.downloadSealFile(dto);
    }

    /**
     * 撤销待签章文件
     *
     * @param dto 批次信息（必须包含batchId）
     * @return 操作结果
     */
    @PostMapping("cancelUnstampedFile")
    public Response<?> cancelUnstampedFile(LawCaseBatchInfoRequestDto dto) {
        log.info("撤销待签章文件，批次ID：{}", dto.getBatchId());
        try {
            Response<?> result = lawCaseBatchInfoService.cancelUnstampedFile(dto);
            log.info("撤销结果 {}", result);
            return result;
        } catch (Exception e) {
            log.error("撤销失败", e);
            return Response.fail("撤销失败：" + e.getMessage());
        }
    }

    /**
     * 撤销签章文件
     *
     * @param dto 批次信息（必须包含batchId）
     * @return 操作结果
     */
    @PostMapping("cancelSealFile")
    public Response<?> cancelSealFile(LawCaseBatchInfoRequestDto dto) {
        log.info("撤销签章文件，批次ID：{}", dto.getBatchId());
        try {
            Response<?> result = lawCaseBatchInfoService.cancelSealFile(dto);
            log.info("撤销结果 {}", result);
            return result;
        } catch (Exception e) {
            log.error("撤销失败", e);
            return Response.fail("撤销失败：" + e.getMessage());
        }
    }
}