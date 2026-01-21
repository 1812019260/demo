package com.example.demo.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.example.demo.bean.Response;
import com.example.demo.bean.vo.LawCaseBatchInfoStateVo;
import com.example.demo.bean.vo.LawCaseBatchInfoVo;
import com.example.demo.dto.LawCaseBatchInfoRequestDto;
import com.example.demo.entity.LawCaseBatchInfo;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 签章文件表 服务类
 *
 * @author zxd
 * @since 2026-01-19
 */
public interface ILawCaseBatchInfoService extends IService<LawCaseBatchInfo> {

    /**
     * 上传压缩文件
     *
     * @param dto 批次信息
     * @param file 上传的压缩文件
     * @return Response 包含批次信息列表或错误信息
     */
    Response<List<LawCaseBatchInfoVo>> uploadUnstampedFile(LawCaseBatchInfoRequestDto dto, MultipartFile file);

    /**
     * 下载未签章文件
     *
     * @param dto 批次信息（必须包含batchId）
     * @return ResponseEntity 包含二进制文件数据
     */
    ResponseEntity<byte[]> downloadUnstampedFile(LawCaseBatchInfoRequestDto dto);

    /**
     * 上传签章文件
     *
     * @param dto 批次信息（必须包含batchId）
     * @param file 签章后的文件
     * @return Response 包含批次信息列表或错误信息
     */
    Response<List<LawCaseBatchInfoVo>> uploadSealFile(LawCaseBatchInfoRequestDto dto, MultipartFile file);

    /**
     * 下载签章文件
     *
     * @param dto 批次信息（必须包含batchId）
     * @return ResponseEntity 包含二进制文件数据
     */
    ResponseEntity<byte[]> downloadSealFile(LawCaseBatchInfoRequestDto dto);

    /**
     * 撤销待签章文件（逻辑删除，不删除远程仓库数据）
     *
     * @param dto 批次信息（必须包含batchId）
     * @return 操作结果
     */
    Response<?> cancelUnstampedFile(LawCaseBatchInfoRequestDto dto);

    /**
     * 撤销签章文件（修改批次状态和完成签章文件保存地址）
     *
     * @param dto 批次信息（必须包含batchId）
     * @return 操作结果
     */
    Response<?> cancelSealFile(LawCaseBatchInfoRequestDto dto);

    /**
     * 查询所有批次信息（分页）
     *
     * @param dto 查询条件
     * @return 分页结果
     */
    IPage<LawCaseBatchInfo> getAll(LawCaseBatchInfoRequestDto dto);

    /**
     * 查询批次状态
     *
     * @param dto 查询条件
     * @return 批次状态列表
     */
    List<LawCaseBatchInfoStateVo> getState(LawCaseBatchInfoRequestDto dto);
}
