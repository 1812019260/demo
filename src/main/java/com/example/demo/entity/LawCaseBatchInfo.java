package com.example.demo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * <p>
 * 签章文件表
 * </p>
 *
 * @author zxd
 * @since 2026-01-19
 */
@Getter
@Setter
@TableName("law_case_batch_info")
public class LawCaseBatchInfo implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    /**
     * 批次id
     */
    private String batchId;

    /**
     * 批次名称
     */
    private String batchName;

    /**
     * 批次状态
     */
    private String batchStatus;

    /**
     * 未签章文件保存地址
     */
    private String uploadAddress;

    /**
     * 完成签章文件保存地址
     */
    private String uploadAddressOver;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 创建人(上传人)
     */
    private String createUserName;

    /**
     * 发送者邮箱
     */
    private String senderEmail;

    /**
     * 接收者邮箱
     */
    private String recipientEmail;

    /**
     * 提醒进行签章邮箱内容
     */
    private String emailContentP;

    /**
     * 邮箱发送时间
     */
    private LocalDateTime emailSendTime;

    /**
     * 下载人
     */
    private String downloadName;

    /**
     * 下载次数
     */
    private Integer downloadCount;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

    /**
     * 更新人
     */
    private String updateUserName;

    /**
     * 是否完成签章(0-完成签章,1-未完成签章)
     */
    private Integer isSign;

    /**
     * 逻辑删除(0-未删除 1-已删除)
     */
    private Integer isDelete;

    /**
     * 备注
     */
    private String remarks;
}
