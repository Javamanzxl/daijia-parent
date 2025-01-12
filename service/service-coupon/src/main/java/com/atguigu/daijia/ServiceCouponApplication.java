package com.atguigu.daijia;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableDiscoveryClient
@EnableFeignClients
@Slf4j
public class ServiceCouponApplication {

    public static void main(String[] args) {

        SpringApplication.run(ServiceCouponApplication.class, args);
        log.info("优惠劵服务启动成功！！！！");
    }
}
