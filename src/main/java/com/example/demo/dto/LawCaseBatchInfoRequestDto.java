package com.example.demo.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 案件批次信息请求对象
 */
@Data
public class LawCaseBatchInfoRequestDto implements Serializable {

    /**
     * 默认分页页码
     */
    public static final int serialVersionUID = 1;

    /**
     * 默认分页大小
     */
    public Integer pageIndex = 10;

    /**
     * 默认分页页码
     */
    public Integer pageSize = 10;


    /**
     * 开始时间
     */
    private LocalDateTime startTime;

    /**
     * 结束时间
     */
    private LocalDateTime endTime;

    /**
     * 批次ID
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
     * 上传地址
     */
    private String uploadAddress;

    /**
     * 上传完成地址
     */
    private String uploadAddressOver;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 创建用户名
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
     * 邮件内容（段落）
     */
    private String emailContentP;

    /**
     * 邮件发送时间
     */
    private LocalDateTime emailSendTime;

    /**
     * 下载名称
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
     * 更新用户名
     */
    private String updateUserName;

    /**
     * 是否完成签章
     */
    private Integer isSign;

    /**
     * 备注
     */
    private String remarks;

}
