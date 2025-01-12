package com.atguigu.daijia.customer.service.impl;

import com.atguigu.daijia.common.constant.RedisConstant;
import com.atguigu.daijia.common.execption.GlobalException;
import com.atguigu.daijia.common.result.Result;
import com.atguigu.daijia.common.result.ResultCodeEnum;
import com.atguigu.daijia.customer.client.CustomerInfoFeignClient;
import com.atguigu.daijia.customer.service.CustomerService;
import com.atguigu.daijia.model.entity.customer.CustomerInfo;
import com.atguigu.daijia.model.vo.customer.CustomerLoginVo;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class CustomerServiceImpl implements CustomerService {
    @Resource
    private CustomerInfoFeignClient customerInfoFeignClient;
    @Resource
    private RedisTemplate<String, String> redisTemplate;

    /**
     * 小程序授权登录
     *
     * @param code
     * @return
     */
    @Override
    public String login(String code) {
        Result<Long> result = customerInfoFeignClient.login(code);
        if (result.getCode() != 200) {
            throw new GlobalException(result.getCode(), result.getMessage());
        }
        Long customerId = result.getData();
        if (customerId == null) {
            throw new GlobalException(ResultCodeEnum.DATA_ERROR);
        }
        String token = UUID.randomUUID().toString().replace("-", "");
        redisTemplate.opsForValue().set(RedisConstant.USER_LOGIN_KEY_PREFIX + token, customerId.toString()
                , RedisConstant.USER_LOGIN_KEY_TIMEOUT, TimeUnit.SECONDS);
        return token;
    }

    /**
     * 获取用户登陆信息
     * @param customerId
     * @return
     */
    @Override
    public CustomerLoginVo getCustomerLoginInfo(long customerId) {
        Result<CustomerLoginVo> result = customerInfoFeignClient.getCustomerLoginInfo(customerId);
        if(result.getCode()!=200){
            throw new GlobalException(result.getCode(), result.getMessage());
        }
        CustomerLoginVo vo = result.getData();
        if (vo == null) {
            throw new GlobalException(ResultCodeEnum.DATA_ERROR);
        }
        return vo;
    }
}
