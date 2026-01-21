package com.example.demo.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 邮件配置类
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "sign.email")
public class EmailConfig {
    /* 是否启用邮件通知 */
    private Boolean enabled;

    /* 收件人邮箱 */
    private String to;

    /* 邮件主题 */
    private String subject;
}