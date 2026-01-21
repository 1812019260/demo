package com.example.demo.bean.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 批次状态视图对象
 */
@Data
public class LawCaseBatchInfoVo implements Serializable {

    private static final long serialVersionUID = 1L;

    /*
     * 批次ID
     */
    private String batchId;
    /*
     * 批次名称
     */
    private String batchName;
    /*
     * 批次状态
     */
    private String batchStatus;
    /*
     * 创建时间
     */
    private LocalDateTime createTime;
    /*
     * 创建人
     */
    private String createUserName;
    /*
     * 发送者邮箱
     */
    private String senderEmail;
    /*
     * 接收者邮箱
     */
    private String recipientEmail;
    /*
     * 邮箱内容
     */
    private String emailContentP;
    /*
     * 邮箱发送时间
     */
    private LocalDateTime emailSendTime;
    /*
     * 下载人
     */
    private String downloadName;
    /*
     * 下载次数
     */
    private Integer downloadCount;
    /*
     * 更新时间
     */
    private LocalDateTime updateTime;
    /*
     * 更新人
     */
    private String updateUserName;
    /*
     * 是否完成签章
     */
    private Integer isSign;

}
