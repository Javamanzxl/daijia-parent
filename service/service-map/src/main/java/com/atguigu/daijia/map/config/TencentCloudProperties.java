package com.atguigu.daijia.map.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * @author ：zxl
 * @Description: 腾讯配置类
 * @ClassName: TencentCloudProperties
 * @date ：2025/01/14 20:16
 */
@Component
@Data
@ConfigurationProperties(prefix = "tencent.map")
public class TencentCloudProperties {
    private String key;
}
