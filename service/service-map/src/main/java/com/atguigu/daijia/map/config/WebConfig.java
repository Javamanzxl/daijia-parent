package com.atguigu.daijia.map.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/**
 * @author ：zxl
 * @Description: web配置类
 * @ClassName: WebConfig
 * @date ：2025/01/14 20:47
 */
@Configuration
public class WebConfig {
    @Bean
    public RestTemplate restTemplate(){
        return new RestTemplate();
    }
}
