package com.atguigu.daijia.driver.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * @author ：zxl
 * @Description: 腾讯云存储
 * @ClassName: TencentCloudProperties
 * @date ：2025/01/12 22:46
 */
@ConfigurationProperties(prefix = "tencent.cloud")
@Component
@Data
public class TencentCloudProperties {
    private String secretId;
    private String secretKey;
    private String region;
    private String bucketPrivate;
    private String personGroupId;
}
