package com.atguigu.daijia.customer.config;

import cn.binarywang.wx.miniapp.api.WxMaService;
import cn.binarywang.wx.miniapp.api.impl.WxMaServiceImpl;
import cn.binarywang.wx.miniapp.config.impl.WxMaDefaultConfigImpl;
import jakarta.annotation.Resource;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

/**
 * @author ：zxl
 * @Description: 微信工具包对象
 * @ClassName: WxConfigOperator
 * @date ：2025/01/12 17:42
 */
@Component
public class WxConfigOperator {
    @Resource
    private WxConfigProperties wxConfigProperties;

    @Bean
    public WxMaService wxMaService(){
        WxMaServiceImpl wxMaService = new WxMaServiceImpl();
        WxMaDefaultConfigImpl wxMaConfig = new WxMaDefaultConfigImpl();
        wxMaConfig.setAppid(wxConfigProperties.getAppId());
        wxMaConfig.setSecret(wxConfigProperties.getSecret());
        wxMaService.setWxMaConfig(wxMaConfig);
        return wxMaService;
    }
}
