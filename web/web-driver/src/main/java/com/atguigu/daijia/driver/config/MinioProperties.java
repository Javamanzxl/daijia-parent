package com.atguigu.daijia.driver.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * @author ：zxl
 * @Description: Minio配置类
 * @ClassName: MinioConfig
 * @date ：2025/01/22 16:22
 */
@ConfigurationProperties(prefix = "minio")
@Data
@Configuration
public class MinioProperties {
    private String endpointUrl;
    private String accessKey;
    private String secretKey;
    private String bucketName;
}
