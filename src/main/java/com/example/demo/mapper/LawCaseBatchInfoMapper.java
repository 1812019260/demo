package com.example.demo.mapper;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.example.demo.bean.vo.LawCaseBatchInfoStateVo;
import com.example.demo.bean.vo.LawCaseBatchInfoVo;
import com.example.demo.dto.LawCaseBatchInfoRequestDto;
import com.example.demo.entity.LawCaseBatchInfo;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * <p>
 * 签章文件表 Mapper 接口
 * </p>
 *
 * @author zxd
 * @since 2026-01-19
 */
public interface LawCaseBatchInfoMapper extends BaseMapper<LawCaseBatchInfo> {

    /**
     * 分页查询所有
     *
     * @param page
     * @param dto
     * @return
     */
    IPage<LawCaseBatchInfoVo> getAll(
            @Param("page") IPage<LawCaseBatchInfoRequestDto> page,
            @Param("dto") LawCaseBatchInfoRequestDto dto
    );

    /**
     * 获取批次状态
     *
     * @param dto
     * @return
     */
    List<LawCaseBatchInfoStateVo> getState(@Param("dto") LawCaseBatchInfoRequestDto dto);

}
